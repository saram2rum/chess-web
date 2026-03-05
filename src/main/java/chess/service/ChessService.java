package chess.service;

import chess.domain.game.ChessGame;
import chess.domain.game.GameRoom;
import chess.domain.piece.Color;
import chess.domain.piece.Type;
import chess.dto.EvalResultDTO;
import chess.dto.EvalUpdateDTO;
import chess.dto.ErrorDTO;
import chess.dto.GameSettings;
import chess.dto.LobbyStateDTO;
import chess.dto.MoveResultDTO;
import chess.dto.StartGameDTO;
import chess.dto.TimeoutResultDTO;
import chess.dto.TimeUpdateDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ChessService {
    private static final int MAX_ROOMS = 10;
    private static final int DISCONNECT_GRACE_SECONDS = 20;

    /** Idle 정리 (분) */
    private static final int IDLE_LOBBY_MINUTES = 15;
    private static final int IDLE_GAME_IN_PROGRESS_MINUTES = 30;
    private static final int IDLE_GAME_OVER_MINUTES = 5;

    private static final String MSG_INACTIVITY = "You have been disconnected due to inactivity.";

    private final ConcurrentHashMap<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToUserId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToNickname = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.concurrent.ScheduledFuture<?>> disconnectTasks = new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messagingTemplate;
    private final AiEvaluationService aiEvaluationService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    /** 형세분석 비동기 전용 (수 이동 블로킹 방지) */
    private final ExecutorService evalExecutor = Executors.newCachedThreadPool();

    private static final String ROOM_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final Random random = new Random();

    public ChessService(SimpMessagingTemplate messagingTemplate, AiEvaluationService aiEvaluationService) {
        this.messagingTemplate = messagingTemplate;
        this.aiEvaluationService = aiEvaluationService;
    }

    /**
     * 새로운 게임 방을 생성하고 5자리 방 코드 반환
     * @throws IllegalStateException 방 개수 제한(MAX_ROOMS) 도달 시
     */
    public String createGame() {
        if (gameRooms.size() >= MAX_ROOMS) {
            throw new IllegalStateException("Maximum number of rooms (10) reached. Please try again later.");
        }

        String roomId;
        do {
            StringBuilder sb = new StringBuilder(5);
            for (int i = 0; i < 5; i++) {
                sb.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
            }
            roomId = sb.toString();
        } while (gameRooms.containsKey(roomId));
        GameRoom newRoom = new GameRoom(roomId);
        gameRooms.put(roomId, newRoom);
        return roomId;
    }

    /**
     * 특정 방에 입장 (userId는 reconnect 시 reclaim 매칭용)
     * @param roomId 방 ID
     * @param sessionId 플레이어의 세션 ID
     * @param userId 플레이어의 사용자 ID (null 가능)
     * @return "HOST" 또는 "GUEST"
     */
    public String joinGame(String roomId, String sessionId, String userId) {
        GameRoom room = getGameRoom(roomId);
        String role = room.joinPlayer(sessionId, userId);
        // reclaim 시 예약된 disconnect 작업 취소
        cancelDisconnectTask(roomId, true);
        cancelDisconnectTask(roomId, false);
        return role;
    }

    /**
     * 로비 상태 조회 (hostUserId/guestUserId로 역할 판별)
     */
    public LobbyStateDTO getLobbyState(String roomId) {
        GameRoom room = getGameRoom(roomId);
        String hostSid = room.getHostSessionId();
        String guestSid = room.getGuestSessionId();
        return new LobbyStateDTO(
            roomId,
            hostSid,
            guestSid,
            hostSid != null ? sessionToUserId.get(hostSid) : null,
            hostSid != null ? sessionToNickname.get(hostSid) : null,
            guestSid != null ? sessionToUserId.get(guestSid) : null,
            guestSid != null ? sessionToNickname.get(guestSid) : null,
            room.getSettings(),
            room.isFull(),
            room.isGameStarted()
        );
    }

    /** 게임 설정 유효 범위 (클라이언트 조작/오버플로우 방지) */
    private static final int SETTINGS_TIME_LIMIT_MIN = 1;
    private static final int SETTINGS_TIME_LIMIT_MAX = 60;
    private static final int SETTINGS_INCREMENT_MIN = 0;
    private static final int SETTINGS_INCREMENT_MAX = 60;

    /**
     * 게임 설정 변경 (방장만 가능)
     */
    public GameSettings updateSettings(String roomId, String sessionId, GameSettings newSettings) {
        GameRoom room = getGameRoom(roomId);

        if (!room.isHost(sessionId)) {
            throw new IllegalArgumentException("Only the host can change settings.");
        }

        if (room.isGameStarted()) {
            throw new IllegalStateException("Game has already started.");
        }

        int timeLimit = Math.max(SETTINGS_TIME_LIMIT_MIN,
                Math.min(SETTINGS_TIME_LIMIT_MAX, newSettings.timeLimit()));
        int increment = Math.max(SETTINGS_INCREMENT_MIN,
                Math.min(SETTINGS_INCREMENT_MAX, newSettings.increment()));
        String startingSide = validStartingSide(newSettings.startingSide());

        GameSettings validated = new GameSettings(startingSide, timeLimit, increment);
        room.updateSettings(validated);
        return room.getSettings();
    }

    private String validStartingSide(String s) {
        if (s == null || s.isBlank()) return "RANDOM";
        String upper = s.toUpperCase();
        if ("WHITE".equals(upper) || "BLACK".equals(upper) || "RANDOM".equals(upper)) {
            return upper;
        }
        return "RANDOM";
    }

    /**
     * 게임 시작 (방장만 가능)
     */
    public void startGame(String roomId, String sessionId) {
        GameRoom room = getGameRoom(roomId);
        
        if (!room.isHost(sessionId)) {
            throw new IllegalArgumentException("Only the host can start the game.");
        }
        
        room.startGame();
    }

    /**
     * 체스 기물을 이동하고 결과를 반환
     * @param promotion 폰 승급 시 "QUEEN","ROOK","BISHOP","KNIGHT" (null이면 퀸)
     */
    public MoveResultDTO move(String roomId, String sessionId, String source, String target, String promotion) {
        GameRoom room = getGameRoom(roomId);
        
        if (!room.isGameStarted()) {
            throw new IllegalStateException("Game has not started yet.");
        }
        
        // 현재 턴의 플레이어인지 검증
        if (!room.isCurrentTurnPlayer(sessionId)) {
            Color playerColor = room.getPlayerColor(sessionId);
            if (playerColor == null) {
                throw new IllegalArgumentException("You are not in this game.");
            }
            throw new IllegalArgumentException("Not your turn! (Current: " + 
                room.getGame().getCurrentTurn() + ", You: " + playerColor + ")");
        }
        
        ChessGame game = room.getGame();
        Type promotionType = parsePromotionType(promotion);
        
        // 이동 시도
        game.move(source, target, promotionType);
        
        // 이동 후 게임 상태 확인
        Color nextTurn = game.getCurrentTurn();
        Color mover = nextTurn.opponent();
        room.onMoveCompleted(mover);
        
        boolean isCheck = game.getBoard().isChecked(nextTurn);
        boolean isCheckMate = isCheck && game.getBoard().isCheckMate(nextTurn);
        boolean isDraw = game.isDraw();
        boolean isGameOver = !game.isRunning();
        
        // 메시지 생성
        String message = buildMessage(isCheckMate, isCheck, isDraw, isGameOver, nextTurn);
        
        var boardSnapshot = game.getBoard().getBoardSnapshot();
        String fen = game.getFEN();
        boolean isCapture = game.getBoard().isLastMoveCapture();
        
        // 체크메이트 시 nextTurn=방금 둔 사람(승자), 턴 안 넘어감. 무승부는 winner=DRAW
        String winner = null;
        String endReason = null;
        if (isGameOver) {
            if (isDraw) {
                winner = "DRAW";
                endReason = game.getDrawReason();
            } else {
                winner = nextTurn.toString();  // 체크메이트는 nextTurn이 승자 (방금 둔 사람)
                endReason = "Checkmate";
            }
        }
        
        long whiteTime = room.getWhiteTimeForBroadcast(nextTurn);
        long blackTime = room.getBlackTimeForBroadcast(nextTurn);

        // 수 이동 우선: 형세분석은 비동기로 분리 (broadcastEvalAsync)
        int moveSeq = room.getMoveSequence();

        return new MoveResultDTO(
            source,
            target,
            nextTurn.toString(),
            isCheck,
            isCheckMate,
            isDraw,
            isGameOver,
            message,
            boardSnapshot,
            fen,
            isCapture,
            winner,
            endReason,
            whiteTime,
            blackTime,
            null,       // evalType: 비동기로 별도 브로드캐스트
            null,       // evalValue
            moveSeq    // moveSequence: eval 순서 보장
        );
    }

    /**
     * 형세분석 비동기 브로드캐스트 (수 이동과 분리, 서비스 저하 방지)
     * moveSequence: 오래된 eval이 나중에 도착해 덮어쓰는 것 방지
     */
    public void broadcastEvalAsync(String roomId, String fen, int moveSequence) {
        if (roomId == null || fen == null || fen.isBlank()) return;
        int seq = moveSequence;
        evalExecutor.submit(() -> {
            try {
                EvalResultDTO eval = aiEvaluationService.evaluate(fen);
                if (eval != null) {
                    messagingTemplate.convertAndSend("/topic/eval/" + roomId,
                            new EvalUpdateDTO(fen, eval.type(), eval.value(), seq));
                }
            } catch (Exception ignored) { /* 형세분석 실패해도 수 이동에는 영향 없음 */ }
        });
    }

    /** 게임 시작 시 초기 형세분석 비동기 브로드캐스트 (moveSequence=0) */
    public void broadcastInitialEvalAsync(String roomId) {
        GameRoom room = gameRooms.get(roomId);
        if (room == null || !room.isGameStarted() || room.getGame() == null) return;
        broadcastEvalAsync(roomId, room.getGame().getFEN(), 0);
    }

    private Type parsePromotionType(String promotion) {
        if (promotion == null || promotion.isBlank()) return null;
        try {
            return Type.valueOf(promotion.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 특정 기물의 이동 가능한 위치 목록 조회
     */
    public List<String> getMovablePositions(String roomId, String sessionId, String source) {
        GameRoom room = getGameRoom(roomId);
        
        if (!room.isGameStarted()) {
            throw new IllegalStateException("Game has not started yet.");
        }
        
        ChessGame game = room.getGame();
        
        // 해당 기물의 색상 확인
        Color playerColor = room.getPlayerColor(sessionId);
        if (playerColor == null) {
            throw new IllegalArgumentException("You are not in this game.");
        }
        
        // 현재 턴의 플레이어만 이동 가능 위치 조회 가능
        if (!room.isCurrentTurnPlayer(sessionId)) {
            throw new IllegalArgumentException("Not your turn.");
        }
        
        // 이동 가능한 위치 계산
        chess.domain.board.Position sourcePos = new chess.domain.board.Position(source);
        List<chess.domain.board.Position> movablePositions = game.calculateMovablePositions(sourcePos);
        
        // Position 객체를 String으로 변환
        return movablePositions.stream()
            .map(chess.domain.board.Position::toString)
            .toList();
    }

    /**
     * WebSocket 연결 시 호출 (sessionId→userId/nickname 매핑 저장)
     */
    public void handleUserConnect(String userId, String sessionId, String nickname) {
        if (sessionId != null && userId != null) {
            sessionToUserId.put(sessionId, userId);
            sessionToNickname.put(sessionId, nickname != null ? nickname : "User");
        }
    }

    /**
     * WebSocket 연결 해제 시 호출
     * - 매핑 제거
     * - 해당 세션이 속한 방에 disconnect 표시, 20초 후 미재접속 시 방 삭제
     */
    public void handleUserDisconnect(String userId, String sessionId, String nickname) {
        if (sessionId != null) {
            GameRoom room = findRoomBySessionId(sessionId);

            sessionToUserId.remove(sessionId);
            sessionToNickname.remove(sessionId);

            if (room == null) return;

            String roomId = room.getRoomId();

            // 2. 해당 슬롯 disconnect 표시
            if (room.isHostSession(sessionId)) {
                room.markHostDisconnected();
            } else if (room.isGuestSession(sessionId)) {
                room.markGuestDisconnected();
            } else {
                return;
            }

            // 3. 즉시 삭제: 한 명만 있거나, 둘 다 끊김
            if (room.isOnlyOneAndDisconnected() || room.isBothDisconnected()) {
                cancelDisconnectTask(roomId, room.isHostSession(sessionId));
                notifyOpponentLeftAndDelete(roomId);
                return;
            }

            // 4. 20초 후 삭제 예약
            String taskKey = roomId + (room.isHostSession(sessionId) ? "_host" : "_guest");
            java.util.concurrent.ScheduledFuture<?> existing = disconnectTasks.remove(taskKey);
            if (existing != null) existing.cancel(false);

            java.util.concurrent.ScheduledFuture<?> future = scheduler.schedule(
                    () -> processDisconnectTimeout(roomId, room.isHostSession(sessionId)),
                    DISCONNECT_GRACE_SECONDS,
                    TimeUnit.SECONDS
            );
            disconnectTasks.put(taskKey, future);
        }
    }

    private GameRoom findRoomBySessionId(String sessionId) {
        for (GameRoom r : gameRooms.values()) {
            if (r.isHostSession(sessionId) || r.isGuestSession(sessionId)) {
                return r;
            }
        }
        return null;
    }

    private void cancelDisconnectTask(String roomId, boolean wasHost) {
        String key = roomId + (wasHost ? "_host" : "_guest");
        java.util.concurrent.ScheduledFuture<?> f = disconnectTasks.remove(key);
        if (f != null) f.cancel(false);
    }

    private void processDisconnectTimeout(String roomId, boolean wasHost) {
        disconnectTasks.remove(roomId + (wasHost ? "_host" : "_guest"));
        GameRoom room = gameRooms.get(roomId);
        if (room == null) return;

        boolean stillDisconnected = wasHost ? room.isHostDisconnected() : room.isGuestDisconnected();
        if (!stillDisconnected) return;  // 이미 reclaim됨

        notifyOpponentLeftAndDelete(roomId);
    }

    private void notifyOpponentLeftAndDelete(String roomId) {
        try {
            messagingTemplate.convertAndSend("/topic/errors/" + roomId,
                    new ErrorDTO("OPPONENT_LEFT", "Opponent has left."));
        } finally {
            gameRooms.remove(roomId);
        }
    }

    /**
     * Leave 버튼으로 명시적 퇴장 시 호출. 상대에게 알림 후 방 삭제.
     */
    public void leaveRoom(String roomId, String sessionId) {
        GameRoom room = gameRooms.get(roomId);
        if (room == null) return;

        if (!room.isHostSession(sessionId) && !room.isGuestSession(sessionId)) {
            return;
        }

        // disconnect 예약된 작업 취소
        cancelDisconnectTask(roomId, true);
        cancelDisconnectTask(roomId, false);

        notifyOpponentLeftAndDelete(roomId);
    }

    /**
     * Idle 방 정리: 로비 15분, 게임중 30분, 종료후 5분
     */
    public void checkAndCleanIdleRooms() {
        long now = System.currentTimeMillis();
        var toRemove = new java.util.ArrayList<String>();

        for (var entry : gameRooms.entrySet()) {
            String roomId = entry.getKey();
            GameRoom room = entry.getValue();

            long lastAt = room.getLastActivityAt();
            int thresholdMinutes;

            if (!room.isGameStarted()) {
                thresholdMinutes = IDLE_LOBBY_MINUTES;
            } else if (room.getGame() != null && room.getGame().isRunning()) {
                thresholdMinutes = IDLE_GAME_IN_PROGRESS_MINUTES;
            } else {
                thresholdMinutes = IDLE_GAME_OVER_MINUTES;
            }

            long thresholdMs = thresholdMinutes * 60L * 1000;
            if (now - lastAt > thresholdMs) {
                toRemove.add(roomId);
            }
        }

        for (String roomId : toRemove) {
            try {
                messagingTemplate.convertAndSend("/topic/errors/" + roomId,
                        new ErrorDTO("INACTIVITY_KICK", MSG_INACTIVITY));
            } finally {
                cancelDisconnectTask(roomId, true);
                cancelDisconnectTask(roomId, false);
                gameRooms.remove(roomId);
            }
        }
    }

    /**
     * 방 ID로 GameRoom을 가져옴 (없으면 예외 발생)
     */
    private GameRoom getGameRoom(String roomId) {
        GameRoom room = gameRooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found: " + roomId);
        }
        return room;
    }

    /**
     * 게임 상태에 따른 메시지 생성
     */
    private String buildMessage(boolean isCheckMate, boolean isCheck, boolean isDraw, 
                                 boolean isGameOver, Color nextTurn) {
        if (isCheckMate) {
            return "🎉 Checkmate! " + nextTurn + " wins!";
        }
        if (isDraw) {
            return "🤝 Draw.";
        }
        if (isCheck) {
            return "🔥 Check! " + nextTurn + " king in danger!";
        }
        if (isGameOver) {
            return "Game over.";
        }
        return "Move successful";
    }

    /**
     * 게임 시작 후 알림용 DTO 조회
     */
    public StartGameDTO getStartGameInfo(String roomId) {
        GameRoom room = getGameRoom(roomId);
        Color currentTurn = room.getGame().getCurrentTurn();
        // 수 이동 우선: 초기 형세분석은 broadcastInitialEvalAsync로 비동기 처리
        return new StartGameDTO(
            "Game start!",
            "Moving to chessboard.",
            room.getHostColor().toString(),
            room.getGuestColor().toString(),
            room.getWhiteTimeForBroadcast(currentTurn),
            room.getBlackTimeForBroadcast(currentTurn),
            null,  // evalType: 비동기
            null   // evalValue
        );
    }

    /**
     * 특정 방이 존재하는지 확인
     */
    public boolean roomExists(String roomId) {
        return gameRooms.containsKey(roomId);
    }

    /**
     * 진행 중인 게임의 타이머 데이터 (3초마다 브로드캐스트용)
     */
    public Map<String, TimeUpdateDTO> getActiveRoomsTimerData() {
        Map<String, TimeUpdateDTO> result = new HashMap<>();
        for (var e : gameRooms.entrySet()) {
            GameRoom r = e.getValue();
            if (r.isGameStarted() && r.getGame() != null && r.getGame().isRunning()) {
                Color ct = r.getGame().getCurrentTurn();
                result.put(e.getKey(), new TimeUpdateDTO(
                    r.getWhiteTimeForBroadcast(ct),
                    r.getBlackTimeForBroadcast(ct)
                ));
            }
        }
        return result;
    }

    /** Play Again - 게임 리셋 후 로비 상태 브로드캐스트 */
    public void returnToLobby(String roomId) {
        GameRoom room = getGameRoom(roomId);
        room.resetForRematch();
        room.touchActivity();
    }

    /**
     * 게임 방 삭제
     */
    public void deleteGame(String roomId) {
        gameRooms.remove(roomId);
    }

    /**
     * 시간 패 처리 - 현재 턴 플레이어 시간 초과 시 호출
     * 클라이언트 조작 방지: 서버 기준으로 실제로 시간이 0 이하인지 검증 후 처리
     * @return 시간 패 결과 (승자 = 상대방), 이미 종료된 게임이면 null
     */
    public TimeoutResultDTO handleTimeout(String roomId) {
        GameRoom room = getGameRoom(roomId);

        if (!room.isGameStarted() || room.getGame() == null) {
            throw new IllegalStateException("Game has not started yet.");
        }

        ChessGame game = room.getGame();
        if (!game.isRunning()) {
            return null; // 이미 종료됨 (중복 호출 방지)
        }

        // 현재 턴 = 시간 초과한 플레이어 (패자)
        Color loser = game.getCurrentTurn();
        long remaining = (loser == Color.WHITE)
                ? room.getWhiteTimeForBroadcast(Color.WHITE)
                : room.getBlackTimeForBroadcast(Color.BLACK);
        if (remaining > 0) {
            throw new IllegalStateException("Time has not run out yet. Remaining: " + remaining + "ms");
        }

        Color winner = loser.opponent();

        room.onTimeout(loser);
        room.touchActivity();  // 게임 종료 시점 기록 (idle 5분용)
        game.endGame();

        return new TimeoutResultDTO(
            loser.toString(),
            true,
            winner.toString(),
            "Time Out"
        );
    }
}


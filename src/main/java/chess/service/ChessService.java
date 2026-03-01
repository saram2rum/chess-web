package chess.service;

import chess.domain.game.ChessGame;
import chess.domain.game.GameRoom;
import chess.domain.piece.Color;
import chess.domain.piece.Type;
import chess.dto.GameSettings;
import chess.dto.LobbyStateDTO;
import chess.dto.MoveResultDTO;
import chess.dto.StartGameDTO;
import chess.dto.TimeoutResultDTO;
import chess.dto.TimeUpdateDTO;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChessService {
    private final ConcurrentHashMap<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToUserId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToNickname = new ConcurrentHashMap<>();

    private static final String ROOM_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final Random random = new Random();

    /**
     * 새로운 게임 방을 생성하고 5자리 방 코드 반환
     */
    public String createGame() {
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
     * 특정 방에 입장
     * @param roomId 방 ID
     * @param sessionId 플레이어의 세션 ID
     * @return "HOST" 또는 "GUEST"
     */
    public String joinGame(String roomId, String sessionId) {
        GameRoom room = getGameRoom(roomId);
        return room.joinPlayer(sessionId);
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
        
        room.updateSettings(newSettings);
        return room.getSettings();
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
            blackTime
        );
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
     * WebSocket 연결 해제 시 호출 (매핑 제거)
     */
    public void handleUserDisconnect(String userId, String sessionId, String nickname) {
        if (sessionId != null) {
            sessionToUserId.remove(sessionId);
            sessionToNickname.remove(sessionId);
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
        return new StartGameDTO(
            "Game start!",
            "Moving to chessboard.",
            room.getHostColor().toString(),
            room.getGuestColor().toString(),
            room.getWhiteTimeForBroadcast(currentTurn),
            room.getBlackTimeForBroadcast(currentTurn)
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
    }

    /**
     * 게임 방 삭제
     */
    public void deleteGame(String roomId) {
        gameRooms.remove(roomId);
    }

    /**
     * 시간 패 처리 - 현재 턴 플레이어 시간 초과 시 호출
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
        Color winner = loser.opponent();

        room.onTimeout(loser);
        game.endGame();

        return new TimeoutResultDTO(
            loser.toString(),
            true,
            winner.toString(),
            "Time Out"
        );
    }
}


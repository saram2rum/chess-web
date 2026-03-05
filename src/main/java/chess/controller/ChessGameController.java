package chess.controller;

import chess.dto.*;
import chess.service.ChessService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

@Controller
public class ChessGameController {
    private final ChessService chessService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChessGameController(ChessService chessService, SimpMessagingTemplate messagingTemplate) {
        this.chessService = chessService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 새로운 게임 방 생성
     * 응답: /user/queue/create (Principal 있을 때) 또는 /topic/create (fallback)
     */
    @MessageMapping("/create")
    public void createGame(Principal principal) {
        String userId = principal != null ? principal.getName() : null;
        System.out.println("📥 방 생성 요청 수신 - 유저: " + userId);

        try {
            String roomId = chessService.createGame();
            System.out.println("✅ 방 생성 완료 - Room ID: " + roomId);

            CreateGameResponseDTO response = new CreateGameResponseDTO(roomId, "Game room created.");

            if (userId != null && !userId.isEmpty()) {
                messagingTemplate.convertAndSendToUser(userId, "/queue/create", response);
                System.out.println("📤 방 생성 응답: /user/" + userId + "/queue/create");
            } else {
                messagingTemplate.convertAndSend("/topic/create", response);
                System.out.println("📤 방 생성 응답 (fallback): /topic/create");
            }
        } catch (IllegalStateException e) {
            System.out.println("❌ 방 생성 실패 (제한 도달): " + e.getMessage());
            CreateGameResponseDTO errorResponse = new CreateGameResponseDTO(null, e.getMessage());
            if (userId != null && !userId.isEmpty()) {
                messagingTemplate.convertAndSendToUser(userId, "/queue/create", errorResponse);
            } else {
                messagingTemplate.convertAndSend("/topic/create", errorResponse);
            }
        }
    }

    /**
     * 게임 방에 입장 (로비)
     * 클라이언트: /app/lobby/join/{roomId}
     * 응답: /topic/lobby/{roomId}
     */
    @MessageMapping("/lobby/join/{roomId}")
    public void joinLobby(
            @DestinationVariable String roomId,
            @Header("simpSessionId") String sessionId,
            Principal principal
    ) {
        String userId = principal != null ? principal.getName() : null;
        System.out.println("📥 로비 입장 요청 - 방: " + roomId + ", 세션: " + sessionId);
        
        try {
            String role = chessService.joinGame(roomId, sessionId, userId);
            System.out.println("✅ 로비 입장 성공 - 역할: " + role);
            
            // 로비 상태 전송
            LobbyStateDTO lobbyState = chessService.getLobbyState(roomId);
            System.out.println("📤 로비 상태 전송: /topic/lobby/" + roomId);
            messagingTemplate.convertAndSend("/topic/lobby/" + roomId, lobbyState);
            
        } catch (Exception e) {
            System.out.println("❌ 로비 입장 실패: " + e.getMessage());
            ErrorDTO error = new ErrorDTO("LOBBY_JOIN_FAILED", e.getMessage());
            messagingTemplate.convertAndSend("/topic/errors/" + roomId, error);
        }
    }

    /**
     * 방 나가기 (Leave 버튼)
     * 클라이언트: /app/lobby/leave/{roomId}
     * 응답: /topic/errors/{roomId} (OPPONENT_LEFT) → 상대에게 알림 후 방 삭제
     */
    @MessageMapping("/lobby/leave/{roomId}")
    public void leaveLobby(
            @DestinationVariable String roomId,
            @Header("simpSessionId") String sessionId
    ) {
        System.out.println("📤 방 나가기 요청 - 방: " + roomId);
        chessService.leaveRoom(roomId, sessionId);
    }

    /**
     * 게임 설정 변경 (방장만 가능)
     * 클라이언트: /app/lobby/settings/{roomId}
     * 응답: /topic/lobby/{roomId}
     */
    @MessageMapping("/lobby/settings/{roomId}")
    public void updateSettings(
            @DestinationVariable String roomId,
            @Header("simpSessionId") String sessionId,
            GameSettings newSettings
    ) {
        System.out.println("📥 설정 변경 요청 - 방: " + roomId);
        
        try {
            chessService.updateSettings(roomId, sessionId, newSettings);
            System.out.println("✅ 설정 변경 완료");
            
            // 변경된 로비 상태 전송
            LobbyStateDTO lobbyState = chessService.getLobbyState(roomId);
            System.out.println("📤 로비 상태 전송: /topic/lobby/" + roomId);
            messagingTemplate.convertAndSend("/topic/lobby/" + roomId, lobbyState);
            
        } catch (Exception e) {
            System.out.println("❌ 설정 변경 실패: " + e.getMessage());
            ErrorDTO error = new ErrorDTO("SETTINGS_UPDATE_FAILED", e.getMessage());
            messagingTemplate.convertAndSend("/topic/errors/" + roomId, error);
        }
    }

    /**
     * Play Again - 로비 복귀 (게임 리셋, 로비 상태 브로드캐스트)
     * 클라이언트: /app/lobby/return/{roomId}
     */
    @MessageMapping("/lobby/return/{roomId}")
    public void returnToLobby(@DestinationVariable String roomId) {
        System.out.println("📥 로비 복귀 요청 (Play Again) - 방: " + roomId);
        try {
            chessService.returnToLobby(roomId);
            LobbyStateDTO lobbyState = chessService.getLobbyState(roomId);
            messagingTemplate.convertAndSend("/topic/lobby/" + roomId, lobbyState);
            System.out.println("📤 로비 상태 브로드캐스트 완료");
        } catch (Exception e) {
            System.out.println("❌ 로비 복귀 실패: " + e.getMessage());
            ErrorDTO error = new ErrorDTO("RETURN_FAILED", e.getMessage());
            messagingTemplate.convertAndSend("/topic/errors/" + roomId, error);
        }
    }

    /**
     * 게임 시작 (방장만 가능)
     * 클라이언트: /app/lobby/start/{roomId}
     * 응답: /topic/game/start/{roomId}
     */
    @MessageMapping("/lobby/start/{roomId}")
    public void startGame(
            @DestinationVariable String roomId,
            @Header("simpSessionId") String sessionId
    ) {
        System.out.println("📥 게임 시작 요청 - 방: " + roomId);
        
        try {
            chessService.startGame(roomId, sessionId);
            System.out.println("✅ 게임 시작 완료");
            
            // 게임 시작 알림 전송 (형세분석 없이 즉시)
            StartGameDTO startInfo = chessService.getStartGameInfo(roomId);
            System.out.println("📤 게임 시작 알림: /topic/game/start/" + roomId);
            messagingTemplate.convertAndSend("/topic/game/start/" + roomId, startInfo);
            chessService.broadcastInitialEvalAsync(roomId);
            
        } catch (Exception e) {
            System.out.println("❌ 게임 시작 실패: " + e.getMessage());
            ErrorDTO error = new ErrorDTO("GAME_START_FAILED", e.getMessage());
            messagingTemplate.convertAndSend("/topic/errors/" + roomId, error);
        }
    }

    /**
     * 체스 기물 이동 처리
     * 클라이언트: /app/move/{roomId}
     * 응답: /topic/game/{roomId}
     */
    @MessageMapping("/move/{roomId}")
    public void handleMove(
            @DestinationVariable String roomId,
            @Header("simpSessionId") String sessionId,
            MoveDTO move
    ) {
        try {
            MoveResultDTO result = chessService.move(roomId, sessionId, move.source(), move.target(), move.promotion());
            messagingTemplate.convertAndSend("/topic/game/" + roomId, result);
            if (!result.isGameOver() && result.fen() != null && result.moveSequence() != null) {
                chessService.broadcastEvalAsync(roomId, result.fen(), result.moveSequence());
            }
        } catch (Exception e) {
            ErrorDTO error = new ErrorDTO("MOVE_FAILED", e.getMessage());
            messagingTemplate.convertAndSend("/topic/errors/" + roomId, error);
        }
    }

    /**
     * 시간 패 처리 (타이머 0초 도달 시 클라이언트에서 호출)
     * 클라이언트: /app/timeout/{roomId}
     * 응답: /topic/game/{roomId} (게임 종료 결과)
     */
    @MessageMapping("/timeout/{roomId}")
    public void handleTimeout(@DestinationVariable String roomId) {
        System.out.println("⏱️ 시간 패 요청 수신 - 방: " + roomId);

        try {
            TimeoutResultDTO result = chessService.handleTimeout(roomId);
            if (result == null) {
                System.out.println("⏱️ 게임이 이미 종료되어 무시");
                return;
            }
            System.out.println("⏱️ 시간 패 처리 완료 - 승자: " + result.winner());
            messagingTemplate.convertAndSend("/topic/game/" + roomId, result);
        } catch (Exception e) {
            System.out.println("⏱️ 시간 패 실패: " + e.getMessage());
            ErrorDTO error = new ErrorDTO("TIMEOUT_FAILED", e.getMessage());
            messagingTemplate.convertAndSend("/topic/errors/" + roomId, error);
        }
    }

    /**
     * 이동 가능한 위치 조회
     * 클라이언트: /app/movable/{roomId}
     * 응답: /topic/movable/{roomId}
     */
    @MessageMapping("/movable/{roomId}")
    public void getMovablePositions(
            @DestinationVariable String roomId,
            @Header("simpSessionId") String sessionId,
            MoveDTO request
    ) {
        try {
            List<String> targets = chessService.getMovablePositions(roomId, sessionId, request.source());
            
            MovablePositionsDTO response = new MovablePositionsDTO(
                request.source(),
                targets
            );
            
            // 브로드캐스트로 변경 (모두에게 보여도 됨)
            messagingTemplate.convertAndSend("/topic/movable/" + roomId, response);
            
        } catch (Exception e) {
            // 에러는 무시
        }
    }
}

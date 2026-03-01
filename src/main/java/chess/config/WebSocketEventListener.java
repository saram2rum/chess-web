package chess.config;

import chess.service.ChessService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * WebSocket 연결/해제 이벤트 리스너
 * 유저의 연결 상태를 추적하고 재접속을 지원
 */
@Component
public class WebSocketEventListener {

    private final ChessService chessService;

    public WebSocketEventListener(ChessService chessService) {
        this.chessService = chessService;
    }

    /**
     * 🆕 WebSocket 연결 이벤트
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();
        
        if (user != null) {
            String userId = user.getName();
            String sessionId = headerAccessor.getSessionId();
            String nickname = headerAccessor.getFirstNativeHeader("nickname");
            if (nickname == null) nickname = "User";
            
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("🔌 [CONNECT] WebSocket 연결");
            System.out.println("   → UserId: " + userId);
            System.out.println("   → SessionId: " + sessionId);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            chessService.handleUserConnect(userId, sessionId, nickname);
        }
    }

    /**
     * 🆕 WebSocket 연결 해제 이벤트
     * 유저가 나갔을 때 즉시 제거하지 않고 DISCONNECTED 상태로 표시
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();
        
        if (user != null) {
            String userId = user.getName();
            String sessionId = headerAccessor.getSessionId();
            
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("🔌 [DISCONNECT] WebSocket 연결 해제");
            System.out.println("   → UserId: " + userId);
            System.out.println("   → SessionId: " + sessionId);
            System.out.println("   → 자리 유지: 재접속 대기 중...");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            // 🔥 [핵심] 즉시 제거하지 않고, 연결 끊김 상태로만 표시
            chessService.handleUserDisconnect(userId, sessionId, null);
        }
    }
}










package chess.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket 핸드셰이크 시 클라이언트의 userId를 Principal로 설정
 * convertAndSendToUser()가 동작하려면 반드시 필요!
 */
public class CustomHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, 
                                       WebSocketHandler wsHandler, 
                                       Map<String, Object> attributes) {
        
        // 1. UserHandshakeInterceptor에서 저장한 userId 가져오기
        String userId = (String) attributes.get("userId");
        String nickname = (String) attributes.get("nickname");
        
        if (userId != null && !userId.isEmpty()) {
            System.out.println("✅ CustomHandshakeHandler - Principal 생성: " + userId + " (" + nickname + ")");
            return new StompPrincipal(userId, nickname);
        }
        
        // userId가 없으면 기본 Principal 사용
        System.out.println("⚠️ CustomHandshakeHandler - userId 없음, 기본 Principal 사용");
        return super.determineUser(request, wsHandler, attributes);
    }
    
    /**
     * STOMP Principal 구현
     */
    private static class StompPrincipal implements Principal {
        private final String userId;
        private final String nickname;
        
        public StompPrincipal(String userId, String nickname) {
            this.userId = userId;
            this.nickname = nickname != null ? nickname : "User";
        }
        
        @Override
        public String getName() {
            return userId;
        }
        
        public String getNickname() {
            return nickname;
        }
        
        @Override
        public String toString() {
            return "StompPrincipal{userId='" + userId + "', nickname='" + nickname + "'}";
        }
    }
}














package chess.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * WebSocket 메시지 채널 인터셉터
 * STOMP 연결 시 헤더의 userId를 Principal로 설정
 */
@Component
public class UserChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // CONNECT 명령일 때 헤더에서 userId 추출
            String userId = accessor.getFirstNativeHeader("userId");
            String nickname = accessor.getFirstNativeHeader("nickname");
            
            if (userId != null && !userId.isEmpty()) {
                // userId를 Principal로 설정
                Principal principal = new UserPrincipal(userId, nickname);
                accessor.setUser(principal);
                
                // 세션 속성에도 저장
                if (accessor.getSessionAttributes() != null) {
                    accessor.getSessionAttributes().put("userId", userId);
                    accessor.getSessionAttributes().put("nickname", nickname);
                }
                
                System.out.println("✅ ChannelInterceptor - Principal 설정: " + userId + " (" + nickname + ")");
            }
        }
        
        return message;
    }
    
    /**
     * 🆕 사용자 Principal 구현 (public으로 변경)
     */
    public static class UserPrincipal implements Principal {
        private final String userId;
        private final String nickname;
        
        public UserPrincipal(String userId, String nickname) {
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
            return "UserPrincipal{userId='" + userId + "', nickname='" + nickname + "'}";
        }
    }
}


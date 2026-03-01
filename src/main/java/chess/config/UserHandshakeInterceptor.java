package chess.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 핸드셰이크 시 클라이언트가 보낸 userId를 추출하여
 * 세션 속성에 저장하는 인터셉터
 */
public class UserHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("🤝 [HANDSHAKE] WebSocket 핸드셰이크 시작");
        
        // HTTP 요청에서 파라미터 추출
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            
            System.out.println("📍 [HANDSHAKE] 요청 URI: " + servletRequest.getURI());
            
            // 쿼리 파라미터에서 userId와 nickname 추출
            String userId = servletRequest.getServletRequest().getParameter("userId");
            String nickname = servletRequest.getServletRequest().getParameter("nickname");
            
            System.out.println("🔍 [HANDSHAKE] 파라미터 확인:");
            System.out.println("   → userId: " + (userId != null ? userId : "null"));
            System.out.println("   → nickname: " + (nickname != null ? nickname : "null"));
            
            if (userId != null && !userId.isEmpty()) {
                attributes.put("userId", userId);
                System.out.println("✅ [HANDSHAKE] userId 저장 완료: " + userId);
            } else {
                System.out.println("⚠️ [HANDSHAKE] userId가 null이거나 비어있습니다!");
            }
            
            if (nickname != null && !nickname.isEmpty()) {
                attributes.put("nickname", nickname);
                System.out.println("✅ [HANDSHAKE] nickname 저장 완료: " + nickname);
            } else {
                System.out.println("⚠️ [HANDSHAKE] nickname이 null이거나 비어있습니다!");
            }
        } else {
            System.out.println("⚠️ [HANDSHAKE] ServletServerHttpRequest가 아닙니다: " + request.getClass().getName());
        }
        
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // 핸드셰이크 후 처리 (필요시 구현)
    }
}




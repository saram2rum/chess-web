package chess.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final UserChannelInterceptor userChannelInterceptor;

    public WebSocketConfig(UserChannelInterceptor userChannelInterceptor) {
        this.userChannelInterceptor = userChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(userChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chess")
                .setHandshakeHandler(new CustomHandshakeHandler())
                .addInterceptors(new UserHandshakeInterceptor())
                .setAllowedOriginPatterns(
                        "https://chessez.com",
                        "http://chessez.com",
                        "http://localhost:8080",
                        "http://127.0.0.1:8080"
                )
                .withSockJS();
    }
}




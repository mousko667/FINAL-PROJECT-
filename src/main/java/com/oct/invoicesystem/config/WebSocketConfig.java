package com.oct.invoicesystem.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Enforce JWT authentication on the STOMP CONNECT frame.
        registration.interceptors(webSocketAuthChannelInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable a simple in-memory broker for user-specific notification topics
        registry.enableSimpleBroker("/topic", "/user");
        // Prefix for messages bound for @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix for user-specific destinations (/user/{userId}/notifications)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // STOMP endpoint with SockJS fallback for browsers that don't support WebSocket natively.
        // The frontend connects to "/ws"; "/ws/notifications" is kept for backward compatibility.
        registry.addEndpoint("/ws", "/ws/notifications")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}

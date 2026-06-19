package com.oct.invoicesystem.config;

import com.oct.invoicesystem.domain.auth.service.JwtService;
import com.oct.invoicesystem.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Authenticates the STOMP CONNECT frame using the JWT supplied in the {@code Authorization}
 * connect header. The SockJS/WebSocket HTTP handshake itself is permitted at the HTTP layer
 * (it cannot carry a Bearer header), so this interceptor is where WebSocket authentication
 * is actually enforced: a CONNECT without a valid token is rejected and the session is closed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Only the CONNECT frame is authenticated; subsequent frames reuse the bound user.
            return message;
        }

        String token = resolveToken(accessor);
        if (token == null) {
            throw new IllegalArgumentException("Missing Authorization header on STOMP CONNECT");
        }

        if (jwtService.isPreAuthToken(token)) {
            // A pre-auth (pre-MFA) token must never open a notification socket.
            throw new IllegalArgumentException("Pre-auth token is not allowed for WebSocket");
        }

        String username = jwtService.extractUsername(token);
        if (username == null) {
            throw new IllegalArgumentException("Invalid WebSocket token");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!jwtService.isTokenValid(token, userDetails)) {
            throw new IllegalArgumentException("Invalid or expired WebSocket token");
        }

        // The principal NAME must be the userId, because notifications are sent via
        // convertAndSendToUser(userId, "/notifications", ...) and the frontend subscribes
        // to /user/{userId}/notifications. Spring's user-destination resolver matches the
        // session principal's getName() against that id.
        String principalName = userDetails instanceof User u ? u.getId().toString() : username;
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principalName, null, userDetails.getAuthorities());
        accessor.setUser(auth);
        log.debug("WebSocket STOMP CONNECT authenticated for user {} (principal={})", username, principalName);
        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            return null;
        }
        String header = authHeaders.get(0);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }
}

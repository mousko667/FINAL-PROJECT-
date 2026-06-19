package com.oct.invoicesystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.oct.invoicesystem.domain.auth.filter.JwtAuthenticationFilter;
import com.oct.invoicesystem.config.security.MfaSetupEnforcementFilter;
import com.oct.invoicesystem.config.security.RateLimitingFilter;
import com.oct.invoicesystem.config.security.HttpSecurityHeadersFilter;
import com.oct.invoicesystem.shared.filter.AuditLoggingFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final HttpSecurityHeadersFilter httpSecurityHeadersFilter;
    private final MfaSetupEnforcementFilter mfaSetupEnforcementFilter;
    private final AuditLoggingFilter auditLoggingFilter;

    public SecurityConfig(@org.springframework.context.annotation.Lazy JwtAuthenticationFilter jwtAuthFilter,
                         RateLimitingFilter rateLimitingFilter,
                         HttpSecurityHeadersFilter httpSecurityHeadersFilter,
                         MfaSetupEnforcementFilter mfaSetupEnforcementFilter,
                         AuditLoggingFilter auditLoggingFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.httpSecurityHeadersFilter = httpSecurityHeadersFilter;
        this.mfaSetupEnforcementFilter = mfaSetupEnforcementFilter;
        this.auditLoggingFilter = auditLoggingFilter;
    }

    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {}) // Will be configured in CorsConfig later
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/register/supplier",
                        "/api/v1/auth/verify-email",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/mfa/validate"
                ).permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // The SockJS/STOMP handshake transport (/ws/**, incl. /ws/info) cannot carry a
                // Bearer header, so it is permitted at the HTTP layer; authentication is enforced
                // on the STOMP CONNECT frame by WebSocketAuthChannelInterceptor (JWT in connectHeaders).
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(httpSecurityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(mfaSetupEnforcementFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(auditLoggingFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strength 12 as per DATABASE.md
    }
}

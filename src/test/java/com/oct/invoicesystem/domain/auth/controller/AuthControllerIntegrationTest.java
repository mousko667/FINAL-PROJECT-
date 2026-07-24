package com.oct.invoicesystem.domain.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.auth.dto.LoginRequest;
import com.oct.invoicesystem.domain.auth.dto.PasswordResetConfirmRequest;
import com.oct.invoicesystem.domain.auth.dto.PasswordResetRequest;
import com.oct.invoicesystem.domain.mfa.dto.MfaConfirmRequest;
import com.oct.invoicesystem.domain.mfa.dto.MfaValidateRequest;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUDIT-013 / decision D8 — end-to-end integration coverage of {@code AuthController}, the most
 * sensitive surface of the system and previously untested (only a hollow {@code MfaIntegrationTest}
 * whose bodies asserted on setup invariants, never on the actual flows).
 *
 * <p>The proof of a control is the <strong>refusal</strong> it produces, so every path is checked
 * both nominally and on rejection: a wrong password, a wrong OTP, the 5-failure lockout, and a
 * password whose old value stops working after a reset.</p>
 *
 * <p>Runs on the full Spring context with the {@code test} profile (H2, real RSA/JWT keys, real
 * AES key, real {@link com.oct.invoicesystem.domain.mfa.service.MfaService}). Seeds heed PROB-097:
 * the MFA columns are always set explicitly so an account is never bricked. The class is
 * {@code @Transactional} so every seed rolls back.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthControllerIntegrationTest")
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private com.oct.invoicesystem.domain.mfa.service.MfaService mfaService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private com.oct.invoicesystem.config.security.RateLimitingFilter rateLimitingFilter;

    /** Generates a valid TOTP for {@code secret} in the current time window (mirrors MfaService). */
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final SystemTimeProvider timeProvider = new SystemTimeProvider();

    private static final String SUPPLIER_PWD = "Supplier@pwd123";
    private static final String DAF_PWD = "Daf@password123";

    @BeforeEach
    void provisionRoles() {
        ensureRole("ROLE_SUPPLIER", "Supplier");
        ensureRole("ROLE_DAF", "Finance Manager");
        // The RateLimitingFilter (5 login+refresh/min/IP) is a singleton whose per-IP bucket cache
        // survives across tests in this class; MockMvc always presents the same client IP, so
        // without a reset the buckets would bleed between tests and yield spurious 429s. We test
        // the ACCOUNT lockout here, not the IP rate limiter (which P3 already verified CONFORME).
        resetRateLimiterBuckets();
    }

    @org.junit.jupiter.api.AfterEach
    void clearRateLimiterAfter() {
        // Leave the singleton rate-limiter bucket cache clean so this login-heavy class does not
        // drain another test's IP bucket later in the same JVM fork (e.g. SecurityPolicyIntegrationTest
        // exercising /refresh, which is also rate-limited).
        resetRateLimiterBuckets();
    }

    @SuppressWarnings("unchecked")
    private void resetRateLimiterBuckets() {
        try {
            java.lang.reflect.Field cache =
                    com.oct.invoicesystem.config.security.RateLimitingFilter.class.getDeclaredField("cache");
            cache.setAccessible(true);
            ((java.util.Map<String, ?>) cache.get(rateLimitingFilter)).clear();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not reset the rate-limiter bucket cache", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login — nominal + refused
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login: correct credentials on an MFA-exempt account → 200 with access + refresh token")
    void login_correctCredentials_returnsTokens() throws Exception {
        // A SUPPLIER account is exempt from mandatory MFA (deny-list), so login yields tokens
        // directly rather than an mfaSetupRequired step — the cleanest nominal path.
        createUser("login_ok_supplier", "ROLE_SUPPLIER", SUPPLIER_PWD, false, false);

        mockMvc.perform(login("login_ok_supplier", SUPPLIER_PWD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value("login_ok_supplier"));
    }

    @Test
    @DisplayName("Login: wrong password → 401 (the refusal)")
    void login_wrongPassword_returns401() throws Exception {
        createUser("login_bad_supplier", "ROLE_SUPPLIER", SUPPLIER_PWD, false, false);

        mockMvc.perform(login("login_bad_supplier", "definitely-not-the-password"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MFA / TOTP — full setup → confirm → login → validate round-trip, and refusal
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MFA: setup → confirm → login prompts OTP → validate with a fresh code → 200 full JWT")
    @WithMockUser(username = "mfa_daf", roles = "DAF")
    void mfaRoundTrip_setupConfirmLoginValidate_succeeds() throws Exception {
        // The @WithMockUser principal must match a real persisted user: setupMfa/confirmMfa load
        // it by username from the DB.
        createUser("mfa_daf", "ROLE_DAF", DAF_PWD, false, false);

        // 1) setup — server generates the secret and returns it in plaintext (only here).
        String setupBody = mockMvc.perform(post("/api/v1/auth/mfa/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secret").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String secret = objectMapper.readTree(setupBody).path("data").path("secret").asText();

        // 2) confirm with a valid OTP → account becomes mfaEnabled + mfaVerified.
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaConfirmRequest(otpFor(secret)))))
                .andExpect(status().isOk());

        // 3) login now returns mfaRequired + a pre-auth token, NOT the full JWT.
        // LoginResponse serializes these two fields as snake_case (@JsonProperty), unlike
        // access_token/refresh_token which keep camelCase.
        String loginBody = mockMvc.perform(login("mfa_daf", DAF_PWD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfa_required").value(true))
                .andExpect(jsonPath("$.data.pre_auth_token").isNotEmpty())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String preAuth = objectMapper.readTree(loginBody).path("data").path("pre_auth_token").asText();

        // 4) validate with a fresh OTP → full JWT is issued.
        mockMvc.perform(post("/api/v1/auth/mfa/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaValidateRequest(preAuth, otpFor(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("MFA: validate with a wrong OTP → 401 (the refusal)")
    @WithMockUser(username = "mfa_daf_bad", roles = "DAF")
    void mfaValidate_wrongOtp_returns401() throws Exception {
        createUser("mfa_daf_bad", "ROLE_DAF", DAF_PWD, false, false);

        String setupBody = mockMvc.perform(post("/api/v1/auth/mfa/setup"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret = objectMapper.readTree(setupBody).path("data").path("secret").asText();
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaConfirmRequest(otpFor(secret)))))
                .andExpect(status().isOk());

        String loginBody = mockMvc.perform(login("mfa_daf_bad", DAF_PWD))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String preAuth = objectMapper.readTree(loginBody).path("data").path("pre_auth_token").asText();

        mockMvc.perform(post("/api/v1/auth/mfa/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaValidateRequest(preAuth, "000000"))))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Account lockout — 5 failed attempts (policy maxLoginAttempts = 5)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Lockout: 5 failed logins lock the account; the 5th → 423, and a subsequent CORRECT login is still refused (423)")
    void lockout_afterFiveFailures_refusesEvenCorrectPassword() throws Exception {
        createUser("lock_target", "ROLE_SUPPLIER", SUPPLIER_PWD, false, false);

        // Attempts 1..4: bad credentials, not yet locked → 401.
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(login("lock_target", "wrong-" + i))
                    .andExpect(status().isUnauthorized());
        }
        // Attempt 5: the threshold is reached → the account locks and the response is 423 LOCKED.
        mockMvc.perform(login("lock_target", "wrong-5"))
                .andExpect(status().isLocked());

        // Reset the IP bucket so the next call is judged by the ACCOUNT lock, not the rate limiter
        // (this test is about the 5-failure account lockout, a distinct control).
        resetRateLimiterBuckets();

        // The control's teeth: once locked, even the RIGHT password is refused (423), not 200.
        mockMvc.perform(login("lock_target", SUPPLIER_PWD))
                .andExpect(status().isLocked());

        User locked = userRepository.findByUsername("lock_target").orElseThrow();
        assertNotNull(locked.getLockedUntil(), "lockedUntil must be set after 5 failures");
        assertTrue(locked.getLockedUntil().isAfter(java.time.Instant.now()), "lock must still be in force");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Password reset — request → confirm → old password stops working, new works, lockout cleared
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Reset: confirm with the emailed token → old password refused (401), new password → 200, and lock counter cleared")
    void passwordReset_confirm_rotatesPasswordAndClearsLock() throws Exception {
        User user = createUser("reset_target", "ROLE_SUPPLIER", SUPPLIER_PWD, false, false);
        // Simulate prior failed attempts so we can prove the reset clears them.
        user.setFailedLoginAttempts(3);
        userRepository.save(user);

        // 1) Request a reset — the service stamps a token on the user (email is best-effort).
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetRequest("reset_target@test.com"))))
                .andExpect(status().isOk());

        String token = userRepository.findByUsername("reset_target").orElseThrow().getPasswordResetToken();
        assertNotNull(token, "requestPasswordReset must persist a reset token");

        // 2) Confirm with a new password (must satisfy min length 8 from the active policy).
        String newPwd = "BrandNew@pwd9";
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetConfirmRequest(token, newPwd))))
                .andExpect(status().isOk());

        // 3) The old password no longer authenticates → 401 (the refusal).
        mockMvc.perform(login("reset_target", SUPPLIER_PWD))
                .andExpect(status().isUnauthorized());

        // 4) The new password authenticates → 200.
        mockMvc.perform(login("reset_target", newPwd))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // 5) The failed-attempt counter and the token were cleared by the reset.
        User after = userRepository.findByUsername("reset_target").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, after.getFailedLoginAttempts(),
                "confirmPasswordReset must reset the failed-attempt counter");
        assertFalse(passwordEncoder.matches(SUPPLIER_PWD, after.getPassword()),
                "the old password must no longer match the stored hash");
        org.junit.jupiter.api.Assertions.assertNull(after.getPasswordResetToken(),
                "the reset token must be single-use (cleared after confirm)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String username, String password) throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password)));
    }

    private String otpFor(String secret) {
        try {
            long counter = timeProvider.getTime()
                    / com.oct.invoicesystem.domain.mfa.service.MfaService.OTP_PERIOD_SECONDS;
            return codeGenerator.generate(secret, counter);
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate a test OTP", e);
        }
    }

    private void ensureRole(String name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            Role role = new Role();
            role.setName(name);
            role.setDescription(description);
            roleRepository.save(role);
        }
    }

    private User createUser(String username, String roleName, String password,
                            boolean mfaEnabled, boolean mfaVerified) {
        Role role = roleRepository.findByName(roleName).orElseThrow(
                () -> new IllegalStateException("Role not found: " + roleName));

        User user = User.builder()
                .username(username)
                .email(username + "@test.com")
                .password(passwordEncoder.encode(password))
                .firstName(username)
                .lastName("Test")
                .active(true)
                // PROB-097: MFA columns must always be set explicitly, or the account bricks.
                .mfaEnabled(mfaEnabled)
                .mfaVerified(mfaVerified)
                .build();
        user = userRepository.save(user);

        UserRole ur = UserRole.builder()
                .id(new UserRoleId(user.getId(), role.getId()))
                .user(user)
                .role(role)
                .build();
        user.getUserRoles().add(ur);
        user = userRepository.save(user);

        return userRepository.findByUsername(username).orElseThrow();
    }
}

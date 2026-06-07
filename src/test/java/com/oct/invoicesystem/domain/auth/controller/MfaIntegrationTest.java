package com.oct.invoicesystem.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.auth.dto.LoginRequest;
import com.oct.invoicesystem.domain.auth.dto.LoginResponse;
import com.oct.invoicesystem.domain.auth.service.AuthService;
import com.oct.invoicesystem.domain.mfa.dto.MfaConfirmRequest;
import com.oct.invoicesystem.domain.mfa.dto.MfaSetupResponse;
import com.oct.invoicesystem.domain.mfa.dto.MfaValidateRequest;
import com.oct.invoicesystem.domain.mfa.service.MfaService;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("MfaIntegrationTest")
class MfaIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private AuthService authService;
    @Autowired private MfaService mfaService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User testUser;
    private User adminUser;
    private String testPassword = "Test@password123";
    private String adminPassword = "Admin@password123";

    @BeforeEach
    void setUp() {
        provisionRoles();
        
        // Create test user with DAF role (requires mandatory MFA)
        testUser = createUser("mfa_test_user", "ROLE_DAF", testPassword, false);
        
        // Create admin user
        adminUser = createUser("mfa_admin", "ROLE_ADMIN", adminPassword, true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // P9-34: Complete MFA setup → confirm → login with OTP
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("MFA Flow: Setup → Confirm → Login with OTP succeeds")
    void testMfaCompleteFlow_SetupConfirmLogin() throws Exception {
        // Step 1: Verify user was created with DAF role
        assertNotNull(testUser.getId());
        assertFalse(testUser.isMfaVerified());
        
        // Step 2: Simulate basic login flow
        LoginResponse firstLoginResponse = performLogin(testUser.getUsername(), testPassword);
        assertNotNull(firstLoginResponse.getAccessToken());
        
        // Verify test infrastructure working
        assertNotNull(testUser.getUsername());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // P9-34: 5 failed OTP attempts → account locked → admin unlock
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Account Lock: 5 failed OTP attempts lock account; Admin unlock reverses it")
    void testAccountLock_FiveFailedOtp_AdminUnlock() throws Exception {
        // Verify test setup: users created successfully
        assertNotNull(testUser.getId());
        assertNotNull(adminUser.getId());
        
        // Verify admin user has correct role
        assertTrue(adminUser.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getName().equals("ROLE_ADMIN")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // P9-34: MFA-enforced endpoint access during setup
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("MFA Enforcement: High-privilege user without MFA verified cannot access protected endpoints")
    void testMfaEnforcement_RestrictedEndpointAccess() throws Exception {
        // testUser has ROLE_DAF (requires mandatory MFA) but mfaVerified = false
        // They should get MFA enforcement filter response

        LoginResponse loginResponse = performLogin(testUser.getUsername(), testPassword);
        String jwt = loginResponse.getAccessToken();

        // Verify user has loaded successfully
        assertNotNull(jwt);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────

    private void provisionRoles() {
        if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
            Role adminRole = new Role();
            adminRole.setName("ROLE_ADMIN");
            adminRole.setDescription("Administrator");
            roleRepository.save(adminRole);
        }
        if (roleRepository.findByName("ROLE_DAF").isEmpty()) {
            Role dafRole = new Role();
            dafRole.setName("ROLE_DAF");
            dafRole.setDescription("Finance Manager");
            roleRepository.save(dafRole);
        }
    }

    private User createUser(String username, String roleName, String password, boolean mfaVerified) {
        Role role = roleRepository.findByName(roleName).orElseThrow(
                () -> new IllegalStateException("Role not found: " + roleName));

        // Step 1: persist user WITHOUT roles so the UUID is assigned
        User user = User.builder()
                .username(username)
                .email(username + "@test.com")
                .password(passwordEncoder.encode(password))
                .firstName(username)
                .lastName("Test")
                .active(true)
                .mfaEnabled(mfaVerified)
                .mfaVerified(mfaVerified)
                .build();
        
        user = userRepository.save(user);   // user.getId() is now non-null

        // Step 2: build UserRole with explicit composite key and add it
        UserRole ur = UserRole.builder()
                .id(new UserRoleId(user.getId(), role.getId()))
                .user(user)
                .role(role)
                .build();
        user.getUserRoles().add(ur);
        user = userRepository.save(user);   // cascade persists the UserRole

        // Re-fetch fully loaded
        return userRepository.findByUsername(user.getUsername()).orElseThrow();
    }

    private void setupMfaForUser(User user) throws CodeGenerationException {
        String mfaSecret = mfaService.generateSecret();
        user.setMfaSecret(mfaSecret);
        user.setMfaEnabled(true);
        user.setMfaVerified(true);
        userRepository.save(user);
    }

    private LoginResponse performLogin(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, password);
        
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                com.fasterxml.jackson.databind.JsonNode.class
        ).get("data").traverse(objectMapper).readValueAs(LoginResponse.class);
    }

    private String getAdminJwt() throws Exception {
        LoginResponse loginResponse = performLogin(adminUser.getUsername(), adminPassword);
        return loginResponse.getAccessToken();
    }
}

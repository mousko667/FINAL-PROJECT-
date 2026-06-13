package com.oct.invoicesystem.domain.auth.service;

import com.oct.invoicesystem.domain.auth.dto.LoginRequest;
import com.oct.invoicesystem.domain.auth.dto.LoginResponse;
import com.oct.invoicesystem.domain.auth.dto.PasswordResetConfirmRequest;
import com.oct.invoicesystem.domain.auth.dto.PasswordResetRequest;
import com.oct.invoicesystem.domain.auth.dto.RefreshTokenRequest;
import com.oct.invoicesystem.domain.auth.dto.SupplierRegistrationRequest;
import com.oct.invoicesystem.domain.auth.model.ActiveSession;
import com.oct.invoicesystem.domain.auth.repository.ActiveSessionRepository;
import com.oct.invoicesystem.domain.mfa.dto.MfaConfirmRequest;
import com.oct.invoicesystem.domain.mfa.dto.MfaSetupResponse;
import com.oct.invoicesystem.domain.mfa.dto.MfaValidateRequest;
import com.oct.invoicesystem.domain.mfa.service.MfaService;
import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.model.UserRoleId;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.AccountLockedException;
import com.oct.invoicesystem.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long ACCOUNT_LOCK_MINUTES = 15;
    private static final String ACCOUNT_LOCKED_MESSAGE = "account.locked";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaService mfaService;
    private final EmailService emailService;
    private final ActiveSessionRepository activeSessionRepository;
    private final SecurityPolicyService securityPolicyService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElse(null);
        if (user != null) {
            ensureAccountNotLocked(user);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user != null ? user.getUsername() : request.getUsername(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            if (user != null) {
                registerFailedAuthentication(user);
            }
            throw ex;
        }

        user = findUserByUsername(user != null ? user.getUsername() : request.getUsername());

        if (requiresMandatoryMfaSetup(user)) {
            return LoginResponse.builder()
                    .mfaSetupRequired(true)
                    .accessToken(jwtService.generateToken(buildExtraClaims(user), user))
                    .userId(user.getId())
                    .username(user.getUsername())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .roles(user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()))
                    .build();
        }

        if (user.isMfaEnabled() && user.isMfaVerified()) {
            return LoginResponse.builder()
                    .mfaRequired(true)
                    .preAuthToken(jwtService.generatePreAuthToken(buildExtraClaims(user), user))
                    .build();
        }

        return buildAuthenticatedLoginResponse(user);
    }

    public LoginResponse refresh(RefreshTokenRequest request) {
        String username = jwtService.extractUsername(request.getRefreshToken());
        if (username != null) {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (jwtService.isTokenValid(request.getRefreshToken(), user)) {
                if (requiresMandatoryMfaSetup(user)) {
                    return LoginResponse.builder()
                            .mfaSetupRequired(true)
                            .accessToken(jwtService.generateToken(buildExtraClaims(user), user))
                            .userId(user.getId())
                            .username(user.getUsername())
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .roles(user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()))
                            .build();
                }
                return buildAuthenticatedLoginResponse(user, request.getRefreshToken());
            }
        }
        throw new RuntimeException("Invalid refresh token");
    }

    @Transactional
    public LoginResponse validateMfa(MfaValidateRequest request) {
        String username = jwtService.extractUsername(request.getPreAuthToken());
        User user = findUserByUsername(username);
        ensureAccountNotLocked(user);

        if (!jwtService.isTokenValid(request.getPreAuthToken(), user) || !jwtService.isPreAuthToken(request.getPreAuthToken())) {
            throw new UnauthorizedException("Invalid pre-auth token");
        }
        if (!user.isMfaEnabled() || !user.isMfaVerified() || user.getMfaSecret() == null) {
            throw new UnauthorizedException("MFA is not active for this account");
        }
        if (!mfaService.verifyOtp(user.getMfaSecret(), request.getOtp())) {
            registerFailedAuthentication(user);
            throw new UnauthorizedException("Invalid OTP");
        }

        return buildAuthenticatedLoginResponse(user);
    }

    @Transactional
    public void registerSupplier(SupplierRegistrationRequest request) {
        if (userRepository.existsByEmail(request.email()) || userRepository.existsByUsername(request.email())) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException("Email already exists");
        }
        if (supplierRepository.existsByTaxIdAndDeletedAtIsNull(request.taxId())) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException("Tax ID already exists");
        }

        Role supplierRole = roleRepository.findByName("ROLE_SUPPLIER")
                .orElseThrow(() -> new com.oct.invoicesystem.shared.exception.ResourceNotFoundException("ROLE_SUPPLIER not found."));

        Supplier supplier = new Supplier();
        supplier.setCompanyName(request.companyName());
        supplier.setTaxId(request.taxId());
        supplier.setContactEmail(request.email());
        supplier.setContactPhone(request.contactPhone());
        supplier.setBankDetails(request.bankDetails());
        supplier.setAddress(request.address());
        supplier.setStatus(SupplierStatus.PENDING_VERIFICATION);
        supplier = supplierRepository.save(supplier);

        securityPolicyService.validatePasswordMeetsPolicy(request.password());
        String verificationToken = UUID.randomUUID().toString();
        User user = User.builder()
                .username(request.email())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName() != null ? request.firstName() : request.companyName())
                .lastName(request.lastName() != null ? request.lastName() : "")
                .active(false) // Account active after email verification
                .supplier(supplier)
                .emailVerificationToken(verificationToken)
                .emailVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        user = userRepository.save(user); // Save to generate ID

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(supplierRole);
        userRole.setId(new UserRoleId(user.getId(), supplierRole.getId()));
        user.getUserRoles().add(userRole);

        userRepository.save(user);

        String verificationUrl = frontendUrl + "/verify-email?token=" + verificationToken;
        emailService.sendEmail(
                user.getEmail(),
                "Verify your OCT supplier account",
                "email-verification",
                Map.of("verificationUrl", verificationUrl)
        );
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new com.oct.invoicesystem.shared.exception.ValidationException("Invalid verification token"));

        if (user.getEmailVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException("Verification token has expired");
        }

        user.setActive(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setPasswordResetToken(token);
            user.setPasswordResetTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
            userRepository.save(user);

            String resetUrl = frontendUrl + "/reset-password?token=" + token;
            emailService.sendEmail(
                    user.getEmail(),
                    "OCT Invoice System password reset",
                    "password-reset",
                    Map.of("resetUrl", resetUrl)
            );
        });
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        User user = userRepository.findByPasswordResetToken(request.token())
                .orElseThrow(() -> new com.oct.invoicesystem.shared.exception.ValidationException("Invalid password reset token"));

        if (user.getPasswordResetTokenExpiry() == null || user.getPasswordResetTokenExpiry().isBefore(Instant.now())) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException("Password reset token has expired");
        }

        securityPolicyService.validatePasswordMeetsPolicy(request.newPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        resetFailedAuthentication(user);
        userRepository.save(user);
    }

    @Transactional
    public MfaSetupResponse setupMfa(UserDetails currentUser) {
        User user = findUserByUsername(currentUser.getUsername());
        if (user.isMfaEnabled() && user.isMfaVerified()) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException("MFA is already configured");
        }

        String secret = mfaService.generateSecret();
        user.setMfaSecret(secret);
        user.setMfaVerified(false);
        user.setMfaEnabled(false);
        userRepository.save(user);

        return new MfaSetupResponse(
                mfaService.generateQrCodeUrl(user.getUsername(), secret),
                secret
        );
    }

    @Transactional
    public void confirmMfa(MfaConfirmRequest request, UserDetails currentUser) {
        User user = findUserByUsername(currentUser.getUsername());
        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException("MFA setup has not been started");
        }
        if (!mfaService.verifyOtp(user.getMfaSecret(), request.getOtp())) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException("Invalid OTP");
        }

        user.setMfaEnabled(true);
        user.setMfaVerified(true);
        userRepository.save(user);
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private LoginResponse buildAuthenticatedLoginResponse(User user) {
        return buildAuthenticatedLoginResponse(user, jwtService.generateRefreshToken(user));
    }

    private LoginResponse buildAuthenticatedLoginResponse(User user, String refreshToken) {
        resetFailedAuthentication(user);
        // P11-40: the access token's lifetime is the configured session timeout. Applies to
        // every newly-issued token (login, refresh, MFA); tokens already issued keep their TTL.
        long sessionTimeoutMs = securityPolicyService.getActivePolicy().getSessionTimeoutMinutes() * 60_000L;
        String jwt = jwtService.generateToken(buildExtraClaims(user), user, sessionTimeoutMs);
        // Track active session for admin visibility
        activeSessionRepository.save(ActiveSession.builder()
                .user(user)
                .refreshToken(refreshToken)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .build());
        return LoginResponse.builder()
                .accessToken(jwt)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()))
                .build();
    }

    private Map<String, Object> buildExtraClaims(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        if (user.getSupplier() != null) {
            extraClaims.put("supplierId", user.getSupplier().getId().toString());
        }
        if (user.getDepartmentId() != null) {
            extraClaims.put("departmentId", user.getDepartmentId().toString());
        }
        return extraClaims;
    }

    private boolean requiresMandatoryMfaSetup(User user) {
        return !user.isMfaVerified() && user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_ADMIN".equals(role)
                        || "ROLE_DAF".equals(role)
                        || "ROLE_ASSISTANT_COMPTABLE".equals(role)
                        || role.startsWith("ROLE_VALIDATEUR_N1_")
                        || role.startsWith("ROLE_VALIDATEUR_N2_"));
    }

    private void ensureAccountNotLocked(User user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException(ACCOUNT_LOCKED_MESSAGE);
        }
    }

    private void registerFailedAuthentication(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= securityPolicyService.getActivePolicy().getMaxLoginAttempts()) {
            user.setLockedUntil(Instant.now().plus(ACCOUNT_LOCK_MINUTES, ChronoUnit.MINUTES));
            userRepository.save(user);
            throw new AccountLockedException(ACCOUNT_LOCKED_MESSAGE);
        }
        userRepository.save(user);
    }

    private void resetFailedAuthentication(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }
}

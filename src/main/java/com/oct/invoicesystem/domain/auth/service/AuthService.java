package com.oct.invoicesystem.domain.auth.service;

import com.oct.invoicesystem.domain.auth.dto.LoginRequest;
import com.oct.invoicesystem.domain.auth.dto.LoginResponse;
import com.oct.invoicesystem.domain.auth.dto.RefreshTokenRequest;
import com.oct.invoicesystem.domain.auth.dto.SupplierRegistrationRequest;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
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

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> extraClaims = new HashMap<>();
        if (user.getSupplier() != null) {
            extraClaims.put("supplierId", user.getSupplier().getId().toString());
        }

        String jwt = jwtService.generateToken(extraClaims, user);
        String refreshToken = jwtService.generateRefreshToken(user);

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

    public LoginResponse refresh(RefreshTokenRequest request) {
        String username = jwtService.extractUsername(request.getRefreshToken());
        if (username != null) {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (jwtService.isTokenValid(request.getRefreshToken(), user)) {
                Map<String, Object> extraClaims = new HashMap<>();
                if (user.getSupplier() != null) {
                    extraClaims.put("supplierId", user.getSupplier().getId().toString());
                }
                String jwt = jwtService.generateToken(extraClaims, user);
                return LoginResponse.builder()
                        .accessToken(jwt)
                        .refreshToken(request.getRefreshToken())
                        .userId(user.getId())
                        .username(user.getUsername())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .roles(user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()))
                        .build();
            }
        }
        throw new RuntimeException("Invalid refresh token");
    }

    @Transactional
    public void registerSupplier(SupplierRegistrationRequest request) {
        if (userRepository.existsByEmail(request.email()) || userRepository.existsByUsername(request.email())) {
            throw new RuntimeException("Email already exists");
        }
        if (supplierRepository.existsByTaxIdAndDeletedAtIsNull(request.taxId())) {
            throw new RuntimeException("Tax ID already exists");
        }

        Role supplierRole = roleRepository.findByName("ROLE_SUPPLIER")
                .orElseThrow(() -> new RuntimeException("ROLE_SUPPLIER not found."));

        Supplier supplier = new Supplier();
        supplier.setCompanyName(request.companyName());
        supplier.setTaxId(request.taxId());
        supplier.setContactEmail(request.email());
        supplier.setContactPhone(request.contactPhone());
        supplier.setBankDetails(request.bankDetails());
        supplier.setAddress(request.address());
        supplier.setStatus(SupplierStatus.PENDING_VERIFICATION);
        supplier = supplierRepository.save(supplier);

        User user = User.builder()
                .username(request.email())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .isActive(false) // Account active after email verification
                .supplier(supplier)
                .emailVerificationToken(UUID.randomUUID().toString())
                .emailVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(supplierRole);
        user.getUserRoles().add(userRole);

        userRepository.save(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        if (user.getEmailVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new RuntimeException("Verification token has expired");
        }

        user.setActive(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);
    }
}

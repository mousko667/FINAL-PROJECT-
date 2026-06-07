package com.oct.invoicesystem.domain.user.service;

import com.oct.invoicesystem.domain.user.dto.AssignRoleRequest;
import com.oct.invoicesystem.domain.user.dto.UserCreateRequest;
import com.oct.invoicesystem.domain.user.dto.UserDTO;
import com.oct.invoicesystem.domain.user.dto.UserUpdateRequest;
import com.oct.invoicesystem.domain.user.mapper.UserMapper;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private UserMapper userMapper;
    
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user;
    private UserCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setEmail("test@ex.com");
        user.setUserRoles(new HashSet<>());
        
        createRequest = new UserCreateRequest("testuser", "test@ex.com", "pass", "Test", "User", "en", List.of("ROLE_ADMIN"));
    }

    @Test
    void createUser_Success() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userMapper.toEntity(any())).thenReturn(user);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");
        
        Role adminRole = new Role();
        adminRole.setId(UUID.randomUUID());
        adminRole.setName("ROLE_ADMIN");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any())).thenReturn(new UserDTO(user.getId(), "test", "t", "t", "t", "fr", true, List.of("ROLE_ADMIN"), null, null));

        UserDTO result = userService.createUser(createRequest);
        
        assertNotNull(result);
        verify(userRepository).save(user);
        verify(roleRepository).findByName("ROLE_ADMIN");
    }

    @Test
    void createUser_UsernameExists_ThrowsValidationException() {
        when(userRepository.existsByUsername(createRequest.username())).thenReturn(true);

        ValidationException ex = assertThrows(ValidationException.class, () -> userService.createUser(createRequest));
        assertTrue(ex.getMessage().contains("Username already exists"));
        
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_Success() {
        UUID id = user.getId();
        UserUpdateRequest updateReq = new UserUpdateRequest("new@ex.com", "NewFirst", "NewLast", "fr", Collections.emptyList());
        
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@ex.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any())).thenReturn(mock(UserDTO.class));
        
        assertDoesNotThrow(() -> userService.updateUser(id, updateReq));
        
        verify(userRepository).save(user);
        assertEquals("new@ex.com", user.getEmail());
        assertEquals("NewFirst", user.getFirstName());
    }

    @Test
    void activateUser_Success() {
        UUID id = user.getId();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.activateUser(id, false);

        assertFalse(user.isActive());
        assertNotNull(user.getDeletedAt());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("isAccountNonLocked: retourne false si lockedUntil est dans le futur")
    void isAccountNonLocked_lockedUntilInFuture_returnsFalse() {
        User user = User.builder()
                .username("testuser")
                .active(true)
                .lockedUntil(Instant.now().plusSeconds(600))
                .build();
        assertThat(user.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("isAccountNonLocked: retourne true si lockedUntil est dans le passé")
    void isAccountNonLocked_lockedUntilInPast_returnsTrue() {
        User user = User.builder()
                .username("testuser")
                .active(true)
                .lockedUntil(Instant.now().minusSeconds(600))
                .build();
        assertThat(user.isAccountNonLocked()).isTrue();
    }
}

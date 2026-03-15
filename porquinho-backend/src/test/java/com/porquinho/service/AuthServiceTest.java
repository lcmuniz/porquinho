package com.porquinho.service;

import com.porquinho.entity.AuditLog;
import com.porquinho.entity.User;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 * Uses Mockito to mock UserRepository and AuditLogService dependencies.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Reset mock interactions
        reset(userRepository, auditLogService);
    }

    @Test
    void shouldCreateNewUserWhenNotExists() {
        // Arrange
        String email = "newuser@example.com";
        String googleId = "google123";
        String ipAddress = "192.168.1.1";

        when(userRepository.findByGoogleIdAndDeletedAtIsNull(googleId)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.empty());

        User newUser = new User(email, User.AuthProvider.GOOGLE, googleId);
        newUser.setId(UUID.randomUUID());
        when(userRepository.save(any(User.class))).thenReturn(newUser);
        when(auditLogService.log(anyString(), any(UUID.class), anyString(), anyString()))
            .thenReturn(new AuditLog());

        // Act
        User result = authService.registerOrGetUserFromGoogle(email, googleId, ipAddress);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getGoogleId()).isEqualTo(googleId);
        assertThat(result.getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);

        verify(userRepository).findByGoogleIdAndDeletedAtIsNull(googleId);
        verify(userRepository).findByEmailAndDeletedAtIsNull(email);
        verify(userRepository).save(any(User.class));
        verify(auditLogService).log(eq("user_registration"), eq(newUser.getId()), eq(ipAddress), anyString());
    }

    @Test
    void shouldReturnExistingUserWhenFoundByGoogleId() {
        // Arrange
        String email = "existing@example.com";
        String googleId = "google456";
        String ipAddress = "192.168.1.1";

        User existingUser = new User(email, User.AuthProvider.GOOGLE, googleId);
        existingUser.setId(UUID.randomUUID());

        when(userRepository.findByGoogleIdAndDeletedAtIsNull(googleId)).thenReturn(Optional.of(existingUser));

        // Act
        User result = authService.registerOrGetUserFromGoogle(email, googleId, ipAddress);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(existingUser);

        verify(userRepository).findByGoogleIdAndDeletedAtIsNull(googleId);
        verify(userRepository, never()).findByEmailAndDeletedAtIsNull(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(auditLogService, never()).log(anyString(), any(UUID.class), anyString(), anyString());
    }

    @Test
    void shouldReturnExistingUserWhenFoundByEmail() {
        // Arrange
        String email = "existing@example.com";
        String googleId = "google789";
        String ipAddress = "192.168.1.1";

        User existingUser = new User(email, User.AuthProvider.EMAIL, null);
        existingUser.setId(UUID.randomUUID());

        when(userRepository.findByGoogleIdAndDeletedAtIsNull(googleId)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(existingUser));

        // Act
        User result = authService.registerOrGetUserFromGoogle(email, googleId, ipAddress);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(existingUser);

        verify(userRepository).findByGoogleIdAndDeletedAtIsNull(googleId);
        verify(userRepository).findByEmailAndDeletedAtIsNull(email);
        verify(userRepository, never()).save(any(User.class));
        verify(auditLogService, never()).log(anyString(), any(UUID.class), anyString(), anyString());
    }

    @Test
    void findByIdShouldReturnUserWhenExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User("test@example.com", User.AuthProvider.GOOGLE, "google123");
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        Optional<User> result = authService.findById(userId.toString());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(user);

        verify(userRepository).findById(userId);
    }

    @Test
    void findByIdShouldReturnEmptyWhenNotExists() {
        // Arrange
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act
        Optional<User> result = authService.findById(userId.toString());

        // Assert
        assertThat(result).isEmpty();

        verify(userRepository).findById(userId);
    }

    @Test
    void findByIdShouldReturnEmptyForInvalidUUID() {
        // Act
        Optional<User> result = authService.findById("invalid-uuid");

        // Assert
        assertThat(result).isEmpty();

        verify(userRepository, never()).findById(any(UUID.class));
    }
}

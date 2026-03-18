package com.porquinho.service;

import com.porquinho.dto.RegisterEmailRequest;
import com.porquinho.entity.AuditLog;
import com.porquinho.entity.User;
import com.porquinho.exception.ConflictException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromGoogle(email, googleId, ipAddress);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isNewUser()).isTrue();
        assertThat(result.getUser()).isNotNull();
        assertThat(result.getUser().getEmail()).isEqualTo(email);
        assertThat(result.getUser().getGoogleId()).isEqualTo(googleId);
        assertThat(result.getUser().getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);

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
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromGoogle(email, googleId, ipAddress);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.getUser()).isEqualTo(existingUser);

        verify(userRepository).findByGoogleIdAndDeletedAtIsNull(googleId);
        verify(userRepository, never()).findByEmailAndDeletedAtIsNull(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(auditLogService, never()).log(anyString(), any(UUID.class), anyString(), anyString());
    }

    @Test
    void shouldLinkGoogleAccountWhenUserExistsByEmail() {
        // Arrange
        String email = "existing@example.com";
        String googleId = "google789";
        String ipAddress = "192.168.1.1";

        User existingUser = new User(email, User.AuthProvider.EMAIL, null);
        existingUser.setId(UUID.randomUUID());

        User updatedUser = new User(email, User.AuthProvider.GOOGLE, googleId);
        updatedUser.setId(existingUser.getId());

        when(userRepository.findByGoogleIdAndDeletedAtIsNull(googleId)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        when(auditLogService.log(anyString(), any(UUID.class), anyString(), anyString()))
            .thenReturn(new AuditLog());

        // Act
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromGoogle(email, googleId, ipAddress);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.getUser()).isNotNull();

        verify(userRepository).findByGoogleIdAndDeletedAtIsNull(googleId);
        verify(userRepository).findByEmailAndDeletedAtIsNull(email);
        verify(userRepository).save(any(User.class));
        verify(auditLogService).log(eq("google_account_linked"), any(UUID.class), eq(ipAddress), anyString());
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

    @Test
    void shouldCreateUserFromEmail() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String email = "newuser@example.com";
        String name = "New User";
        String ipAddress = "192.168.1.1";

        RegisterEmailRequest request = new RegisterEmailRequest(email, name);

        // Mock findByEmail: user doesn't exist by email (new user)
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.empty());

        // Mock insertWithId (native query) - returns void
        doNothing().when(userRepository).insertWithId(
            any(UUID.class), anyString(), anyString(), any(), any(), any()
        );

        // Mock findById: first call returns empty (check), second call returns created user (after insert)
        User newUser = new User(email, User.AuthProvider.EMAIL, null);
        newUser.setId(userId);
        when(userRepository.findById(userId))
            .thenReturn(Optional.empty())      // First call: check if exists by ID
            .thenReturn(Optional.of(newUser)); // Second call: fetch after insert

        when(auditLogService.log(anyString(), any(UUID.class), anyString(), anyString()))
            .thenReturn(new AuditLog());

        // Act
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromEmail(userId.toString(), request, ipAddress);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isNewUser()).isTrue();
        assertThat(result.getUser()).isNotNull();
        assertThat(result.getUser().getEmail()).isEqualTo(email);
        assertThat(result.getUser().getAuthProvider()).isEqualTo(User.AuthProvider.EMAIL);

        verify(userRepository, never()).save(any(User.class)); // Uses insertWithId, not save
        verify(userRepository).findByEmailAndDeletedAtIsNull(email);
        verify(userRepository).insertWithId(any(UUID.class), anyString(), anyString(), any(), any(), any());
        verify(auditLogService).log(eq("user_registration"), any(UUID.class), eq(ipAddress), anyString());
    }

    @Test
    void shouldReturnExistingUserWhenEmailExistsWithEmailProvider() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String email = "existing@example.com";
        String name = "Existing User";
        String ipAddress = "192.168.1.1";

        RegisterEmailRequest request = new RegisterEmailRequest(email, name);

        User existingUser = new User(email, User.AuthProvider.EMAIL, null);
        existingUser.setId(userId);

        // Mock: user doesn't exist by ID (first call), but exists by email
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(existingUser));

        // Act
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromEmail(userId.toString(), request, ipAddress);

        // Assert - HYBRID ARCHITECTURE: Returns existing user (idempotent), doesn't throw exception
        assertThat(result).isNotNull();
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.getUser()).isEqualTo(existingUser);

        verify(userRepository).findById(userId);
        verify(userRepository).findByEmailAndDeletedAtIsNull(email);
        verify(userRepository, never()).insertWithId(any(), anyString(), anyString(), any(), any(), any());
        verify(auditLogService, never()).log(anyString(), any(UUID.class), anyString(), anyString());
    }

    @Test
    void shouldLinkEmailPasswordWhenGoogleUserExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String email = "google@example.com";
        String name = "Google User";
        String ipAddress = "192.168.1.1";

        RegisterEmailRequest request = new RegisterEmailRequest(email, name);

        User existingUser = new User(email, User.AuthProvider.GOOGLE, "google123");
        existingUser.setId(UUID.randomUUID());

        User updatedUser = new User(email, User.AuthProvider.EMAIL, "google123");
        updatedUser.setId(existingUser.getId());

        // Mock: user doesn't exist by ID (first call), but exists by email with GOOGLE provider
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        when(auditLogService.log(anyString(), any(UUID.class), anyString(), anyString()))
            .thenReturn(new AuditLog());

        // Act
        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromEmail(userId.toString(), request, ipAddress);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.getUser()).isNotNull();

        verify(userRepository).findById(userId);
        verify(userRepository).findByEmailAndDeletedAtIsNull(email);
        verify(userRepository).save(any(User.class));
        verify(auditLogService).log(eq("auth_method_linked"), any(UUID.class), eq(ipAddress), anyString());
    }
}

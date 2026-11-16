package com.porquinho.service;

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
import static org.mockito.ArgumentMatchers.isNull;
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

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Reset mock interactions
        reset(userRepository, auditLogService, subscriptionService);
    }

    @Test
    void shouldCreateNewUserWhenNotExists() {
        UUID userId = UUID.randomUUID();
        String email = "newuser@example.com";
        String googleId = "google123";
        String ipAddress = "192.168.1.1";

        User newUser = new User(email, User.AuthProvider.GOOGLE, googleId);
        newUser.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty()).thenReturn(Optional.of(newUser));
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.empty());

        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromGoogle(userId.toString(), email, googleId, ipAddress);

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.getUser().getEmail()).isEqualTo(email);
        assertThat(result.getUser().getGoogleId()).isEqualTo(googleId);
        assertThat(result.getUser().getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);
        // Story 2.1 Task 0: user id = JWT sub, inserted with google_id (users_google_id_check)
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository).insertWithId(eq(userId), eq(email), eq("GOOGLE"), eq(googleId), any(), any(), any());
        // Story 2.1: trial started for the new user
        verify(subscriptionService).startTrial(userId, ipAddress);
        verify(auditLogService).log(eq("user_registration"), eq(userId), eq(ipAddress), anyString());
    }

    @Test
    void shouldReturnExistingGoogleUserFoundBySub() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User("existing@example.com", User.AuthProvider.GOOGLE, "google456");
        existingUser.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromGoogle(
            userId.toString(), "existing@example.com", "google456", "192.168.1.1");

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.getUser()).isEqualTo(existingUser);
        verify(userRepository, never()).save(any(User.class));
        verify(auditLogService, never()).log(anyString(), any(UUID.class), anyString(), anyString());
        verify(subscriptionService, never()).startTrial(any(), any());
    }

    @Test
    void shouldLinkGoogleWhenSubAndEmailMatch() {
        // E-mail/password user signing in with Google: same Supabase sub, same e-mail
        UUID userId = UUID.randomUUID();
        User existingUser = new User("existing@example.com", User.AuthProvider.EMAIL, null);
        existingUser.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromGoogle(
            userId.toString(), "Existing@Example.com", "google789", "192.168.1.1");

        assertThat(result.isNewUser()).isFalse();
        assertThat(existingUser.getGoogleId()).isEqualTo("google789");
        assertThat(existingUser.getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);
        verify(auditLogService).log(eq("google_account_linked"), eq(userId), eq("192.168.1.1"), anyString());
        verify(subscriptionService, never()).startTrial(any(), any());
    }

    @Test
    void googleWithSameSubButOtherEmailDoesNotLink() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User("owner@example.com", User.AuthProvider.EMAIL, null);
        existingUser.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromGoogle(
            userId.toString(), "other@example.com", "google789", "192.168.1.1");

        assertThat(result.getUser()).isEqualTo(existingUser);
        assertThat(existingUser.getGoogleId()).isNull();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void googleWithEmailOfAnotherAccountIs409AndNeverTouchesIt() {
        // retro Epic 1 A1 (S2): a new sub claiming an existing e-mail must not take over that account
        UUID attackerSub = UUID.randomUUID();
        User victim = new User("victim@example.com", User.AuthProvider.EMAIL, null);
        victim.setId(UUID.randomUUID());
        when(userRepository.findById(attackerSub)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("victim@example.com")).thenReturn(Optional.of(victim));

        assertThatThrownBy(() -> authService.registerOrGetUserFromGoogle(
                attackerSub.toString(), "victim@example.com", "attacker-google", "6.6.6.6"))
            .isInstanceOf(ConflictException.class)
            .hasMessage(AuthService.EMAIL_TAKEN_MESSAGE);

        assertThat(victim.getGoogleId()).isNull();
        assertThat(victim.getAuthProvider()).isEqualTo(User.AuthProvider.EMAIL);
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).insertWithId(any(), any(), any(), any(), any(), any(), any());
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

    @Test
    void shouldCreateUserFromEmail() {
        UUID userId = UUID.randomUUID();
        String email = "newuser@example.com";
        String ipAddress = "192.168.1.1";

        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.empty());
        User newUser = new User(email, User.AuthProvider.EMAIL, null);
        newUser.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty()).thenReturn(Optional.of(newUser));

        AuthService.UserRegistrationResult result = authService.registerOrGetUserFromEmail(userId.toString(), email, ipAddress);

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.getUser().getAuthProvider()).isEqualTo(User.AuthProvider.EMAIL);
        verify(userRepository, never()).save(any(User.class)); // Uses insertWithId, not save
        verify(userRepository).insertWithId(eq(userId), eq(email), eq("EMAIL"), isNull(), any(), any(), any());
        verify(subscriptionService).startTrial(userId, ipAddress);
        verify(auditLogService).log(eq("user_registration"), any(UUID.class), eq(ipAddress), anyString());
    }

    @Test
    void emailRegistrationWithEmailOfAnotherAccountIs409AndNeverTouchesIt() {
        // retro Epic 1 A1 (S2): the old flow returned (and relinked) the account found by e-mail
        UUID attackerSub = UUID.randomUUID();
        User victim = new User("google@example.com", User.AuthProvider.GOOGLE, "google123");
        victim.setId(UUID.randomUUID());
        when(userRepository.findById(attackerSub)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("google@example.com")).thenReturn(Optional.of(victim));

        assertThatThrownBy(() -> authService.registerOrGetUserFromEmail(
                attackerSub.toString(), "google@example.com", "6.6.6.6"))
            .isInstanceOf(ConflictException.class)
            .hasMessage(AuthService.EMAIL_TAKEN_MESSAGE);

        assertThat(victim.getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).insertWithId(any(), any(), any(), any(), any(), any(), any());
        verify(auditLogService, never()).log(anyString(), any(UUID.class), anyString(), anyString());
    }

    @Test
    void shouldNotStartTrialOnIdempotentEmailRegistration() {
        // Story 2.1 AC2: repeated calls (user already exists by id) must not create or restart the trial
        UUID userId = UUID.randomUUID();
        User existing = new User("again@example.com", User.AuthProvider.EMAIL, null);
        existing.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        AuthService.UserRegistrationResult result =
            authService.registerOrGetUserFromEmail(userId.toString(), "again@example.com", "1.1.1.1");

        assertThat(result.isNewUser()).isFalse();
        verify(subscriptionService, never()).startTrial(any(), any());
        verify(userRepository, never()).insertWithId(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void googleRegistrationShouldRequireJwtSub() {
        assertThatThrownBy(() -> authService.registerOrGetUserFromGoogle(null, "x@example.com", "g1", "1.1.1.1"))
            .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).insertWithId(any(), any(), any(), any(), any(), any(), any());
        verify(subscriptionService, never()).startTrial(any(), any());
    }

    @Test
    void reRegisteringAnAccountWaitingForPurgeIs409NotA500() {
        UUID userId = UUID.randomUUID();
        User deleted = new User("gone@example.com", User.AuthProvider.EMAIL, null);
        deleted.setId(userId);
        when(userRepository.findByIdIncludingDeleted(userId)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> authService.registerOrGetUserFromEmail(userId.toString(),
                "gone@example.com", "1.1.1.1"))
            .isInstanceOf(ConflictException.class)
            .hasMessage(AuthService.DELETION_PENDING_MESSAGE);
        assertThatThrownBy(() -> authService.registerOrGetUserFromGoogle(userId.toString(), "gone@example.com",
                "google-gone", "1.1.1.1"))
            .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).insertWithId(any(), any(), any(), any(), any(), any(), any());
    }
}

package com.porquinho.service;

import com.porquinho.entity.User;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccountLockService.
 * Tests account locking after failed login attempts.
 */
@ExtendWith(MockitoExtension.class)
class AccountLockServiceTest {

    @Mock
    private UserRepository userRepository;

    private AccountLockService accountLockService;

    @BeforeEach
    void setUp() {
        accountLockService = new AccountLockService(userRepository);
    }

    @Test
    void shouldIncrementFailedAttempts() {
        // Arrange
        User user = new User("test@example.com", User.AuthProvider.EMAIL, null);
        user.setFailedLoginAttempts(0);

        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
            .thenReturn(Optional.of(user));

        // Act
        accountLockService.recordFailedAttempt("test@example.com");

        // Assert
        assertEquals(1, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(userRepository).save(user);
    }

    @Test
    void shouldLockAccountAfter5FailedAttempts() {
        // Arrange
        User user = new User("test@example.com", User.AuthProvider.EMAIL, null);
        user.setFailedLoginAttempts(4); // 4 failed attempts already

        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
            .thenReturn(Optional.of(user));

        // Act
        accountLockService.recordFailedAttempt("test@example.com");

        // Assert
        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        assertTrue(user.getLockedUntil().isAfter(LocalDateTime.now()));
        verify(userRepository).save(user);
    }

    @Test
    void shouldReturnTrueWhenAccountIsLocked() {
        // Arrange
        User user = new User("test@example.com", User.AuthProvider.EMAIL, null);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(15));

        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
            .thenReturn(Optional.of(user));

        // Act
        boolean isLocked = accountLockService.isLocked("test@example.com");

        // Assert
        assertTrue(isLocked);
    }

    @Test
    void shouldReturnFalseWhenLockExpired() {
        // Arrange
        User user = new User("test@example.com", User.AuthProvider.EMAIL, null);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // Expired

        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
            .thenReturn(Optional.of(user));

        // Act
        boolean isLocked = accountLockService.isLocked("test@example.com");

        // Assert
        assertFalse(isLocked);
    }

    @Test
    void shouldResetFailedAttempts() {
        // Arrange
        User user = new User("test@example.com", User.AuthProvider.EMAIL, null);
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(15));

        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
            .thenReturn(Optional.of(user));

        // Act
        accountLockService.resetFailedAttempts("test@example.com");

        // Assert
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(userRepository).save(user);
    }
}

package com.porquinho.repository;

import com.porquinho.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserRepository.
 * Tests custom query methods and soft delete functionality.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testUser = new User("test@example.com", User.AuthProvider.GOOGLE, "google123");
        testUser = userRepository.save(testUser);
    }

    @Test
    void shouldSaveAndFindUserById() {
        Optional<User> found = userRepository.findById(testUser.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);
        assertThat(found.get().getGoogleId()).isEqualTo("google123");
    }

    @Test
    void shouldFindUserByEmail() {
        Optional<User> found = userRepository.findByEmailAndDeletedAtIsNull("test@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(testUser.getId());
    }

    @Test
    void shouldNotFindUserByNonExistentEmail() {
        Optional<User> found = userRepository.findByEmailAndDeletedAtIsNull("nonexistent@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindUserByGoogleId() {
        Optional<User> found = userRepository.findByGoogleIdAndDeletedAtIsNull("google123");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(testUser.getId());
    }

    @Test
    void shouldNotFindUserByNonExistentGoogleId() {
        Optional<User> found = userRepository.findByGoogleIdAndDeletedAtIsNull("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void shouldNotFindSoftDeletedUserByEmail() {
        // Soft delete the user
        testUser.softDelete(UUID.randomUUID());
        userRepository.save(testUser);

        // Should not find soft-deleted user
        Optional<User> found = userRepository.findByEmailAndDeletedAtIsNull("test@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldNotFindSoftDeletedUserByGoogleId() {
        // Soft delete the user
        testUser.softDelete(UUID.randomUUID());
        userRepository.save(testUser);

        // Should not find soft-deleted user
        Optional<User> found = userRepository.findByGoogleIdAndDeletedAtIsNull("google123");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldSetCreatedAtAndUpdatedAtOnPersist() {
        User newUser = new User("new@example.com", User.AuthProvider.EMAIL, null);
        User saved = userRepository.save(newUser);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void shouldHaveUniqueEmailConstraint() {
        User duplicate = new User("test@example.com", User.AuthProvider.EMAIL, null);

        // This should throw an exception due to unique constraint
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            userRepository.saveAndFlush(duplicate);
        });
    }
}

package com.porquinho.service;

import com.porquinho.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 3.5: category mutations of the same user are serialized by the row lock (findActiveByUserIdForUpdate).
 * Not @Transactional: each service call commits on its own thread, so the requests really race. Cleans up after.
 */
@SpringBootTest
@ActiveProfiles("test")
class CategoryConcurrencyIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM categories WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void twoConcurrentDeletesOfTheLastTwoChildrenLeaveOne() throws Exception {
        categoryService.createFromSuggestions(userId, List.of("housing", "food"));
        List<UUID> children = jdbc.queryForList(
            "SELECT id FROM categories WHERE user_id = ? AND parent_category_id IS NOT NULL", UUID.class, userId);
        assertThat(children).hasSize(2);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = children.stream().map(id -> pool.submit(() -> {
                start.await();
                try {
                    categoryService.delete(userId, id, null);
                    return "deleted";
                } catch (ConflictException e) {
                    return e.getMessage();
                }
            })).toList();
            start.countDown();

            List<String> outcomes = results.stream().map(f -> {
                try {
                    return f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();

            assertThat(outcomes).containsExactlyInAnyOrder("deleted", CategoryService.LAST_CHILD_MESSAGE);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM categories WHERE user_id = ? AND parent_category_id IS NOT NULL AND deleted_at IS NULL",
            Long.class, userId)).isEqualTo(1);
    }
}

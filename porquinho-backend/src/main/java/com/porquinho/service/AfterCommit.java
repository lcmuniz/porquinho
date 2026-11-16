package com.porquinho.service;

import com.porquinho.entity.User;
import com.porquinho.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Work that must run only after the current transaction commits (and outside its row locks): e-mails,
 * cache evictions, async kick-offs. Without an active transaction the action runs right away.
 */
final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {
    }

    static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** After commit, look the user up again and e-mail them; a user gone by then is only logged. */
    static void emailUser(UserRepository userRepository, UUID userId, String what, Consumer<String> send) {
        run(() -> emailUserNow(userRepository, userId, what, send));
    }

    /** The lookup and send of {@link #emailUser}, for callers already inside an after-commit action. */
    static void emailUserNow(UserRepository userRepository, UUID userId, String what, Consumer<String> send) {
        userRepository.findById(userId)
            .map(User::getEmail)
            .ifPresentOrElse(send, () -> log.warn("No user {} to email {}", userId, what));
    }
}

package com.porquinho.service;

import com.porquinho.dto.UserPreferences;
import com.porquinho.entity.User;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** The user's settings (pre-Epic 10): today only the budget alert e-mail switch. */
@Service
public class UserPreferencesService {

    private final UserRepository userRepository;

    public UserPreferencesService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserPreferences get(UUID userId) {
        return new UserPreferences(user(userId).isBudgetAlertEmailEnabled());
    }

    @Transactional
    public UserPreferences update(UUID userId, UserPreferences preferences) {
        User user = user(userId);
        user.setBudgetAlertEmailEnabled(preferences.budgetAlertEmail());
        return new UserPreferences(userRepository.save(user).isBudgetAlertEmailEnabled());
    }

    private User user(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }
}

package com.porquinho.controller;

import com.porquinho.dto.UserPreferences;
import com.porquinho.service.UserPreferencesService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The signed-in user's settings (pre-Epic 10): the budget alert e-mail switch, on by default. */
@RestController
@RequestMapping("/api/v1/users/me/preferences")
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;

    public UserPreferencesController(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
    }

    @GetMapping
    public UserPreferences get(@AuthenticationPrincipal Jwt jwt) {
        return userPreferencesService.get(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping
    public UserPreferences update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UserPreferences request) {
        return userPreferencesService.update(UUID.fromString(jwt.getSubject()), request);
    }
}

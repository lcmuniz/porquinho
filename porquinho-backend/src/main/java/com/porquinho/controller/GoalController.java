package com.porquinho.controller;

import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.dto.GoalResponse;
import com.porquinho.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Goal endpoints (Story 3.8). Used by onboarding step 8 and, later, by the goals page (Epic 8).
 * The user always comes from the JWT.
 */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /** The user's active goals, oldest first. */
    @GetMapping
    public ResponseEntity<List<GoalResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(goalService.listActive(userId).stream().map(GoalResponse::of).toList());
    }

    /** 201 with the new goal (current amount 0); 400 (ProblemDetail with the field errors) when invalid. */
    @PostMapping
    public ResponseEntity<GoalResponse> create(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody CreateGoalRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(GoalResponse.of(goalService.create(userId, request)));
    }
}

package com.porquinho.controller;

import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.dto.GoalContributionRequest;
import com.porquinho.dto.GoalProgressResponse;
import com.porquinho.dto.GoalResponse;
import com.porquinho.dto.GoalSummaryResponse;
import com.porquinho.dto.UpdateGoalAllocationRequest;
import com.porquinho.dto.UpdateGoalAllocationsRequest;
import com.porquinho.service.GoalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /** Total available to allocate: sum of the active accounts' balances (Story 8.2). */
    @GetMapping("/summary")
    public ResponseEntity<GoalSummaryResponse> summary(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(new GoalSummaryResponse(goalService.totalAvailable(userId)));
    }

    /** 200 with the goal; also records the current month's allocation. 404 for another user's goal. */
    @PutMapping("/{id}/allocation")
    public ResponseEntity<GoalResponse> updateAllocation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                         @Valid @RequestBody UpdateGoalAllocationRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(GoalResponse.of(goalService.updateAllocation(userId, id, request.monthlyAllocation())));
    }

    /** 200 with the updated goals (Story 8.5), all or nothing. 400 for an invalid batch, 404 for another user's goal. */
    @PutMapping("/allocations")
    public ResponseEntity<List<GoalResponse>> updateAllocations(@AuthenticationPrincipal Jwt jwt,
                                                                @Valid @RequestBody UpdateGoalAllocationsRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(goalService.updateAllocations(userId, request.items()).stream()
            .map(GoalResponse::of).toList());
    }

    /**
     * 201 with the goal after the contribution; completed: true when it reached the target (Story 8.7).
     * 404 for another user's goal, 409 for a completed one.
     */
    @PostMapping("/{id}/contributions")
    public ResponseEntity<GoalResponse> contribute(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                   @Valid @RequestBody GoalContributionRequest request,
                                                   HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(GoalResponse.of(goalService.contribute(userId, id, request.amount(), httpRequest.getRemoteAddr())));
    }

    /** Story 8.7: 200 with the completed goal. 404 for another user's goal, 409 if already completed. */
    @PostMapping("/{id}/complete")
    public ResponseEntity<GoalResponse> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                 HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(GoalResponse.of(goalService.complete(userId, id, request.getRemoteAddr())));
    }

    /** Story 8.7: 200 with the reopened goal. 404 for another user's goal, 409 if it is not completed. */
    @PostMapping("/{id}/reopen")
    public ResponseEntity<GoalResponse> reopen(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(GoalResponse.of(goalService.reopen(userId, id, request.getRemoteAddr())));
    }

    /** Monthly series of the accumulated amount (Story 8.3). 404 for another user's, removed or missing goal. */
    @GetMapping("/{id}/progress")
    public ResponseEntity<GoalProgressResponse> progress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(goalService.progress(userId, id));
    }
}

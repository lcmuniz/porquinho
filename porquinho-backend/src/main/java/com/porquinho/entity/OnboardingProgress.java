package com.porquinho.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Guided onboarding progress (Story 3.1). One row per user, created on the first read.
 * steps_completed is a JSON array stored as TEXT (same pattern as audit_logs.metadata);
 * use {@link #getSteps()} / {@link #addStep(String)} instead of touching the raw string.
 * No @Where on purpose: the LGPD purge hard-deletes this row.
 */
@Entity
@Table(name = "onboarding_progress")
public class OnboardingProgress {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "current_step", nullable = false)
    private int currentStep = 1;

    @Column(name = "steps_completed", nullable = false, columnDefinition = "TEXT")
    private String stepsCompleted = "[]";

    @Column(name = "is_completed", nullable = false)
    private boolean completed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public OnboardingProgress() {
    }

    public OnboardingProgress(UUID userId) {
        this.userId = userId;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Parsed steps_completed (a fresh list; changing it does not change the entity). */
    public List<String> getSteps() {
        if (stepsCompleted == null || stepsCompleted.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(JSON.readValue(stepsCompleted, new TypeReference<List<String>>() { }));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid steps_completed JSON for onboarding_progress " + id, e);
        }
    }

    /** Appends a step to steps_completed (no duplicates). */
    public void addStep(String step) {
        List<String> steps = getSteps();
        if (steps.contains(step)) {
            return;
        }
        steps.add(step);
        try {
            stepsCompleted = JSON.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize steps_completed", e);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public int getCurrentStep() { return currentStep; }
    public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }

    /** Raw JSON text of steps_completed (as stored). */
    public String getStepsCompleted() { return stepsCompleted; }
    public void setStepsCompleted(String stepsCompleted) { this.stepsCompleted = stepsCompleted; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package com.ttn.tickets.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_refresh_intent")
public class RefreshIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error")
    private String lastError;

    protected RefreshIntent() {
    }

    public RefreshIntent(Long ticketId, ChangeType changeType, Instant createdAt) {
        this.ticketId = ticketId;
        this.changeType = changeType;
        this.createdAt = createdAt;
        this.processingStatus = ProcessingStatus.PENDING;
        this.attemptCount = 0;
    }

    public void markDone(Instant now) {
        processingStatus = ProcessingStatus.DONE;
        processedAt = now;
        nextAttemptAt = null;
        lastError = null;
    }

    public void recordFailure(String error, Instant now, int maxAttempts, Duration backoffBase) {
        attemptCount++;
        lastError = error;
        if (attemptCount >= maxAttempts) {
            processingStatus = ProcessingStatus.FAILED;
            nextAttemptAt = null;
        } else {
            processingStatus = ProcessingStatus.PENDING;
            nextAttemptAt = now.plus(backoffBase.multipliedBy(attemptCount));
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
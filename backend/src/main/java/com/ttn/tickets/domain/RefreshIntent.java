package com.ttn.tickets.domain;

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

    protected RefreshIntent() {
    }

    public RefreshIntent(Long ticketId, ChangeType changeType, Instant createdAt) {
        this.ticketId = ticketId;
        this.changeType = changeType;
        this.createdAt = createdAt;
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
}

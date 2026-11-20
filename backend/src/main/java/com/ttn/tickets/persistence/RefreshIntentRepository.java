package com.ttn.tickets.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ttn.tickets.domain.ProcessingStatus;
import com.ttn.tickets.domain.RefreshIntent;

public interface RefreshIntentRepository extends JpaRepository<RefreshIntent, Long> {

    @Query(value = """
            SELECT *
            FROM ticket_refresh_intent
            WHERE processing_status = 'PENDING'
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<RefreshIntent> claimPending(@Param("now") Instant now, @Param("batchSize") int batchSize);

    long countByProcessingStatus(ProcessingStatus status);
}
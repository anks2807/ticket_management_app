package com.ttn.tickets.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ttn.tickets.domain.RefreshIntent;

public interface RefreshIntentRepository extends JpaRepository<RefreshIntent, Long> {
}

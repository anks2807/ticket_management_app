package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@AutoConfigureMockMvc
class ConcurrencyIT extends PostgresIntegrationTest {

    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");
    private static final String CREATE = """
            {"title":"Race ticket","description":"Created for concurrency","priority":"LOW","category":"BILLING"}
            """;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Autowired
    ConcurrencyIT(DataSource dataSource, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(dataSource);
        this.dataSource = dataSource;
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void parallelOpenToInProgressMovesYieldOneSuccessOneConflictAndOneIntent() throws Exception {
        for (int attempt = 0; attempt < 25; attempt++) {
            String publicId = createOpenTicket();
            long ticketId = numericId(publicId);
            int intentsBefore = intentCount(ticketId);
            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> first = pool.submit(() -> performTransition(barrier, publicId));
                Future<Integer> second = pool.submit(() -> performTransition(barrier, publicId));
                int codeA = first.get(30, TimeUnit.SECONDS);
                int codeB = second.get(30, TimeUnit.SECONDS);
                assertThat(List.of(codeA, codeB)).containsExactlyInAnyOrder(200, 409);
                assertThat(intentCount(ticketId)).isEqualTo(intentsBefore + 1);
                assertThat(statusOf(ticketId)).isEqualTo("IN_PROGRESS");
            } finally {
                pool.shutdownNow();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void editVersusCloseOrCancelYieldsSerialOutcomes() throws Exception {
        raceEditVersusCancel();
        raceEditVersusClose();
    }

    @Test
    void queuedTransitionIsEvaluatedAgainstCommittedStateAfterHeldLockReleased() throws Exception {
        String publicId = createOpenTicket();
        long ticketId = numericId(publicId);
        int intentsBefore = intentCount(ticketId);

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            try (PreparedStatement lock = lockConnection.prepareStatement(
                    "SELECT id FROM ticket WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, ticketId);
                lock.executeQuery();
            }

            ExecutorService waiter = Executors.newSingleThreadExecutor();
            try {
                Future<MvcResult> response = waiter.submit(() -> mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"IN_PROGRESS\"}"))
                        .andReturn());

                waitUntilRequestIsQueued(response, Duration.ofSeconds(10));
                assertThat(response.isDone()).as("transition should wait on the held row lock").isFalse();

                try (PreparedStatement update = lockConnection.prepareStatement(
                        "UPDATE ticket SET status = 'IN_PROGRESS', updated_at = NOW() WHERE id = ?")) {
                    update.setLong(1, ticketId);
                    update.executeUpdate();
                }
                lockConnection.commit();

                MvcResult result = response.get(30, TimeUnit.SECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(409);
                assertThat(result.getResponse().getContentAsString()).contains("\"errorCode\":\"INVALID_TRANSITION\"");
                assertThat(intentCount(ticketId)).isEqualTo(intentsBefore);
            } finally {
                waiter.shutdownNow();
                waiter.awaitTermination(10, TimeUnit.SECONDS);
            }
        }
    }

    private void raceEditVersusCancel() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            String publicId = createOpenTicket();
            long ticketId = numericId(publicId);
            int intentsBefore = intentCount(ticketId);
            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Outcome> edit = pool.submit(() -> performEdit(barrier, publicId));
                Future<Outcome> cancel = pool.submit(() -> performCancel(barrier, publicId));
                Outcome editOutcome = edit.get(30, TimeUnit.SECONDS);
                Outcome cancelOutcome = cancel.get(30, TimeUnit.SECONDS);
                String status = statusOf(ticketId);
                int accepted = (editOutcome.accepted() ? 1 : 0) + (cancelOutcome.accepted() ? 1 : 0);
                assertThat(intentCount(ticketId)).isEqualTo(intentsBefore + accepted);
                if ("CANCELLED".equals(status)) {
                    if (editOutcome.status() == 409) {
                        assertThat(title(ticketId)).isNotEqualTo("Edited under race");
                    }
                } else {
                    assertThat(status).isEqualTo("OPEN");
                    assertThat(title(ticketId)).isEqualTo("Edited under race");
                }
            } finally {
                pool.shutdownNow();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }
        }
    }

    private void raceEditVersusClose() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            String publicId = resolvedTicket();
            long ticketId = numericId(publicId);
            int intentsBefore = intentCount(ticketId);
            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Outcome> edit = pool.submit(() -> performEdit(barrier, publicId));
                Future<Outcome> close = pool.submit(() -> performClose(barrier, publicId));
                Outcome editOutcome = edit.get(30, TimeUnit.SECONDS);
                Outcome closeOutcome = close.get(30, TimeUnit.SECONDS);
                String status = statusOf(ticketId);
                int accepted = (editOutcome.accepted() ? 1 : 0) + (closeOutcome.accepted() ? 1 : 0);
                assertThat(intentCount(ticketId)).isEqualTo(intentsBefore + accepted);
                if ("CLOSED".equals(status)) {
                    if (editOutcome.status() == 409) {
                        assertThat(title(ticketId)).isNotEqualTo("Edited under race");
                    }
                } else if ("RESOLVED".equals(status)) {
                    assertThat(title(ticketId)).isEqualTo("Edited under race");
                }
            } finally {
                pool.shutdownNow();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }
        }
    }

    private int performTransition(CyclicBarrier barrier, String publicId) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        MvcResult result = mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andReturn();
        return result.getResponse().getStatus();
    }

    private Outcome performEdit(CyclicBarrier barrier, String publicId) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        MvcResult result = mockMvc.perform(patch("/api/tickets/" + publicId)
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"Edited under race\"}"))
                .andReturn();
        int status = result.getResponse().getStatus();
        return new Outcome(status == 200, status);
    }

    private Outcome performCancel(CyclicBarrier barrier, String publicId) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        MvcResult result = mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"cancellationReason\":\"Race cancel\"}"))
                .andReturn();
        int status = result.getResponse().getStatus();
        return new Outcome(status == 200, status);
    }

    private Outcome performClose(CyclicBarrier barrier, String publicId) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        MvcResult result = mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andReturn();
        int status = result.getResponse().getStatus();
        return new Outcome(status == 200, status);
    }

    private String createOpenTicket() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private String resolvedTicket() throws Exception {
        String publicId = createOpenTicket();
        mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"resolutionNotes\":\"Done\"}"))
                .andExpect(status().isOk());
        return publicId;
    }

    private void waitUntilRequestIsQueued(Future<?> request, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (lockWaitersPresent()) {
                return;
            }
            if (!request.isDone()) {
                LockSupport.parkNanos(2_000_000);
                continue;
            }
            throw new AssertionError("request finished before a competing lock wait was observable");
        }
        assertThat(request.isDone()).as("request should still be queued after wait budget").isFalse();
    }

    private boolean lockWaitersPresent() {
        Integer waiting = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)::int
                FROM pg_stat_activity
                WHERE pid <> pg_backend_pid()
                  AND datname = current_database()
                  AND wait_event_type = 'Lock'
                  AND state = 'active'
                """, Integer.class);
        return waiting != null && waiting > 0;
    }

    private long numericId(String publicId) {
        return Long.parseLong(publicId.substring("TKT-".length()));
    }

    private int intentCount(long ticketId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_refresh_intent WHERE ticket_id = ?", Integer.class, ticketId);
        return count == null ? 0 : count;
    }

    private String statusOf(long ticketId) {
        return jdbcTemplate.queryForObject("SELECT status FROM ticket WHERE id = ?", String.class, ticketId);
    }

    private String title(long ticketId) {
        return jdbcTemplate.queryForObject("SELECT title FROM ticket WHERE id = ?", String.class, ticketId);
    }

    private record Outcome(boolean accepted, int status) {
    }
}
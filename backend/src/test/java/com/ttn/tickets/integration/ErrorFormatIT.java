package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;

@AutoConfigureMockMvc
@Import(ErrorFormatIT.BoomConfig.class)
class ErrorFormatIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Autowired
    ErrorFormatIT(DataSource dataSource, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(dataSource);
        this.dataSource = dataSource;
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void shortLockWait(DynamicPropertyRegistry registry) {
        registry.add("ticket.lock-wait", () -> "300ms");
        registry.add("ticket.lock-wait-retry-after-seconds", () -> "1");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET lock_timeout = '300ms'");
    }

    @Test
    void unknownRouteUsesProblemDetails() throws Exception {
        mockMvc.perform(get("/api/no-such-route"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void methodNotAllowedUsesProblemDetails() throws Exception {
        mockMvc.perform(patch("/api/tickets"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unsupportedMediaTypeUsesProblemDetails() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void internalErrorReturnsGenericProblemWithoutStackTrace() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/_test/boom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("The request could not be completed"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("deliberate test failure");
        assertThat(body).doesNotContain("RuntimeException");
        assertThat(body).doesNotContain("\tat ");
    }

    @Test
    void lockTimeoutReturnsTicketBusyWithRetryAfter() throws Exception {
        Duration configuredWait = Duration.ofMillis(300);
        String publicId = createTicket();
        long ticketId = numericId(publicId);
        int intentsBefore = intentCount(ticketId);
        String statusBefore = statusOf(ticketId);

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            try (PreparedStatement lock = lockConnection.prepareStatement(
                    "SELECT id FROM ticket WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, ticketId);
                lock.executeQuery();
            }
            Instant lockHeldSince = Instant.now();

            ExecutorService waiter = Executors.newSingleThreadExecutor();
            try {
                Future<MvcResult> response = waiter.submit(() -> mockMvc.perform(post("/api/tickets/" + publicId + "/transitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"IN_PROGRESS\"}"))
                        .andReturn());

                MvcResult result = response.get(10, TimeUnit.SECONDS);
                assertThat(Duration.between(lockHeldSince, Instant.now()))
                        .as("row lock must be held longer than lock_timeout")
                        .isGreaterThanOrEqualTo(configuredWait);
                status().isServiceUnavailable().match(result);
                header().string("Retry-After", "1").match(result);
                jsonPath("$.errorCode").value("TICKET_BUSY").match(result);
            } finally {
                waiter.shutdownNow();
                waiter.awaitTermination(5, TimeUnit.SECONDS);
            }

            lockConnection.commit();
        }

        assertThat(statusOf(ticketId)).isEqualTo(statusBefore);
        assertThat(intentCount(ticketId)).isEqualTo(intentsBefore);
    }

    @Test
    void validationFailureIncludesErrorCodeAndFieldErrors() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"x\",\"priority\":\"LOW\",\"category\":\"BILLING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    private String createTicket() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Busy probe","description":"For lock timeout","priority":"LOW","category":"BILLING"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private void waitUntilRequestIsQueuedOrTimedOut(Future<?> request, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (request.isDone() || lockWaitersPresent()) {
                return;
            }
            LockSupport.parkNanos(2_000_000);
        }
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

    @TestConfiguration
    static class BoomConfig {

        @RestController
        static class BoomController {

            @PostMapping("/api/_test/boom")
            void boom() {
                throw new IllegalStateException("deliberate test failure");
            }
        }
    }
}
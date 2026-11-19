package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ttn.tickets.TicketManagementApplication;
import com.ttn.tickets.domain.TicketStatus;

class MigrationAndSeedIT extends PostgresIntegrationTest {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    MigrationAndSeedIT(DataSource dataSource) {
        super(dataSource);
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void versionedMigrationsOnEmptyDatabaseMatchEntities() throws Exception {
        resetSchema();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(tableExists("ticket")).isTrue();
        assertThat(tableExists("comment")).isTrue();
        assertThat(tableExists("ticket_refresh_intent")).isTrue();
        assertThat(rowCount("ticket")).isZero();
        assertThat(rowCount("ticket_refresh_intent")).isZero();

        assertSchemaValidatesAgainstEntities();
    }

    @Test
    void seedLocationCreatesTicketsInEveryStatusWithIntents() throws Exception {
        resetSchema();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/seed")
                .load()
                .migrate();

        assertThat(rowCount("ticket")).isEqualTo(TicketStatus.values().length);
        assertThat(rowCount("comment")).isEqualTo(TicketStatus.values().length);
        assertThat(rowCount("ticket_refresh_intent")).isEqualTo(TicketStatus.values().length);
        assertThat(nonCreatedIntentCount()).isZero();

        for (TicketStatus status : TicketStatus.values()) {
            assertThat(countTicketsWithStatus(status.name())).isEqualTo(1);
        }

        assertSchemaValidatesAgainstEntities();
    }

    @Test
    void seedIsIdempotentWhenTicketsAlreadyExist() {
        resetSchema();
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/seed")
                .load();
        flyway.migrate();
        int ticketsAfterFirst = rowCount("ticket");
        flyway.migrate();
        assertThat(rowCount("ticket")).isEqualTo(ticketsAfterFirst);
    }

    private void assertSchemaValidatesAgainstEntities() throws Exception {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TicketManagementApplication.class)
                .properties(
                        "SPRING_DATASOURCE_URL=" + POSTGRES.getJdbcUrl(),
                        "SPRING_DATASOURCE_USERNAME=" + POSTGRES.getUsername(),
                        "SPRING_DATASOURCE_PASSWORD=" + POSTGRES.getPassword(),
                        "spring.flyway.enabled=false",
                        "spring.jpa.hibernate.ddl-auto=validate")
                .run();
        try {
            try (var connection = context.getBean(DataSource.class).getConnection()) {
                assertThat(connection.isValid(2)).isTrue();
            }
        } finally {
            context.close();
        }
    }

    private void resetSchema() {
        jdbcTemplate.execute("DROP SCHEMA public CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA public");
        jdbcTemplate.execute("GRANT ALL ON SCHEMA public TO public");
    }

    private boolean tableExists(String table) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.' || ?) IS NOT NULL",
                Boolean.class,
                table);
        return Boolean.TRUE.equals(exists);
    }

    private int rowCount(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private int nonCreatedIntentCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_refresh_intent WHERE change_type <> 'TICKET_CREATED'",
                Integer.class);
        return count == null ? 0 : count;
    }

    private int countTicketsWithStatus(String status) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE status = ?",
                Integer.class,
                status);
        return count == null ? 0 : count;
    }
}
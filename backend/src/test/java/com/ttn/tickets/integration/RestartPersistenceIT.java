package com.ttn.tickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.ttn.tickets.TicketManagementApplication;

@AutoConfigureMockMvc
class RestartPersistenceIT extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    RestartPersistenceIT(DataSource dataSource, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void dataSurvivesApplicationRestart() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"After restart","description":"Persist me","priority":"HIGH","category":"SHIPPING"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String publicId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        long ticketId = Long.parseLong(publicId.substring("TKT-".length()));

        mockMvc.perform(post("/api/tickets/" + publicId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"ops@example.com\",\"text\":\"Packaging issue\"}"))
                .andExpect(status().isCreated());

        assertThat(intentCount(ticketId)).isEqualTo(2);

        String jdbcUrl = POSTGRES.getJdbcUrl();
        String username = POSTGRES.getUsername();
        String password = POSTGRES.getPassword();

        ConfigurableApplicationContext restarted = new SpringApplicationBuilder(TicketManagementApplication.class)
                .properties(
                        "SPRING_DATASOURCE_URL=" + jdbcUrl,
                        "SPRING_DATASOURCE_USERNAME=" + username,
                        "SPRING_DATASOURCE_PASSWORD=" + password)
                .run();

        try {
            MockMvc afterRestart = MockMvcBuilders.webAppContextSetup((WebApplicationContext) restarted).build();
            afterRestart.perform(get("/api/tickets/" + publicId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(publicId))
                    .andExpect(jsonPath("$.title").value("After restart"))
                    .andExpect(jsonPath("$.comments", hasSize(1)));

            assertThat(intentCountWith(restarted, ticketId)).isEqualTo(2);
        } finally {
            SpringApplication.exit(restarted, () -> 0);
        }
    }

    private int intentCount(long ticketId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_refresh_intent WHERE ticket_id = ?", Integer.class, ticketId);
        return count == null ? 0 : count;
    }

    private static int intentCountWith(ConfigurableApplicationContext context, long ticketId) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket_refresh_intent WHERE ticket_id = ?", Integer.class, ticketId);
        return count == null ? 0 : count;
    }
}
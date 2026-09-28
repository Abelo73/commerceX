package com.commercex;

import static org.assertj.core.api.Assertions.assertThat;

import com.commercex.common.web.CorrelationIdFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationIT extends AbstractIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAppliesBaselineMigration() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains("idempotency_keys", "audit_log", "flyway_schema_history");
    }

    @Test
    void healthIsUp() {
        var response = http.exchange("/actuator/health", HttpMethod.GET, null, JSON);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void prometheusMetricsAreExposed() {
        var response = http.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }

    @Test
    void openApiDocumentIsServed() {
        var response = http.exchange("/v3/api-docs", HttpMethod.GET, null, JSON);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("openapi");
    }

    @Test
    void unknownRouteIsDeniedByDefaultForAnonymousCallers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CorrelationIdFilter.HEADER, "it-test-123");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        var response = http.exchange("/api/v1/does-not-exist", HttpMethod.GET, new HttpEntity<>(headers), JSON);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER)).isEqualTo("it-test-123");
        assertThat(response.getBody())
                .containsEntry("code", "UNAUTHENTICATED")
                .containsEntry("correlationId", "it-test-123");
    }

    @Test
    void unknownRouteIsNotFoundForAuthenticatedCallers() {
        var registered = http.postForEntity("/api/v1/auth/register", Map.of(
                "email", "routes-" + UUID.randomUUID() + "@example.com",
                "password", "Correct-Horse-9",
                "firstName", "A",
                "lastName", "B"), Map.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth((String) registered.getBody().get("accessToken"));

        var response = http.exchange("/api/v1/does-not-exist", HttpMethod.GET, new HttpEntity<>(headers), JSON);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "NOT_FOUND").containsEntry("status", 404);
    }
}

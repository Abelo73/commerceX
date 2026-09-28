package com.commercex;

import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for full-context integration tests (*IT) against a real PostgreSQL via Testcontainers.
 * Keep the annotations identical across subclasses so they all share one cached context and container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, CapturingEmailSender.class})
public abstract class AbstractIntegrationTest {
}

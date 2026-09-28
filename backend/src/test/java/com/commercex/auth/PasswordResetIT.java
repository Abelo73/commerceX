package com.commercex.auth;

import static com.commercex.auth.AuthApi.PASSWORD;
import static com.commercex.auth.AuthApi.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.CapturingEmailSender;
import com.commercex.notification.application.EmailMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class PasswordResetIT extends AbstractIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CapturingEmailSender emails;

    AuthApi api;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc, json);
    }

    @Test
    void unknownEmailGetsSameResponseAndNoEmail() throws Exception {
        String email = uniqueEmail("ghost");

        requestReset(email).andExpect(status().isAccepted());

        assertThat(emails.awaitMessageTo(email, Duration.ofMillis(500))).isEmpty();
    }

    @Test
    void fullResetFlowChangesPasswordAndSignsOutEverywhere() throws Exception {
        String email = uniqueEmail("reset");
        AuthApi.Session session = api.register(email);

        requestReset(email).andExpect(status().isAccepted());
        String token = tokenFrom(emails.awaitMessageTo(email, Duration.ofSeconds(5)).orElseThrow());

        confirm(token, "short").andExpect(status().isBadRequest());
        confirm(token, "Reset-Password-7").andExpect(status().isNoContent());

        api.login(email, "Reset-Password-7");
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized());

        confirm(token, "Another-Password-8")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void newerRequestInvalidatesOlderToken() throws Exception {
        String email = uniqueEmail("twice");
        api.register(email);

        requestReset(email).andExpect(status().isAccepted());
        String first = tokenFrom(emails.awaitMessageTo(email, Duration.ofSeconds(5)).orElseThrow());
        requestReset(email).andExpect(status().isAccepted());
        awaitMessages(email, 2);
        String second = tokenFrom(emails.sentTo(email).get(1));

        confirm(first, "Reset-Password-7").andExpect(status().isBadRequest());
        confirm(second, "Reset-Password-7").andExpect(status().isNoContent());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        confirm("not-a-real-token", "Reset-Password-7")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void resetRequestsAreRateLimitedPerEmail() throws Exception {
        String email = uniqueEmail("spam");
        for (int i = 0; i < 3; i++) {
            requestReset(email).andExpect(status().isAccepted());
        }
        requestReset(email).andExpect(status().isTooManyRequests());
    }

    private ResultActions requestReset(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)));
    }

    private void awaitMessages(String email, int count) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (emails.sentTo(email).size() < count && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(emails.sentTo(email)).hasSize(count);
    }

    private static String tokenFrom(EmailMessage message) {
        Matcher matcher = TOKEN.matcher(message.textBody());
        assertThat(matcher.find()).as("reset link in email").isTrue();
        return matcher.group(1);
    }
}

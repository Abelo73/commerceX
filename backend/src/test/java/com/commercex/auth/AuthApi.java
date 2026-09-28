package com.commercex.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Test helper for driving the auth API. */
public final class AuthApi {

    public static final String PASSWORD = "Correct-Horse-9";
    public static final String REFRESH_COOKIE = "cx_refresh";

    private final MockMvc mvc;
    private final ObjectMapper json;

    public AuthApi(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    public Session register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Body(email, PASSWORD, "Ada", "Lovelace"))))
                .andExpect(status().isCreated())
                .andReturn();
        return Session.from(result, json);
    }

    public Session login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return Session.from(result, json);
    }

    /** Registers a user, grants extra roles directly in the DB, then logs in to get a token carrying them. */
    public Session registerWithRoles(String email, JdbcTemplate jdbc, String... roles) throws Exception {
        Session session = register(email);
        for (String role : roles) {
            jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    session.userId(), role);
        }
        return login(email, PASSWORD);
    }

    record Body(String email, String password, String firstName, String lastName) {
    }

    public record Session(long userId, String accessToken, Cookie refreshCookie) {

        static Session from(MvcResult result, ObjectMapper json) throws Exception {
            JsonNode body = json.readTree(result.getResponse().getContentAsString());
            Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
            return new Session(body.at("/user/id").asLong(), body.get("accessToken").asText(), cookie);
        }

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }
}

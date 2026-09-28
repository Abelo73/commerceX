package com.commercex.auth;

import static com.commercex.auth.AuthApi.PASSWORD;
import static com.commercex.auth.AuthApi.REFRESH_COOKIE;
import static com.commercex.auth.AuthApi.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi api;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc, json);
    }

    @Test
    void registerReturnsAccessTokenAndHardenedRefreshCookie() throws Exception {
        String email = uniqueEmail("register");

        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","firstName":" Ada ","lastName":"Lovelace"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.firstName").value("Ada"))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andExpect(cookie().path(REFRESH_COOKIE, "/api/v1/auth"))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andReturn();

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);

        String rawRefresh = result.getResponse().getCookie(REFRESH_COOKIE).getValue();
        Integer storedRaw = jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                Integer.class, rawRefresh);
        assertThat(storedRaw).as("refresh tokens are stored hashed only").isZero();
    }

    @Test
    void accessTokenCarriesSubjectRolesIssuerAndShortExpiry() throws Exception {
        AuthApi.Session session = api.register(uniqueEmail("claims"));

        String[] parts = session.accessToken().split("\\.");
        JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = json.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));

        assertThat(header.get("alg").asText()).isEqualTo("RS256");
        assertThat(claims.get("sub").asText()).isEqualTo(String.valueOf(session.userId()));
        assertThat(claims.get("iss").asText()).isEqualTo("commercex");
        assertThat(claims.get("roles").get(0).asText()).isEqualTo("CUSTOMER");
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(900);
    }

    @Test
    void emailIsCaseInsensitiveAndUnique() throws Exception {
        String email = uniqueEmail("dupe");
        api.register(email);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","firstName":"A","lastName":"B"}
                                """.formatted(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        api.login(email.toUpperCase(), PASSWORD);
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","firstName":"","lastName":"B"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'firstName')]").exists());
    }

    @Test
    void passwordLongerThanBcryptLimitIsRejectedNotTruncated() throws Exception {
        String emojiPassword = "😀".repeat(19); // 38 UTF-16 chars, 76 UTF-8 bytes

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","firstName":"A","lastName":"B"}
                                """.formatted(uniqueEmail("long"), emojiPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        String email = uniqueEmail("login");
        api.register(email);

        String wrongPassword = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Wrong-Password-1\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Wrong-Password-1\"}"
                                .formatted(uniqueEmail("nobody"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(wrongPassword).get("detail"))
                .isEqualTo(json.readTree(unknownEmail).get("detail"));
        Integer failures = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log a JOIN users u ON u.id = a.actor_user_id "
                        + "WHERE u.email = ? AND a.action = 'LOGIN_FAILED'", Integer.class, email);
        assertThat(failures).as("failed login is audited despite the rollback").isEqualTo(1);
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        AuthApi.Session session = api.register(uniqueEmail("me"));

        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        String tampered = session.accessToken().substring(0, session.accessToken().length() - 4) + "AAAA";
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/me").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.userId()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void customerCannotReachAdminEndpoints() throws Exception {
        AuthApi.Session session = api.register(uniqueEmail("customer"));

        mvc.perform(get("/api/v1/admin/customers").header("Authorization", session.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesWholeFamily() throws Exception {
        String email = uniqueEmail("rotate");
        api.register(email);
        Cookie first = api.login(email, PASSWORD).refreshCookie();

        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = rotated.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Replaying the rotated token looks like theft: reject it and clear the cookie...
        mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));

        // ...and revoke the legitimate successor too.
        mvc.perform(post("/api/v1/auth/refresh").cookie(second))
                .andExpect(status().isUnauthorized());

        Integer reuseEvents = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log a JOIN users u ON u.id = a.actor_user_id "
                        + "WHERE u.email = ? AND a.action = 'REFRESH_TOKEN_REUSE_DETECTED'", Integer.class, email);
        assertThat(reuseEvents).isEqualTo(1);
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesSessionAndIsIdempotent() throws Exception {
        AuthApi.Session session = api.register(uniqueEmail("logout"));

        mvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));
        mvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsRateLimitedPerEmail() throws Exception {
        String email = uniqueEmail("bruteforce");
        api.register(email);
        String badLogin = "{\"email\":\"%s\",\"password\":\"Wrong-Password-1\"}".formatted(email);

        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(badLogin))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(badLogin))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void changePasswordVerifiesCurrentAndSignsOutOtherSessions() throws Exception {
        String email = uniqueEmail("change");
        AuthApi.Session session = api.register(email);

        mvc.perform(put("/api/v1/me/password")
                        .header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Not-My-Password\",\"newPassword\":\"Brand-New-Pass-2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

        mvc.perform(put("/api/v1/me/password")
                        .header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"Brand-New-Pass-2\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized());
        api.login(email, "Brand-New-Pass-2");
    }
}

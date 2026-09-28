package com.commercex.auth;

import static com.commercex.auth.AuthApi.PASSWORD;
import static com.commercex.auth.AuthApi.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AdminCustomerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi api;
    AuthApi.Session admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new AuthApi(mvc, json);
        admin = api.registerWithRoles(uniqueEmail("admin"), jdbc, "ADMIN");
    }

    @Test
    void adminSearchesCustomersByEmail() throws Exception {
        String email = uniqueEmail("findme");
        api.register(email);

        mvc.perform(get("/api/v1/admin/customers")
                        .param("q", email.substring(0, 14).toUpperCase())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(email));
    }

    @Test
    void lockingAccountBlocksLoginAndEndsSessions() throws Exception {
        String email = uniqueEmail("lockme");
        AuthApi.Session customer = api.register(email);

        update(customer.userId(), "{\"status\":\"LOCKED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        mvc.perform(post("/api/v1/auth/refresh").cookie(customer.refreshCookie()))
                .andExpect(status().isUnauthorized());

        Integer audits = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'USER_UPDATED' AND resource_id = ? "
                        + "AND actor_user_id = ?", Integer.class, String.valueOf(customer.userId()), admin.userId());
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void grantedRoleAppearsInNextToken() throws Exception {
        String email = uniqueEmail("promote");
        AuthApi.Session customer = api.register(email);

        update(customer.userId(), "{\"roles\":[\"CUSTOMER\",\"CATALOG_MANAGER\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("CATALOG_MANAGER"))
                .andExpect(jsonPath("$.roles[1]").value("CUSTOMER"));

        AuthApi.Session promoted = api.login(email, PASSWORD);
        mvc.perform(get("/api/v1/admin/customers").header("Authorization", promoted.bearer()))
                .andExpect(status().isForbidden()); // customer admin is ADMIN-only
    }

    @Test
    void adminCannotLockThemselvesOrDropOwnAdminRole() throws Exception {
        update(admin.userId(), "{\"status\":\"DISABLED\"}").andExpect(status().isConflict());
        update(admin.userId(), "{\"roles\":[\"CUSTOMER\"]}").andExpect(status().isConflict());
    }

    @Test
    void invalidInputAndUnknownUser() throws Exception {
        update(admin.userId(), "{\"roles\":[]}").andExpect(status().isBadRequest());
        update(admin.userId(), "{\"status\":\"BANANA\"}").andExpect(status().isBadRequest());
        update(Long.MAX_VALUE, "{\"status\":\"LOCKED\"}").andExpect(status().isNotFound());
    }

    private ResultActions update(long userId, String body) throws Exception {
        return mvc.perform(patch("/api/v1/admin/customers/{id}", userId)
                .header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}

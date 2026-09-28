package com.commercex.auth;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AddressIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    AuthApi.Session owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = new AuthApi(mvc, json).register(uniqueEmail("address"));
    }

    @Test
    void firstAddressBecomesDefaultAndDefaultCanMove() throws Exception {
        long home = idOf(create(owner, "Home", false).andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.country").value("US")));
        long work = idOf(create(owner, "Work", true).andExpect(jsonPath("$.isDefault").value(true)));

        mvc.perform(get("/api/v1/me/addresses").header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == %d)].isDefault".formatted(home)).value(false))
                .andExpect(jsonPath("$[?(@.id == %d)].isDefault".formatted(work)).value(true));
    }

    @Test
    void deletingDefaultPromotesOldestRemaining() throws Exception {
        long first = idOf(create(owner, "One", false));
        long second = idOf(create(owner, "Two", false));
        long third = idOf(create(owner, "Three", true));

        mvc.perform(delete("/api/v1/me/addresses/{id}", third).header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me/addresses/{id}", first).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.isDefault").value(true));
        mvc.perform(get("/api/v1/me/addresses/{id}", second).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void updateChangesFields() throws Exception {
        long id = idOf(create(owner, "Home", false));

        mvc.perform(put("/api/v1/me/addresses/{id}", id)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Cottage", "gb", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Cottage"))
                .andExpect(jsonPath("$.country").value("GB"))
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void otherUsersAddressesAreInvisible() throws Exception {
        long id = idOf(create(owner, "Home", false));
        AuthApi.Session intruder = new AuthApi(mvc, json).register(uniqueEmail("intruder"));

        mvc.perform(get("/api/v1/me/addresses/{id}", id).header("Authorization", intruder.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/me/addresses/{id}", id)
                        .header("Authorization", intruder.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Hijacked", "US", true)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/addresses/{id}", id).header("Authorization", intruder.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidAddressIsRejected() throws Exception {
        mvc.perform(post("/api/v1/me/addresses")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"line1\":\"1 Main\",\"city\":\"X\",\"postalCode\":\"1\","
                                + "\"country\":\"USA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'country')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'fullName')]").exists());
    }

    @Test
    void addressBookIsCapped() throws Exception {
        for (int i = 0; i < 20; i++) {
            create(owner, "A" + i, false).andExpect(status().isCreated());
        }
        create(owner, "One too many", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private ResultActions create(AuthApi.Session session, String label, boolean isDefault) throws Exception {
        return mvc.perform(post("/api/v1/me/addresses")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(label, "us", isDefault)));
    }

    private static String body(String label, String country, boolean isDefault) {
        return """
                {"label":"%s","fullName":"Ada Lovelace","line1":"1 Main St","city":"Springfield",
                 "region":"IL","postalCode":"62701","country":"%s","phone":"+1 555 0100","isDefault":%s}
                """.formatted(label, country, isDefault);
    }

    private long idOf(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}

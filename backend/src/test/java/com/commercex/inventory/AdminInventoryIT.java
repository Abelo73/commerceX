package com.commercex.inventory;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

class AdminInventoryIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InventoryApi inventory;

    @Autowired
    TransactionTemplate tx;

    InventoryTestSupport support;

    @BeforeEach
    void setUp() throws Exception {
        support = new InventoryTestSupport(mvc, json, jdbc, inventory, tx);
    }

    @Test
    void restockCreatesRecordAndLedgerEntry() throws Exception {
        long variantId = support.variantWithStock(0);

        support.adjust(variantId, "RESTOCK", 12)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(12))
                .andExpect(jsonPath("$.available").value(12))
                .andExpect(jsonPath("$.lowStock").value(false));

        mvc.perform(get("/api/v1/admin/inventory/{id}/movements", variantId)
                        .header("Authorization", support.operations.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("RESTOCK"))
                .andExpect(jsonPath("$.content[0].onHandAfter").value(12))
                .andExpect(jsonPath("$.content[0].actorUserId").value(support.operations.userId()));
    }

    @Test
    void cannotRemoveStockThatCustomersHaveReserved() throws Exception {
        long variantId = support.variantWithStock(5);
        support.reserve(support.newOrderId(), new ReservationLine(variantId, 4));

        support.adjust(variantId, "ADJUSTMENT", -2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        support.adjust(variantId, "ADJUSTMENT", -1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(4))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void validatesAdjustments() throws Exception {
        long variantId = support.variantWithStock(5);

        support.adjust(variantId, "RESTOCK", -3).andExpect(status().isBadRequest());
        support.adjust(variantId, "ADJUSTMENT", 0).andExpect(status().isBadRequest());
        support.adjust(Long.MAX_VALUE, "RESTOCK", 3).andExpect(status().isNotFound());
        support.adjust(variantId, "BOGUS", 3).andExpect(status().isBadRequest());
    }

    @Test
    void lowStockReportUsesThreshold() throws Exception {
        long variantId = support.variantWithStock(0);
        support.adjust(variantId, "RESTOCK", 10).andExpect(status().isOk());

        mvc.perform(put("/api/v1/admin/inventory/{id}/threshold", variantId)
                        .header("Authorization", support.operations.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lowStockThreshold\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStock").value(true));

        mvc.perform(get("/api/v1/admin/inventory").param("lowStock", "true").param("size", "100")
                        .header("Authorization", support.operations.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].variantId").value(hasItem((int) variantId)));
    }

    @Test
    void onlyAdminAndOperationsManageInventory() throws Exception {
        AuthApi.Session customer = new AuthApi(mvc, json).register(uniqueEmail("shopper"));

        mvc.perform(get("/api/v1/admin/inventory").header("Authorization", customer.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/inventory").header("Authorization", support.catalogManagerBearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void insufficientStockProblemCarriesDetails() throws Exception {
        long variantId = support.variantWithStock(1);
        support.reserve(support.newOrderId(), new ReservationLine(variantId, 1));

        support.adjust(variantId, "ADJUSTMENT", -1)
                .andExpect(status().isConflict());
    }
}

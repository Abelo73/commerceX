package com.commercex.checkout;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.auth.AuthApi;
import com.commercex.testsupport.ShopFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** A signed-in customer with a saved address, ready to fill a cart and check out. */
public final class CheckoutSupport {

    public final AuthApi.Session customer;
    public final long addressId;
    private final MockMvc mvc;
    private final ObjectMapper json;

    public CheckoutSupport(MockMvc mvc, ObjectMapper json, ShopFixtures shop) throws Exception {
        this.mvc = mvc;
        this.json = json;
        this.customer = shop.auth.register(uniqueEmail("buyer"));
        String address = mvc.perform(post("/api/v1/me/addresses")
                        .header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ada Lovelace","line1":"1 Main St","city":"Springfield",
                                 "postalCode":"62701","country":"US"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        this.addressId = json.readTree(address).get("id").asLong();
    }

    public static String newKey() {
        return "checkout-" + UUID.randomUUID();
    }

    public void addToCart(long variantId, int quantity) throws Exception {
        mvc.perform(post("/api/v1/cart/items").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":%d,\"quantity\":%d}".formatted(variantId, quantity)))
                .andExpect(status().isOk());
    }

    public JsonNode quote(String shippingMethod) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/checkout/quote").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingMethod\":\"%s\"}".formatted(shippingMethod)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    public ResultActions placeOrder(String key, String shippingMethod, long expectedTotal) throws Exception {
        return placeOrder(key, addressId, shippingMethod, expectedTotal);
    }

    public ResultActions placeOrder(String key, long addressId, String shippingMethod, long expectedTotal)
            throws Exception {
        var request = post("/api/v1/orders").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":%d,\"shippingMethod\":\"%s\",\"expectedTotal\":%d}"
                        .formatted(addressId, shippingMethod, expectedTotal));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }

    /** Places an order for the current cart at the quoted STANDARD total. */
    public JsonNode checkout() throws Exception {
        long total = quote("STANDARD").at("/total/amount").asLong();
        return json.readTree(placeOrder(newKey(), "STANDARD", total)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }
}

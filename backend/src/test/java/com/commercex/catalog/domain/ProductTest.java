package com.commercex.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    private static Product product(List<String> options) {
        return Product.create("USD", new ProductDetails("Air Max", "air-max", null, null, null, 1L, options,
                List.of(), Set.of("running")), "Nike", NOW);
    }

    private static VariantSpec variant(String sku, String color, long price) {
        return new VariantSpec(sku, Map.of("Color", color), price, null, true);
    }

    @Test
    void syncComputesFromPriceAndKeepsVariantsBySku() {
        Product p = product(List.of("Color"));
        p.syncVariants(List.of(variant("A", "Black", 12_000), variant("B", "White", 9_900)), NOW);

        assertThat(p.getMinPriceMinor()).isEqualTo(9_900);
        assertThat(p.cheapestActiveVariant().getSku()).isEqualTo("B");

        ProductVariant a = p.getVariants().get(0);
        p.syncVariants(List.of(variant("A", "Black", 11_000)), NOW);

        assertThat(p.getVariants()).hasSize(2);
        assertThat(p.getVariants().get(0)).isSameAs(a);
        assertThat(a.getPriceMinor()).isEqualTo(11_000);
        assertThat(p.getVariants().get(1).isActive()).as("omitted variant is deactivated, not deleted").isFalse();
        assertThat(p.getMinPriceMinor()).isEqualTo(11_000);
    }

    @Test
    void rejectsInvalidVariantSets() {
        Product p = product(List.of("Color"));

        assertThatThrownBy(() -> p.syncVariants(List.of(variant("A", "Black", 100), variant("A", "White", 100)), NOW))
                .isInstanceOf(CatalogRuleViolation.class);
        assertThatThrownBy(() -> p.syncVariants(List.of(variant("A", "Black", 100), variant("B", "Black", 100)), NOW))
                .isInstanceOf(CatalogRuleViolation.class);
        assertThatThrownBy(() -> p.syncVariants(List.of(
                new VariantSpec("A", Map.of("Size", "42"), 100, null, true)), NOW))
                .isInstanceOf(CatalogRuleViolation.class);
        assertThatThrownBy(() -> p.syncVariants(List.of(
                new VariantSpec("A", Map.of("Color", "Black"), 100, 100L, true)), NOW))
                .isInstanceOf(CatalogRuleViolation.class);
    }

    @Test
    void deactivatedVariantStillOwnsItsCombination() {
        Product p = product(List.of("Color"));
        p.syncVariants(List.of(variant("A", "Black", 100)), NOW);
        p.syncVariants(List.of(variant("B", "White", 100)), NOW);

        assertThatThrownBy(() -> p.syncVariants(List.of(variant("C", "Black", 100)), NOW))
                .isInstanceOf(CatalogRuleViolation.class);
        p.syncVariants(List.of(variant("A", "Black", 100)), NOW); // reactivating the original SKU is fine
        assertThat(p.activeVariants()).extracting(ProductVariant::getSku).containsExactly("A");
    }

    @Test
    void publishRequiresActiveVariantAndActiveCategory() {
        Product p = product(List.of("Color"));

        assertThatThrownBy(() -> p.publish(NOW, true)).isInstanceOf(CatalogRuleViolation.class);

        p.syncVariants(List.of(variant("A", "Black", 100)), NOW);
        assertThatThrownBy(() -> p.publish(NOW, false)).isInstanceOf(CatalogRuleViolation.class);

        p.publish(NOW, true);
        assertThat(p.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(p.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void publishedProductCannotLoseAllActiveVariants() {
        Product p = product(List.of("Color"));
        p.syncVariants(List.of(variant("A", "Black", 100)), NOW);
        p.publish(NOW, true);

        assertThatThrownBy(() -> p.syncVariants(List.of(
                new VariantSpec("A", Map.of("Color", "Black"), 100, null, false)), NOW))
                .isInstanceOf(CatalogRuleViolation.class);
    }

    @Test
    void optionValuesAndLabelsFollowOptionOrder() {
        Product p = product(List.of("Color", "Size"));
        p.syncVariants(List.of(
                new VariantSpec("A", Map.of("Size", "42", "Color", "Black"), 100, null, true),
                new VariantSpec("B", Map.of("Color", "Black", "Size", "43"), 100, null, true),
                new VariantSpec("C", Map.of("Color", "Red", "Size", "42"), 100, null, false)), NOW);

        assertThat(p.activeOptionValues()).containsExactly(
                Map.entry("Color", List.of("Black")),
                Map.entry("Size", List.of("42", "43")));
        assertThat(p.getVariants().get(0).label(p.getOptionNames())).isEqualTo("Black / 42");
    }

    @Test
    void rejectsTooManyOrDuplicateOptions() {
        assertThatThrownBy(() -> product(List.of("A", "B", "C", "D"))).isInstanceOf(CatalogRuleViolation.class);
        assertThatThrownBy(() -> product(List.of("Color", "Color"))).isInstanceOf(CatalogRuleViolation.class);
    }

    @Test
    void searchKeywordsCombineBrandAndTags() {
        assertThat(Product.keywords("Nike", Set.of("running"))).isEqualTo("Nike running");
        assertThat(Product.keywords(null, Set.of())).isNull();
    }
}

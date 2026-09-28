package com.commercex.wishlist.application;

import com.commercex.cart.CartApi;
import com.commercex.catalog.CatalogApi;
import com.commercex.catalog.ProductCard;
import com.commercex.catalog.VariantSnapshot;
import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.error.ValidationException;
import com.commercex.common.web.MoneyResponse;
import com.commercex.wishlist.infrastructure.WishlistRepository;
import com.commercex.wishlist.infrastructure.WishlistRepository.Entry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {

    static final int MAX_ITEMS = 200;

    /** {@code available} is false once the product is unpublished; the entry is kept so it can return. */
    public record WishlistItem(long productId, String slug, String name, MoneyResponse price, String imageUrl,
            boolean inStock, boolean available, Instant addedAt) {
    }

    private final WishlistRepository wishlist;
    private final CatalogApi catalog;
    private final CartApi cart;

    WishlistService(WishlistRepository wishlist, CatalogApi catalog, CartApi cart) {
        this.wishlist = wishlist;
        this.catalog = catalog;
        this.cart = cart;
    }

    @Transactional(readOnly = true)
    public List<WishlistItem> list(long userId) {
        List<Entry> entries = wishlist.entries(userId);
        Map<Long, ProductCard> cards = catalog.products(entries.stream().map(Entry::productId).toList());
        return entries.stream()
                .filter(e -> cards.containsKey(e.productId()))
                .map(e -> {
                    ProductCard c = cards.get(e.productId());
                    return new WishlistItem(c.productId(), c.slug(), c.name(),
                            MoneyResponse.of(c.priceMinor(), c.currency()), c.imageUrl(),
                            c.published() && c.inStock(), c.published(), e.addedAt());
                })
                .toList();
    }

    @Transactional
    public List<WishlistItem> add(long userId, long productId) {
        ProductCard product = catalog.products(List.of(productId)).get(productId);
        if (product == null || !product.published()) {
            throw new NotFoundException("Product", productId);
        }
        if (!wishlist.contains(userId, productId) && wishlist.count(userId) >= MAX_ITEMS) {
            throw new ConflictException(ErrorCode.LIMIT_EXCEEDED, "A wishlist can hold at most " + MAX_ITEMS
                    + " products");
        }
        wishlist.add(userId, productId);
        return list(userId);
    }

    /** Idempotent. */
    @Transactional
    public List<WishlistItem> remove(long userId, long productId) {
        wishlist.remove(userId, productId);
        return list(userId);
    }

    /**
     * Adds the chosen variant to the cart and removes the product from the wishlist, atomically:
     * if the cart rejects it (stock, availability), the wishlist is left untouched.
     */
    @Transactional
    public List<WishlistItem> moveToCart(long userId, long productId, long variantId, int quantity) {
        if (!wishlist.contains(userId, productId)) {
            throw new NotFoundException("Wishlist item", productId);
        }
        VariantSnapshot variant = catalog.variants(List.of(variantId)).get(variantId);
        if (variant == null || variant.productId() != productId) {
            throw new ValidationException("variantId", "is not a variant of product " + productId);
        }
        if (!variant.purchasable()) {
            throw new BusinessException(ErrorCode.VARIANT_UNAVAILABLE, "This product is no longer available");
        }
        cart.addForUser(userId, variantId, quantity);
        wishlist.remove(userId, productId);
        return list(userId);
    }
}

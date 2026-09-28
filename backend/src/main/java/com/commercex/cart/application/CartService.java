package com.commercex.cart.application;

import com.commercex.cart.CartApi;
import com.commercex.cart.CartLine;
import com.commercex.cart.infrastructure.CartRepository;
import com.commercex.catalog.CatalogApi;
import com.commercex.catalog.VariantSnapshot;
import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.error.ValidationException;
import com.commercex.common.money.Money;
import com.commercex.common.ratelimit.RateLimiter;
import com.commercex.common.security.SecureTokens;
import com.commercex.common.web.MoneyResponse;
import com.commercex.inventory.InsufficientStockException;
import com.commercex.inventory.InventoryApi;
import com.commercex.inventory.StockLevel;
import com.commercex.promotion.CouponEvaluation;
import com.commercex.promotion.CouponInvalidException;
import com.commercex.promotion.DiscountLine;
import com.commercex.promotion.PromotionApi;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService implements CartApi {

    /** Result of a write: the cart plus a guest token when a new guest cart was created. */
    public record CartResult(CartView cart, String issuedGuestToken) {

        @Override
        public String toString() {
            return "CartResult[lines=" + cart.lines().size() + "]";
        }
    }

    private final CartRepository carts;
    private final CatalogApi catalog;
    private final InventoryApi inventory;
    private final PromotionApi promotions;
    private final RateLimiter rateLimiter;
    private final CartProperties properties;
    private final Clock clock;
    private final String currency;

    CartService(CartRepository carts, CatalogApi catalog, InventoryApi inventory, PromotionApi promotions,
            RateLimiter rateLimiter, CartProperties properties, Clock clock,
            @Value("${commercex.catalog.currency}") String currency) {
        this.carts = carts;
        this.catalog = catalog;
        this.inventory = inventory;
        this.promotions = promotions;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
        this.currency = currency;
    }

    // ---- storefront ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CartView view(CartOwner owner) {
        return existingCart(owner).map(cartId -> render(cartId, owner)).orElseGet(this::emptyView);
    }

    @Transactional
    public CartResult addItem(CartOwner owner, long variantId, int quantity, String clientIp) {
        requireQuantity(quantity);
        VariantSnapshot variant = purchasable(variantId);

        String issuedToken = null;
        Long cartId = existingCart(owner).orElse(null);
        if (cartId == null) {
            if (owner instanceof CartOwner.User user) {
                cartId = carts.getOrCreateUserCart(user.userId());
            } else {
                rateLimiter.consume("cart:create:ip:" + clientIp, properties.guestCreationsPerIp());
                issuedToken = SecureTokens.generate();
                cartId = carts.createGuestCart(SecureTokens.sha256(issuedToken), guestExpiry());
            }
        }

        int current = currentQuantity(cartId, variantId);
        if (current == 0 && carts.lineCount(cartId) >= properties.maxLines()) {
            throw new ConflictException(ErrorCode.LIMIT_EXCEEDED,
                    "A cart can hold at most " + properties.maxLines() + " different products");
        }
        requireStock(variant.variantId(), current + quantity);
        carts.addQuantity(cartId, variantId, quantity).orElseThrow(() -> new ValidationException("quantity",
                "at most " + CartRepository.MAX_QUANTITY_PER_LINE + " per product"));
        carts.touch(cartId, guestExpiry());
        return new CartResult(render(cartId, owner), issuedToken);
    }

    @Transactional
    public CartView updateQuantity(CartOwner owner, long variantId, int quantity) {
        requireQuantity(quantity);
        long cartId = existingCart(owner).orElseThrow(() -> new NotFoundException("Cart item", variantId));
        if (currentQuantity(cartId, variantId) == 0) {
            throw new NotFoundException("Cart item", variantId);
        }
        purchasable(variantId);
        requireStock(variantId, quantity);
        carts.setQuantity(cartId, variantId, quantity);
        carts.touch(cartId, guestExpiry());
        return render(cartId, owner);
    }

    @Transactional
    public CartView removeItem(CartOwner owner, long variantId) {
        long cartId = existingCart(owner).orElseThrow(() -> new NotFoundException("Cart item", variantId));
        if (!carts.remove(cartId, variantId)) {
            throw new NotFoundException("Cart item", variantId);
        }
        carts.touch(cartId, guestExpiry());
        return render(cartId, owner);
    }

    @Transactional
    public CartView clear(CartOwner owner) {
        existingCart(owner).ifPresent(cartId -> {
            carts.clear(cartId);
            carts.setCoupon(cartId, null);
        });
        return emptyView();
    }

    /** Applies a code only if it qualifies for the cart right now (422 with the reason otherwise). */
    @Transactional
    public CartView applyCoupon(CartOwner owner, String code) {
        long cartId = existingCart(owner).orElseThrow(() -> new BusinessException(ErrorCode.CHECKOUT_NOT_READY,
                "Add items to your cart before applying a code"));
        Priced priced = price(carts.lines(cartId));
        CouponEvaluation evaluation = promotions.evaluate(code, userId(owner), priced.discountLines(),
                priced.subtotal().amountMinor());
        if (!evaluation.valid()) {
            throw new CouponInvalidException(evaluation);
        }
        carts.setCoupon(cartId, evaluation.code());
        carts.touch(cartId, guestExpiry());
        return render(cartId, owner);
    }

    @Transactional
    public CartView removeCoupon(CartOwner owner) {
        Optional<Long> cartId = existingCart(owner);
        cartId.ifPresent(id -> carts.setCoupon(id, null));
        return cartId.map(id -> render(id, owner)).orElseGet(this::emptyView);
    }

    /**
     * Folds a guest cart into the signed-in user's cart (quantities summed, capped per line) and deletes
     * the guest cart. Unknown or expired tokens are ignored, so the call is safe to repeat.
     */
    @Transactional
    public CartView merge(long userId, String guestToken) {
        Optional<Long> guestCart = guestToken == null || guestToken.isBlank()
                ? Optional.empty()
                : carts.findGuestCart(SecureTokens.sha256(guestToken));
        long userCart = carts.getOrCreateUserCart(userId);
        guestCart.ifPresent(guest -> carts.mergeInto(guest, userCart));
        return render(userCart, new CartOwner.User(userId));
    }

    // ---- CartApi ---------------------------------------------------------------------------------

    @Override
    @Transactional
    public void addForUser(long userId, long variantId, int quantity) {
        addItem(new CartOwner.User(userId), variantId, quantity, "internal");
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartLine> userCartLines(long userId) {
        return carts.findUserCart(userId).map(carts::lines).orElse(List.of());
    }

    @Override
    @Transactional
    public void clearUserCart(long userId) {
        carts.findUserCart(userId).ifPresent(cartId -> {
            carts.clear(cartId);
            carts.setCoupon(cartId, null);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> userCartCoupon(long userId) {
        return carts.findUserCart(userId).flatMap(carts::coupon);
    }

    // ---- pricing ---------------------------------------------------------------------------------

    private record Priced(List<CartView.Line> lines, Money subtotal, int itemCount, boolean allOk,
            List<DiscountLine> discountLines) {
    }

    private Priced price(List<CartLine> lines) {
        List<Long> ids = lines.stream().map(CartLine::variantId).toList();
        Map<Long, VariantSnapshot> variants = catalog.variants(ids);
        Map<Long, StockLevel> stock = inventory.stockLevels(ids);

        Money subtotal = Money.of(0, currency);
        int itemCount = 0;
        boolean allOk = !lines.isEmpty();
        List<CartView.Line> rendered = new ArrayList<>();
        List<DiscountLine> discountLines = new ArrayList<>();
        for (CartLine line : lines) {
            VariantSnapshot v = variants.get(line.variantId());
            StockLevel level = stock.get(line.variantId());
            CartView.Issue issue = null;
            Integer available = null;
            if (v == null || !v.purchasable()) {
                issue = CartView.Issue.UNAVAILABLE;
            } else if (level.available() <= 0) {
                issue = CartView.Issue.OUT_OF_STOCK;
                available = 0;
            } else if (level.available() < line.quantity()) {
                issue = CartView.Issue.INSUFFICIENT_STOCK;
                available = level.available();
            }
            MoneyResponse unit = null;
            MoneyResponse lineTotal = null;
            if (v != null) {
                Money unitPrice = Money.of(v.priceMinor(), v.currency());
                Money total = unitPrice.times(line.quantity());
                unit = MoneyResponse.of(unitPrice);
                lineTotal = MoneyResponse.of(total);
                if (issue == null) {
                    subtotal = subtotal.plus(total);
                    itemCount += line.quantity();
                    discountLines.add(new DiscountLine(v.productId(), v.categoryId(), total.amountMinor()));
                }
            }
            allOk &= issue == null;
            rendered.add(new CartView.Line(
                    line.variantId(),
                    v == null ? null : v.productId(),
                    v == null ? null : v.productSlug(),
                    v == null ? null : v.productName(),
                    v == null ? null : v.label(),
                    v == null ? null : v.sku(),
                    v == null ? null : v.imageUrl(),
                    line.quantity(), unit,
                    v == null ? null : MoneyResponse.of(v.compareAtPriceMinor(), v.currency()),
                    lineTotal, issue, available));
        }
        return new Priced(rendered, subtotal, itemCount, allOk, discountLines);
    }

    private CartView render(long cartId, CartOwner owner) {
        Priced priced = price(carts.lines(cartId));
        CartView.AppliedCoupon coupon = null;
        long discount = 0;
        Optional<String> code = carts.coupon(cartId);
        if (code.isPresent()) {
            CouponEvaluation evaluation = promotions.evaluate(code.get(), userId(owner), priced.discountLines(),
                    priced.subtotal().amountMinor());
            discount = evaluation.valid() ? evaluation.discountMinor() : 0;
            coupon = new CartView.AppliedCoupon(evaluation.code(), evaluation.valid(), money(discount),
                    evaluation.reason(), evaluation.message());
        }
        return new CartView(priced.lines(), priced.itemCount(), MoneyResponse.of(priced.subtotal()), coupon,
                money(discount), money(priced.subtotal().amountMinor() - discount), priced.allOk());
    }

    private CartView emptyView() {
        return new CartView(List.of(), 0, money(0), null, money(0), money(0), false);
    }

    // ---- internals -------------------------------------------------------------------------------

    private Optional<Long> existingCart(CartOwner owner) {
        return switch (owner) {
            case CartOwner.User user -> carts.findUserCart(user.userId());
            case CartOwner.Guest guest -> carts.findGuestCart(SecureTokens.sha256(guest.token()));
            case CartOwner.Anonymous anonymous -> Optional.empty();
        };
    }

    private static Long userId(CartOwner owner) {
        return owner instanceof CartOwner.User user ? user.userId() : null;
    }

    private VariantSnapshot purchasable(long variantId) {
        VariantSnapshot variant = catalog.variants(List.of(variantId)).get(variantId);
        if (variant == null) {
            throw new NotFoundException("Variant", variantId);
        }
        if (!variant.purchasable()) {
            throw new BusinessException(ErrorCode.VARIANT_UNAVAILABLE, "This product is no longer available");
        }
        return variant;
    }

    private void requireStock(long variantId, int wanted) {
        StockLevel level = inventory.stockLevels(List.of(variantId)).get(variantId);
        if (!level.canSatisfy(wanted)) {
            throw new InsufficientStockException(variantId, wanted, level.available());
        }
    }

    private int currentQuantity(long cartId, long variantId) {
        return carts.lines(cartId).stream().filter(l -> l.variantId() == variantId).mapToInt(CartLine::quantity)
                .findFirst().orElse(0);
    }

    private static void requireQuantity(int quantity) {
        if (quantity < 1 || quantity > CartRepository.MAX_QUANTITY_PER_LINE) {
            throw new ValidationException("quantity", "must be between 1 and " + CartRepository.MAX_QUANTITY_PER_LINE);
        }
    }

    private MoneyResponse money(long amountMinor) {
        return new MoneyResponse(amountMinor, currency);
    }

    private Instant guestExpiry() {
        return clock.instant().plus(properties.guestTtl());
    }
}

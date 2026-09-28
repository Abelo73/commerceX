package com.commercex.checkout.application;

import com.commercex.auth.AccountApi;
import com.commercex.auth.CustomerInfo;
import com.commercex.auth.ShippingAddress;
import com.commercex.cart.CartApi;
import com.commercex.cart.CartLine;
import com.commercex.catalog.CatalogApi;
import com.commercex.catalog.VariantSnapshot;
import com.commercex.checkout.application.CheckoutProperties.ShippingMethod;
import com.commercex.checkout.application.CheckoutViews.Quote;
import com.commercex.checkout.application.CheckoutViews.QuoteLine;
import com.commercex.checkout.application.CheckoutViews.ShippingOption;
import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.ValidationException;
import com.commercex.common.idempotency.Idempotency;
import com.commercex.common.web.MoneyResponse;
import com.commercex.order.OrderAddress;
import com.commercex.order.OrderApi;
import com.commercex.order.OrderDetails;
import com.commercex.order.PlaceOrderCommand;
import com.commercex.promotion.CouponEvaluation;
import com.commercex.promotion.CouponInvalidException;
import com.commercex.promotion.DiscountLine;
import com.commercex.promotion.PromotionApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the signed-in customer's cart into an order. Prices, shipping and totals are always recomputed on
 * the server; the client's cart contents and prices are never trusted.
 */
@Service
@EnableConfigurationProperties(CheckoutProperties.class)
public class CheckoutService {

    public record PlaceOrderRequest(long addressId, String shippingMethod, long expectedTotal) {
    }

    private final CartApi cart;
    private final CatalogApi catalog;
    private final AccountApi accounts;
    private final OrderApi orders;
    private final PromotionApi promotions;
    private final Idempotency idempotency;
    private final CheckoutProperties properties;
    private final String currency;

    CheckoutService(CartApi cart, CatalogApi catalog, AccountApi accounts, OrderApi orders, PromotionApi promotions,
            Idempotency idempotency, CheckoutProperties properties,
            @Value("${commercex.catalog.currency}") String currency) {
        this.cart = cart;
        this.catalog = catalog;
        this.accounts = accounts;
        this.orders = orders;
        this.promotions = promotions;
        this.idempotency = idempotency;
        this.properties = properties;
        this.currency = currency;
    }

    public List<ShippingOption> shippingOptions(long subtotalMinor) {
        return properties.shippingMethods().stream()
                .map(m -> new ShippingOption(m.code(), m.name(), money(m.priceFor(subtotalMinor)), m.minDays(),
                        m.maxDays()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Quote quote(long userId, String shippingMethodCode) {
        Priced priced = price(userId);
        ShippingMethod method = shippingMethodCode == null
                ? properties.shippingMethods().get(0)
                : shippingMethod(shippingMethodCode);
        long shipping = method.priceFor(priced.subtotal());
        long tax = 0; // Prices are tax-inclusive until a tax provider is integrated.
        List<String> problems = new ArrayList<>(priced.problems());
        CouponEvaluation coupon = cart.userCartCoupon(userId)
                .map(code -> promotions.evaluate(code, userId, priced.discountLines(), priced.subtotal()))
                .orElse(null);
        long discount = coupon != null && coupon.valid() ? coupon.discountMinor() : 0;
        if (coupon != null && !coupon.valid()) {
            problems.add("Coupon " + coupon.code() + ": " + coupon.message());
        }
        return new Quote(priced.quoteLines(), money(priced.subtotal()), money(discount), money(shipping), money(tax),
                money(priced.subtotal() - discount + shipping + tax), coupon == null ? null : coupon.code(),
                method.code(), shippingOptions(priced.subtotal()), problems.isEmpty(), problems);
    }

    /** Idempotent per {@code Idempotency-Key}: a retried request returns the originally created order. */
    public Idempotency.Result<OrderDetails> placeOrder(long userId, PlaceOrderRequest request, String idempotencyKey) {
        return idempotency.execute(userId, "place-order", idempotencyKey, request, 201, () -> place(userId, request));
    }

    private OrderDetails place(long userId, PlaceOrderRequest request) {
        CustomerInfo customer = accounts.customer(userId).filter(CustomerInfo::active)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_DISABLED, "This account is not active"));
        ShippingAddress address = accounts.address(userId, request.addressId())
                .orElseThrow(() -> new ValidationException("addressId", "is not one of your saved addresses"));
        ShippingMethod method = shippingMethod(request.shippingMethod());

        Priced priced = price(userId);
        // Problems first: a cart whose only items became unavailable is not "empty" to the customer.
        if (!priced.problems().isEmpty()) {
            throw new BusinessException(ErrorCode.CHECKOUT_NOT_READY,
                    "Some items in your cart are no longer available; review your cart",
                    Map.of("problems", priced.problems()));
        }
        if (priced.lines().isEmpty()) {
            throw new BusinessException(ErrorCode.CHECKOUT_NOT_READY, "Your cart is empty");
        }
        long shipping = method.priceFor(priced.subtotal());
        long tax = 0;
        String couponCode = cart.userCartCoupon(userId).orElse(null);
        long discount = 0;
        if (couponCode != null) {
            CouponEvaluation coupon = promotions.evaluate(couponCode, userId, priced.discountLines(),
                    priced.subtotal());
            if (!coupon.valid()) {
                throw new CouponInvalidException(coupon);
            }
            couponCode = coupon.code();
            discount = coupon.discountMinor();
        }
        long total = priced.subtotal() - discount + shipping + tax;
        if (total != request.expectedTotal()) {
            throw new BusinessException(ErrorCode.PRICE_CHANGED,
                    "The order total changed; review the updated total before paying",
                    Map.of("expectedTotal", request.expectedTotal(), "actualTotal", total));
        }

        OrderDetails order = orders.place(new PlaceOrderCommand(userId, customer.email(), currency, priced.lines(),
                priced.subtotal(), discount, shipping, tax, total, method.code(),
                new OrderAddress(address.fullName(), address.line1(), address.line2(), address.city(),
                        address.region(), address.postalCode(), address.country(), address.phone()),
                couponCode));
        if (couponCode != null) {
            // Same transaction: if the last use was just taken by someone else, the whole order rolls back.
            long redeemed = promotions.redeem(couponCode, userId, order.id(), priced.discountLines(),
                    priced.subtotal());
            if (redeemed != discount) {
                throw new BusinessException(ErrorCode.PRICE_CHANGED, "The discount changed; review your order");
            }
        }
        cart.clearUserCart(userId);
        return order;
    }

    private record Priced(List<PlaceOrderCommand.Line> lines, List<QuoteLine> quoteLines, long subtotal,
            List<String> problems, List<DiscountLine> discountLines) {
    }

    private Priced price(long userId) {
        List<CartLine> cartLines = cart.userCartLines(userId);
        Map<Long, VariantSnapshot> variants = catalog.variants(cartLines.stream().map(CartLine::variantId).toList());
        List<PlaceOrderCommand.Line> lines = new ArrayList<>();
        List<QuoteLine> quoteLines = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<DiscountLine> discountLines = new ArrayList<>();
        long subtotal = 0;
        for (CartLine line : cartLines) {
            VariantSnapshot v = variants.get(line.variantId());
            if (v == null || !v.purchasable()) {
                problems.add("Variant " + line.variantId() + " is no longer available");
                quoteLines.add(new QuoteLine(line.variantId(), v == null ? null : v.productName(),
                        v == null ? null : v.label(), line.quantity(), null, null, "UNAVAILABLE"));
                continue;
            }
            long lineTotal = Math.multiplyExact(v.priceMinor(), (long) line.quantity());
            subtotal = Math.addExact(subtotal, lineTotal);
            lines.add(new PlaceOrderCommand.Line(v.variantId(), v.productId(), v.productName(), v.label(), v.sku(),
                    v.imageUrl(), v.priceMinor(), line.quantity()));
            discountLines.add(new DiscountLine(v.productId(), v.categoryId(), lineTotal));
            quoteLines.add(new QuoteLine(v.variantId(), v.productName(), v.label(), line.quantity(),
                    money(v.priceMinor()), money(lineTotal), null));
        }
        return new Priced(lines, quoteLines, subtotal, problems, discountLines);
    }

    private ShippingMethod shippingMethod(String code) {
        return properties.shippingMethods().stream().filter(m -> m.code().equalsIgnoreCase(code)).findFirst()
                .orElseThrow(() -> new ValidationException("shippingMethod", "unknown shipping method " + code));
    }

    private MoneyResponse money(long amountMinor) {
        return new MoneyResponse(amountMinor, currency);
    }
}

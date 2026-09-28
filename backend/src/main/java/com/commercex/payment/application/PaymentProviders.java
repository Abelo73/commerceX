package com.commercex.payment.application;

import com.commercex.payment.provider.PaymentProvider;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Registry of provider adapters. New payments use the configured one; existing payments keep theirs. */
@Component
@EnableConfigurationProperties(PaymentProperties.class)
class PaymentProviders {

    private final Map<String, PaymentProvider> byName;
    private final String active;

    PaymentProviders(List<PaymentProvider> providers, PaymentProperties properties) {
        this.byName = providers.stream().collect(Collectors.toMap(PaymentProvider::name, Function.identity()));
        this.active = properties.provider();
        if (!byName.containsKey(active)) {
            throw new IllegalStateException("Unknown payment provider '" + active + "'; available: " + byName.keySet());
        }
    }

    PaymentProvider active() {
        return byName.get(active);
    }

    PaymentProvider byName(String name) {
        PaymentProvider provider = byName.get(name);
        if (provider == null) {
            throw new IllegalStateException("Payment provider '" + name + "' is not available");
        }
        return provider;
    }
}

package com.commercex.payment.application;

import com.commercex.payment.PaymentApi;
import com.commercex.payment.domain.PaymentStatus;
import com.commercex.payment.infrastructure.PaymentRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentStatsService implements PaymentApi {

    private final PaymentRepository payments;

    PaymentStatsService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    @Transactional(readOnly = true)
    public long failedPaymentsSince(Instant from) {
        return payments.countByStatusAndCreatedAtGreaterThanEqual(PaymentStatus.FAILED, from);
    }
}

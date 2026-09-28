package com.commercex.promotion;

import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import java.util.Map;

public class CouponInvalidException extends BusinessException {

    public CouponInvalidException(CouponEvaluation evaluation) {
        super(ErrorCode.COUPON_INVALID, evaluation.message(),
                Map.of("code", evaluation.code(), "reason", evaluation.reason()));
    }
}

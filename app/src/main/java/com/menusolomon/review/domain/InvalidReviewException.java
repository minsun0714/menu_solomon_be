package com.menusolomon.review.domain;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;

public class InvalidReviewException extends BusinessException {
    public InvalidReviewException() {
        super(ErrorCode.VALIDATION_ERROR);
    }
}

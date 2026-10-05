package com.menusolomon.vote.domain;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;

public class VoteClosingTimeException extends BusinessException {
    public VoteClosingTimeException() {
        super(ErrorCode.VALIDATION_ERROR);
    }
}

package com.menusolomon.vote.domain;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public record HistoryPeriod(Instant from, Instant until) {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public static HistoryPeriod of(String view, LocalDate date, YearMonth month) {
        LocalDate start, end;
        if ("WEEK".equals(view) && date != null && month == null) {
            start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); end = start.plusWeeks(1);
        } else if ("MONTH".equals(view) && month != null && date == null) {
            start = month.atDay(1); end = month.plusMonths(1).atDay(1);
        } else throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return new HistoryPeriod(start.atStartOfDay(SEOUL).toInstant(), end.atStartOfDay(SEOUL).toInstant());
    }
}

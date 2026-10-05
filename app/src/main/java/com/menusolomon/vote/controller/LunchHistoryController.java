package com.menusolomon.vote.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.vote.dto.LunchHistoryResponse;
import com.menusolomon.vote.service.VoteService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams/{teamId}/lunch-history")
public class LunchHistoryController {
    private final VoteService service;
    public LunchHistoryController(VoteService service) { this.service=service; }
    @GetMapping
    public ApiResponse<List<LunchHistoryResponse>> history(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @RequestParam String view,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ApiResponse.of(service.getHistory(teamId, token, view, date, month));
    }
}

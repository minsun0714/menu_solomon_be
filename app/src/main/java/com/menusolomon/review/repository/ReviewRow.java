package com.menusolomon.review.repository;

import java.time.Instant;

public record ReviewRow(Long id, Long teamMemberId, int rating, String content,
        String authorNickname, Instant createdAt, Instant updatedAt) {}

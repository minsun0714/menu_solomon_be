package com.menusolomon.fixture;

import com.menusolomon.user.domain.User;
import java.time.Instant;

public final class UserFixture {
    private UserFixture() {
    }

    public static User user(String kakaoId) {
        return new User(kakaoId, "nickname", null, Instant.parse("2025-01-01T00:00:00Z"));
    }
}

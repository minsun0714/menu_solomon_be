package com.menusolomon.user.fixture;

import com.menusolomon.user.domain.User;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserFixture {

    public static User user(Long id, String tokenHash, String nickname) {
        User user = User.create(tokenHash, nickname, Instant.parse("2026-10-04T08:30:00Z"));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserFixture() {
    }
}

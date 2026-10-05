package com.menusolomon.user.domain;

import static org.assertj.core.api.Assertions.*;
import com.menusolomon.common.exception.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {
    private static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");

    @Test
    void anonymousNickname_isShortAndDoesNotExposeSessionHash() {
        var user = User.createAnonymous("session-hash", NOW);
        assertThat(user.getNickname()).matches("익명[0-9a-f]{6}");
    }

    @Test
    void changeNickname_trimsInputAndPreservesCreationTime() {
        var user = User.createAnonymous("hash", NOW);
        user.changeNickname("  수달4821  ", NOW.plusSeconds(1));
        assertThat(user.getNickname()).isEqualTo("수달4821");
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "a", "1234567890123", "ab\nc", "ab\tc"})
    void invalidNickname_doesNotChangeUser(String nickname) {
        var user = User.create("hash", "원래이름", NOW);
        assertThatThrownBy(() -> user.changeNickname(nickname, NOW.plusSeconds(1)))
                .isInstanceOf(BusinessException.class);
        assertThat(user.getNickname()).isEqualTo("원래이름");
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
    }
}

package com.menusolomon.team.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TeamTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createTeam_withNameAndDescription_succeeds() {
        Team team = new Team("솔로몬 개발팀", "점심 메뉴를 함께 정해요", "token", NOW);

        assertThat(team.getName()).isEqualTo("솔로몬 개발팀");
        assertThat(team.getDescription()).isEqualTo("점심 메뉴를 함께 정해요");
        assertThat(team.getInviteToken()).isEqualTo("token");
    }

    @Test
    void createTeam_withoutDescription_usesEmptyString() {
        Team team = new Team("팀", null, "token", NOW);

        assertThat(team.getDescription()).isEmpty();
    }

    @Test
    void createTeam_withBlankName_fails() {
        assertThatThrownBy(() -> new Team(" ", "d", "token", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeInfo_changesNameAndDescription() {
        Team team = new Team("팀", "설명", "token", NOW);
        Instant later = NOW.plusSeconds(60);

        team.changeInfo("새 팀", "새 설명", later);

        assertThat(team.getName()).isEqualTo("새 팀");
        assertThat(team.getDescription()).isEqualTo("새 설명");
        assertThat(team.getUpdatedAt()).isEqualTo(later);
    }
}

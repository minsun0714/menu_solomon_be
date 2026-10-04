package com.menusolomon.team.fixture;

import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

public final class TeamFixture {

    public static final Instant NOW = Instant.parse("2026-10-04T08:30:00Z");

    public static Team team() {
        Team team = Team.create("솔로몬 개발팀", "점심 메뉴를 함께 정해요", "invite-token", NOW);
        ReflectionTestUtils.setField(team, "id", 1L);
        return team;
    }

    public static TeamMember admin() {
        TeamMember member = TeamMember.newAdmin(1L, 10L, NOW);
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    public static TeamMember member() {
        TeamMember member = TeamMember.newMember(1L, 20L, NOW);
        ReflectionTestUtils.setField(member, "id", 2L);
        return member;
    }

    private TeamFixture() {
    }
}

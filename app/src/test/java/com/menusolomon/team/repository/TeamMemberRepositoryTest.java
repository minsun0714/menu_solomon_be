package com.menusolomon.team.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menusolomon.fixture.TeamMemberFixture;
import com.menusolomon.team.domain.TeamMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class TeamMemberRepositoryTest {
    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Test
    void duplicateTeamAndUser_violatesUniqueConstraint() {
        teamMemberRepository.saveAndFlush(TeamMemberFixture.activeMember(1L, 2L));

        assertThatThrownBy(() -> teamMemberRepository.saveAndFlush(
                TeamMemberFixture.activeMember(1L, 2L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByTeamIdAndUserId_findsInactiveMembership() {
        TeamMember inactive = teamMemberRepository.saveAndFlush(TeamMemberFixture.inactiveMember(1L, 2L));

        assertThat(teamMemberRepository.findByTeamIdAndUserId(1L, 2L)).contains(inactive);
    }

    @Test
    void findActiveMembership_excludesInactiveMembership() {
        teamMemberRepository.saveAndFlush(TeamMemberFixture.inactiveMember(1L, 2L));

        assertThat(teamMemberRepository.findByTeamIdAndUserIdAndLeftAtIsNull(1L, 2L)).isEmpty();
    }

    @Test
    void findAllActiveMembers_excludesInactiveMembers() {
        teamMemberRepository.save(TeamMemberFixture.activeMember(1L, 2L));
        teamMemberRepository.save(TeamMemberFixture.inactiveMember(1L, 3L));
        teamMemberRepository.flush();

        assertThat(teamMemberRepository.findAllByTeamIdAndLeftAtIsNull(1L))
                .extracting(TeamMember::getUserId)
                .containsExactly(2L);
    }

    @Test
    void countActiveMembers_countsOnlyActiveMembers() {
        teamMemberRepository.save(TeamMemberFixture.activeMember(1L, 2L));
        teamMemberRepository.save(TeamMemberFixture.inactiveMember(1L, 3L));
        teamMemberRepository.flush();

        assertThat(teamMemberRepository.countByTeamIdAndLeftAtIsNull(1L)).isEqualTo(1);
    }
}

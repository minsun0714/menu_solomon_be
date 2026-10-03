package com.menusolomon.team.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.menusolomon.fixture.TeamFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class TeamRepositoryTest {
    @Autowired
    private TeamRepository teamRepository;

    @Test
    void findByInviteToken_returnsMatchingTeam() {
        var saved = teamRepository.saveAndFlush(TeamFixture.team("invite-token"));

        assertThat(teamRepository.findByInviteToken("invite-token")).contains(saved);
    }
}

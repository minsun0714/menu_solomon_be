package com.menusolomon.team.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menusolomon.fixture.UserFixture;
import com.menusolomon.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Test
    void duplicateKakaoId_violatesUniqueConstraint() {
        userRepository.saveAndFlush(UserFixture.user("kakao-id"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(UserFixture.user("kakao-id")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

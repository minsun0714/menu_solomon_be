package com.menusolomon.team.domain;

import static com.menusolomon.team.fixture.TeamFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.menusolomon.common.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TeamTest {
    @Test
    void changeInfo_updatesTeam_andUpdatedAt() {
        var team = team();
        team.changeInfo("플랫폼 개발팀", "점심 맛집 공유", NOW.plusSeconds(60));
        assertThat(team.getName()).isEqualTo("플랫폼 개발팀");
        assertThat(team.getDescription()).isEqualTo("점심 맛집 공유");
        assertThat(team.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(team.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void changeInfo_missingDescription_usesEmptyString() {
        var team = team();
        team.changeInfo("새 팀", null, NOW);
        assertThat(team.getDescription()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void changeInfo_invalidName_doesNotChangeTeam(String name) {
        var team = team();
        assertThatThrownBy(() -> team.changeInfo(name, "새 소개", NOW.plusSeconds(60)))
                .isInstanceOf(BusinessException.class);
        assertThat(team.getName()).isEqualTo("솔로몬 개발팀");
        assertThat(team.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void changeInviteToken_changesToken_andUpdatedAt() {
        var team = team();
        team.changeInviteToken("new-token", NOW.plusSeconds(60));
        assertThat(team.getInviteToken()).isEqualTo("new-token");
        assertThat(team.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void changeOfficeLocation_setsAllOfficeFields_andUpdatedAt() {
        var team = team();
        team.changeOfficeLocation("123", "엔셀", "서울", new BigDecimal("37.123"), new BigDecimal("127.123"), NOW.plusSeconds(60));
        assertThat(team.getOfficeKakaoPlaceId()).isEqualTo("123");
        assertThat(team.getOfficeName()).isEqualTo("엔셀");
        assertThat(team.getOfficeAddress()).isEqualTo("서울");
        assertThat(team.getOfficeLatitude()).isEqualByComparingTo("37.123");
        assertThat(team.getOfficeLongitude()).isEqualByComparingTo("127.123");
        assertThat(team.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        team.changeOfficeLocation("456", "새 사무실", "부산", new BigDecimal("35.1"), new BigDecimal("129.1"), NOW.plusSeconds(120));
        assertThat(team.getOfficeKakaoPlaceId()).isEqualTo("456");
        assertThat(team.getOfficeName()).isEqualTo("새 사무실");
        assertThat(team.getOfficeAddress()).isEqualTo("부산");
        assertThat(team.getOfficeLatitude()).isEqualByComparingTo("35.1");
        assertThat(team.getOfficeLongitude()).isEqualByComparingTo("129.1");
    }

    @Test
    void changeOfficeLocation_incompleteLocation_doesNotPartiallyUpdateTeam() {
        var team = team();
        team.changeOfficeLocation("123", "엔셀", "서울", BigDecimal.ONE, BigDecimal.TEN, NOW);
        assertThatThrownBy(() -> team.changeOfficeLocation("456", "변경", "부산", BigDecimal.TEN, null, NOW.plusSeconds(60)))
                .isInstanceOf(BusinessException.class);
        assertThat(team.getOfficeKakaoPlaceId()).isEqualTo("123");
        assertThat(team.getOfficeName()).isEqualTo("엔셀");
        assertThat(team.getOfficeAddress()).isEqualTo("서울");
        assertThat(team.getOfficeLatitude()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(team.getUpdatedAt()).isEqualTo(NOW);
    }
}

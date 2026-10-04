package com.menusolomon.team.dto;

import com.menusolomon.team.domain.Team;
import java.math.BigDecimal;

public record OfficeLocationResponse(String kakaoPlaceId, String name, String address,
        BigDecimal latitude, BigDecimal longitude) {
    public static OfficeLocationResponse from(Team team) {
        return team.getOfficeKakaoPlaceId() == null ? null
                : new OfficeLocationResponse(team.getOfficeKakaoPlaceId(), team.getOfficeName(),
                        team.getOfficeAddress(), team.getOfficeLatitude(), team.getOfficeLongitude());
    }
}

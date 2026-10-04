# 팀 관리와 사무실 위치

기존 익명 세션의 ACTIVE 팀원(leftAt == null)만 호출할 수 있다. 세션은 자동 생성하지 않는다. 비팀원/탈퇴한 팀원은 403 NOT_TEAM_MEMBER, 관리자 전용 API를 호출한 일반 팀원은 403 ADMIN_REQUIRED다. 오류 형식은 기존 ProblemDetail이다.

| Method | Endpoint | 권한 | 성공 응답 |
| --- | --- | --- | --- |
| PATCH | /api/teams/{teamId} | ADMIN | 200, 수정된 팀 정보 |
| GET | /api/teams/{teamId}/invitation | ADMIN | 200, 완성된 inviteUrl |
| POST | /api/teams/{teamId}/invitation/regenerate | ADMIN | 200, 새 inviteUrl |
| POST | /api/teams/{teamId}/admin-transfer | ADMIN | 200, adminTeamMemberId |
| DELETE | /api/teams/{teamId}/members/me | ACTIVE 팀원 | 204 |
| GET | /api/teams/{teamId}/office | ACTIVE 팀원 | 200, 위치 또는 data: null |
| PUT | /api/teams/{teamId}/office | ACTIVE 팀원 | 200, 저장된 위치 |

팀 수정은 name이 필수이며 blank를 허용하지 않는다. description은 팀 생성과 동일하게 누락/null이면 빈 문자열이다. 이번 PATCH는 부분 수정이 아닌 위 정책을 따른다.

초대 토큰은 기존과 동일하게 SecureRandom 32 bytes를 URL-safe Base64로 인코딩한다. 재발급은 Team의 토큰을 교체하므로 기존 링크는 즉시 INVITATION_NOT_FOUND가 된다.

관리자 위임 요청은 숫자 targetTeamMemberId를 받는다. 대상은 같은 팀의 ACTIVE MEMBER여야 하며 자신에게 위임할 수 없다. 유효하지 않은 대상은 MEMBER_NOT_FOUND다. 대상 검증을 마친 뒤 기존 관리자를 MEMBER, 대상을 ADMIN으로 한 트랜잭션에서 변경한다.

일반 멤버의 탈퇴는 leftAt을 설정한다. 관리자에게 다른 ACTIVE 팀원이 있으면 409 ADMIN_TRANSFER_REQUIRED다. 마지막 관리자 탈퇴는 Review → TeamRestaurant → TeamMember → Team 순서로 일괄 삭제한다. 탈퇴한 팀원 기록도 제거하며 공유 Restaurant와 User는 유지한다.

사무실 위치는 별도 테이블 없이 Team에 다섯 필드(kakaoPlaceId, name, address, latitude, longitude)를 함께 저장한다. 프론트에서 선택한 장소의 상세 정보를 전달받으므로 카카오 API를 다시 호출하지 않는다. 모든 ACTIVE 팀원이 설정과 변경을 할 수 있으며 항상 200을 반환한다.

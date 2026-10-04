# 메뉴솔로몬 API 명세 — 현재 구현 기준

현재 Controller, DTO, Service에 구현된 계약을 기준으로 작성한다. 예전 요구사항에만 있는 API는 포함하지 않는다.

## 1. 공통

- Base URL: `/api` (로컬 Compose: `http://localhost:8080/api`)
- 요청: `Content-Type: application/json`, 성공 응답: `application/json`
- 성공 응답은 `{ "data": ... }`. `204`는 응답 본문 없음.
- 오류: `application/problem+json`, Spring ProblemDetail 사용.
- 날짜: ISO 8601 UTC 문자열. 예: `2026-10-04T04:00:00Z`.
- 프론트 요청은 `credentials: "include"` 사용.
- CORS는 `CORS_ALLOWED_ORIGINS`의 정확한 Origin만 허용하며 credentials 지원.
- ACTIVE 팀원: `leftAt == null`. 관리자도 ACTIVE여야 한다.
- 팀원 API는 세션 누락·무효, 비팀원·탈퇴 팀원에 `403 NOT_TEAM_MEMBER` 반환. 사용자/쿠키 자동 생성 없음.
- 권한 및 리소스 검증 순서에 따라 오류가 달라질 수 있다. 없는 리소스에 항상 404가 우선하는 것은 아니다.

### ID

응답 ID는 문자열: `user_1`, `team_1`, `member_1`, `restaurant_1`, `teamRestaurant_1`, `review_1`, `vote_1`, `candidate_1`.

경로의 `teamId`, `teamRestaurantId`, `voteId`는 숫자 또는 해당 접두사 문자열 모두 허용한다.
JSON 요청의 `targetTeamMemberId`, `teamRestaurantId`, `voteCandidateId`는 **양의 정수**로 보낸다.
`kakaoPlaceId`는 문자열이다.

### 오류 예시

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "팀원만 사용할 수 있습니다.",
  "instance": "/api/teams/team_1",
  "code": "NOT_TEAM_MEMBER"
}
```

본문 필드 검증 오류는 `fieldErrors` 객체가 추가될 수 있다. 쿼리/형식 오류에는 항상 포함되지는 않는다.

## 2. 익명 세션

### GET `/session/me`

권한: 공개. 성공: `200`.

- 쿠키가 없거나 무효하면 서버가 익명 사용자와 난수 토큰을 생성한다.
- 유효한 쿠키는 같은 사용자를 반환하며 쿠키를 재발급하지 않는다.
- 쿠키는 API 호스트 전용(host-only)이며 Domain 속성이 없다.
- 프로필 이미지, 원문 토큰, 토큰 해시는 응답 JSON에 포함하지 않는다.

```http
Set-Cookie: menu_solomon_session={token}; Path=/; Max-Age=31536000; Expires=...; Secure; HttpOnly; SameSite=Lax
```

```json
{ "data": { "id": "user_1", "nickname": "익명 사용자 1234" } }
```

## 3. 팀과 초대

### POST `/teams`

권한: 익명 사용자, 필요 시 세션 자동 발급. 성공: `201`.

```json
{ "name": "솔로몬 개발팀", "description": "점심 메뉴를 함께 정해요" }
```

`name` 필수·blank 불가. `description` 생략/null은 빈 문자열. 생성자는 ADMIN, 초대 링크도 생성한다.

```json
{ "data": { "id": "team_1", "name": "솔로몬 개발팀", "description": "점심 메뉴를 함께 정해요", "myRole": "ADMIN", "inviteUrl": "https://example.com/invite/{token}" } }
```

### GET `/teams/{teamId}`

권한: ACTIVE 팀원. 성공: `200`.

```json
{ "data": { "id": "team_1", "name": "솔로몬 개발팀", "description": "점심 메뉴를 함께 정해요", "memberCount": 6, "myRole": "ADMIN" } }
```

`memberCount`는 ACTIVE 팀원 수, `myRole`은 ADMIN 또는 MEMBER. 오류: `NOT_TEAM_MEMBER`, `TEAM_NOT_FOUND`.

### PATCH `/teams/{teamId}`

권한: ACTIVE ADMIN. 성공: `200`.

```json
{ "name": "플랫폼 개발팀", "description": "점심 맛집 공유" }
```

현재 구현은 `name` 필수·blank 불가. `description` 생략/null은 빈 문자열로 변경한다.
**미전송 필드 유지 방식의 부분 수정은 지원하지 않는다.**

```json
{ "data": { "id": "team_1", "name": "플랫폼 개발팀", "description": "점심 맛집 공유" } }
```

오류: `VALIDATION_ERROR`, `NOT_TEAM_MEMBER`, `ADMIN_REQUIRED`, `TEAM_NOT_FOUND`.

### GET `/invitations/{inviteToken}`

권한: 공개. 성공: `200`. 세션 생성·쿠키 발급 없음.

```json
{
  "data": {
    "teamId": "team_1", "name": "솔로몬 개발팀", "description": "점심 메뉴를 함께 정해요",
    "memberCount": 1, "isAlreadyMember": false,
    "members": [{ "id": "member_1", "role": "ADMIN", "joinedAt": "2026-10-04T04:00:00Z", "user": { "id": "user_1", "nickname": "익명 사용자 1234" } }]
  }
}
```

팀원 목록/개수는 ACTIVE 기준. 유효한 기존 세션이 있으면 `isAlreadyMember`를 계산하며 없으면 false.
무효·재발급된 토큰: `404 INVITATION_NOT_FOUND`.

### POST `/invitations/{inviteToken}/join`

본문 없음. 필요 시 세션 자동 발급. 새 멤버 생성: `201`, 기존 멤버: `200`.
기존 ACTIVE 멤버의 중복 요청은 멱등 처리한다. 탈퇴 멤버는 기존 멤버 행을 MEMBER로 재활성화하며 `200`을 반환한다.

```json
{ "data": { "id": "member_2", "teamId": "team_1", "userId": "user_2", "role": "MEMBER", "joinedAt": "2026-10-04T04:00:00Z" } }
```

오류: `INVITATION_NOT_FOUND`.

### GET `/teams/{teamId}/invitation`

권한: ACTIVE ADMIN. 성공: `200`.

```json
{ "data": { "inviteUrl": "https://example.com/invite/{token}" } }
```

### POST `/teams/{teamId}/invitation/regenerate`

권한: ACTIVE ADMIN. 본문 없음. 성공: `200`, 응답은 위 초대 링크 응답과 동일.
기존 토큰은 즉시 무효화된다. 링크의 Origin은 `FRONTEND_ORIGIN` 사용.
두 초대 링크 API 오류: `NOT_TEAM_MEMBER`, `ADMIN_REQUIRED`, `TEAM_NOT_FOUND`.

### POST `/teams/{teamId}/admin-transfer`

권한: ACTIVE ADMIN. 성공: `200`.

```json
{ "targetTeamMemberId": 10 }
```

대상은 같은 팀의 ACTIVE MEMBER이며 본인이 아니어야 한다. 기존 ADMIN은 MEMBER로 변경된다.

```json
{ "data": { "adminTeamMemberId": "member_10" } }
```

오류: `VALIDATION_ERROR`, `NOT_TEAM_MEMBER`, `ADMIN_REQUIRED`, `MEMBER_NOT_FOUND`.

### DELETE `/teams/{teamId}/members/me`

권한: ACTIVE 팀원. 성공: `204`.

- MEMBER: 탈퇴 시각 설정.
- 다른 ACTIVE 팀원이 있는 ADMIN: `409 ADMIN_TRANSFER_REQUIRED`.
- 마지막 ACTIVE ADMIN: 팀, 팀원, 팀 식당, 리뷰, 투표 및 확정 결과 삭제. 공유 Restaurant와 User는 유지.
- 위임 후 탈퇴하려면 관리자 위임 API 성공 후 이 API를 호출한다. 통합 API는 현재 없음.

## 4. 사무실 위치

### GET `/teams/{teamId}/office`

권한: ACTIVE 팀원. 성공: `200`. 미설정 상태는 `{ "data": null }`.

```json
{ "data": { "kakaoPlaceId": "987654", "name": "솔로몬 오피스", "address": "서울 강남구", "latitude": 37.5009, "longitude": 127.0364 } }
```

### PUT `/teams/{teamId}/office`

권한: ACTIVE 팀원(ADMIN 전용 아님). 성공: `200`.
요청은 위 data 객체와 동일한 5개 필드. ID/이름/주소는 필수·blank 불가, 위도/경도는 필수 숫자.
서버는 전달받은 장소 선택 결과를 팀 공용 위치로 저장하며 카카오 API를 재호출하지 않는다.
응답은 조회 응답과 동일하다.
오류: `VALIDATION_ERROR`, `NOT_TEAM_MEMBER`, `TEAM_NOT_FOUND`.

## 5. 카카오 장소 검색

### GET `/places/search?query=역삼%20한식&page=1&size=5`

권한: 공개(현재 구현상 세션/팀원 검증 없음). 성공: `200`.

| 파라미터 | 조건 | 기본값 |
| --- | --- | --- |
| query | 필수, blank 불가 | 없음 |
| page | 1~45 | 1 |
| size | 1~15 | 5 |

```json
{ "data": { "items": [{ "kakaoPlaceId": "12345678", "name": "맛있는 식당", "address": "서울 강남구", "latitude": 37.501, "longitude": 127.039, "category": "한식", "kakaoPlaceUrl": "https://place.map.kakao.com/12345678" }], "page": 1, "pageSize": 5, "totalCount": 20, "totalPages": 4, "hasNextPage": true } }
```

백엔드가 카카오 검색 API를 호출하고 결과 장소를 DB에 저장한다. API 키는 응답에 노출하지 않는다.
오류: `400 VALIDATION_ERROR`, `502 KAKAO_API_ERROR`.

## 6. 팀 식당

모든 API는 ACTIVE 팀원 전용. ADMIN과 MEMBER 모두 사용 가능.

### POST `/teams/{teamId}/restaurants`

```json
{ "kakaoPlaceId": "12345678" }
```

ID 필수·blank 불가. **장소 검색 결과에서 선택해 등록한다.** DB에 없는 장소 ID는 `404 KAKAO_PLACE_NOT_FOUND`.
등록 시 카카오 API를 다시 호출하지 않는다. 같은 팀 중복은 `409 RESTAURANT_ALREADY_REGISTERED`.
성공: `201`.

```json
{ "data": { "id": "teamRestaurant_1", "restaurantId": "restaurant_1", "kakaoPlaceId": "12345678", "name": "맛있는 식당", "address": "서울 강남구", "latitude": 37.501, "longitude": 127.039, "category": "한식", "kakaoPlaceUrl": "https://place.map.kakao.com/12345678" } }
```

### GET `/teams/{teamId}/restaurants`

성공: `200`. 페이지네이션 없이 반환.

| 파라미터 | 의미 |
| --- | --- |
| keyword | 선택, 이름·카테고리·주소 검색 |
| category | 선택, 카테고리 정확히 일치 |
| sort | LATEST(기본), NAME, RATING_DESC |

LATEST는 등록 최신순, NAME은 이름순, RATING_DESC는 평균 별점 내림차순이며 리뷰 없는 식당은 뒤에 둔다.

```json
{
  "data": {
    "restaurants": [{
      "id": "teamRestaurant_1", "restaurantId": "restaurant_1", "kakaoPlaceId": "12345678",
      "name": "맛있는 식당", "address": "서울 강남구", "latitude": 37.501, "longitude": 127.039,
      "category": "한식", "kakaoPlaceUrl": "https://place.map.kakao.com/12345678",
      "registeredByNickname": "익명 사용자 1234", "createdAt": "2026-10-04T04:00:00Z",
      "averageRating": 4.5, "reviewCount": 2,
      "latestReview": { "nickname": "익명 사용자 5678", "rating": 5, "content": "맛있어요", "updatedAt": "2026-10-04T05:00:00Z" }
    }],
    "totalCount": 6, "totalReviewCount": 7, "categoryCounts": { "한식": 2, "양식": 4 }
  }
}
```

리뷰가 없으면 `averageRating: null`, `reviewCount: 0`, `latestReview: null`.
`latestReview`는 updatedAt 최신 리뷰. `totalCount`, `totalReviewCount`, `categoryCounts`는 검색·필터와 무관한 팀 전체 기준이다. `categoryCounts`에 별도 `전체` 키는 없다.

### GET `/teams/{teamId}/restaurants/{teamRestaurantId}`

성공: `200`. `data`는 위 restaurants 배열의 개별 객체와 동일(최신 리뷰 포함).
다른 팀 소속 또는 없는 팀 식당은 `404 TEAM_RESTAURANT_NOT_FOUND`.

### DELETE `/teams/{teamId}/restaurants/{teamRestaurantId}`

성공: `204`. 팀 식당 연결과 해당 리뷰 삭제. 공유 Restaurant 원본 유지.
다른 팀 소속 또는 없는 팀 식당은 `404 TEAM_RESTAURANT_NOT_FOUND`.
공통 권한 오류: `403 NOT_TEAM_MEMBER`.

## 7. 리뷰

공통 경로: `/teams/{teamId}/restaurants/{teamRestaurantId}/reviews`.
모든 API는 ACTIVE 팀원 전용이며 식당도 요청 팀 소속이어야 한다.

### PUT `{공통 경로}/me`

```json
{ "rating": 5, "content": "점심 먹기 좋아요" }
```

별점은 필수 정수 1~5. 내용은 필수·blank 불가·최대 200자.
한 팀원당 한 팀 식당에 리뷰 한 개. 신규 `201`, 기존 리뷰 덮어쓰기는 `200`.
기존 ID/createdAt 유지, rating/content/updatedAt 변경. 별도 POST/PATCH API 없음.

```json
{ "data": { "id": "review_1", "teamRestaurantId": "teamRestaurant_1", "rating": 5, "content": "점심 먹기 좋아요", "authorNickname": "익명 사용자 1234", "createdAt": "2026-10-04T04:00:00Z", "updatedAt": "2026-10-04T04:00:00Z" } }
```

### GET `{공통 경로}`

성공: `200`. updatedAt 최신순. `isMine`은 현재 TeamMember가 작성자인지 여부.

```json
{ "data": [{ "id": "review_1", "rating": 5, "content": "맛있어요", "authorNickname": "익명 사용자 1234", "isMine": true, "createdAt": "2026-10-04T04:00:00Z", "updatedAt": "2026-10-04T05:00:00Z" }] }
```

### DELETE `{공통 경로}/me`

성공: `204`. 현재 팀원의 리뷰만 삭제. 없으면 `404 REVIEW_NOT_FOUND`.
공통 오류: `NOT_TEAM_MEMBER`, `TEAM_RESTAURANT_NOT_FOUND`; 입력 오류: `VALIDATION_ERROR`.

## 8. 점심 투표

공통 경로: `/teams/{teamId}/votes`. 모든 API는 ACTIVE 팀원 전용. 확정도 ADMIN 전용이 아니다.
한 팀에 OPEN 투표 여러 개 가능. 상태는 OPEN / CONFIRMED.
후보·참여 여부·투표 변경·확정은 OPEN에서만 가능. 확정 후 변경은 `409 VOTE_ALREADY_CONFIRMED`.

### POST `{공통 경로}`

```json
{ "title": "오늘 점심 뭐 먹지?" }
```

title 필수·blank 불가·최대 100자. 성공: `201`.

```json
{ "data": { "id": "vote_1", "teamId": "team_1", "title": "오늘 점심 뭐 먹지?", "status": "OPEN", "createdAt": "2026-10-04T04:00:00Z" } }
```

생성 시 ACTIVE 팀원을 participating=true로 초기화한다. 생성 이후 가입한 팀원은 자신의 참여 API를 호출해야 투표할 수 있다.

### GET `{공통 경로}?status=OPEN`

성공: `200`. status 선택(OPEN/CONFIRMED). 생략 시 전체, OPEN 우선·createdAt 최신순.

```json
{ "data": [{ "id": "vote_1", "title": "오늘 점심 뭐 먹지?", "status": "OPEN", "createdAt": "2026-10-04T04:00:00Z", "participantCount": 2, "candidateCount": 1, "myParticipation": true, "myVoteCandidateId": "candidate_1" }] }
```

participantCount는 participating=true인 저장된 참여자 수. 투표하지 않았으면 myVoteCandidateId=null, 참여 행이 없으면 myParticipation=false.

### GET `{공통 경로}/{voteId}`

성공: `200`. teamId + voteId 범위 확인. 다른 팀 소속/없음은 `404 VOTE_NOT_FOUND`.

```json
{
  "data": {
    "id": "vote_1", "title": "오늘 점심 뭐 먹지?", "status": "OPEN", "createdAt": "2026-10-04T04:00:00Z",
    "participantCount": 2,
    "participants": [{ "teamMemberId": "member_1", "nickname": "익명 사용자 1234", "participating": true }],
    "candidates": [{ "candidateId": "candidate_1", "restaurantId": "restaurant_1", "name": "맛있는 식당", "category": "한식", "address": "서울 강남구", "averageRating": 4.5, "voteCount": 1, "isMyVote": true }],
    "myParticipation": true, "myVoteCandidateId": "candidate_1", "confirmedMenu": null
  }
}
```

participants는 저장된 투표 참여 상태 목록이다. 예시 배열은 축약했으며 count와 배열 길이가 반드시 같지는 않다(불참 포함).
확정 후 confirmedMenu는 아래 확정 응답의 객체로 반환한다.

### PUT `{공통 경로}/{voteId}/participants/me`

```json
{ "participating": false }
```

필수 boolean. 본인만 변경 가능. 불참 전환 시 본인의 해당 투표 기록 삭제. 다른 투표에는 영향 없음.
성공: `200`.

```json
{ "data": { "teamMemberId": "member_1", "participating": false } }
```

### POST `{공통 경로}/{voteId}/candidates`

```json
{ "teamRestaurantId": 10 }
```

필수 양의 정수. 해당 팀에 등록된 식당만 후보 가능. 불참자도 후보 등록 가능.
같은 투표의 Restaurant 중복은 `409 VOTE_CANDIDATE_ALREADY_EXISTS`.
다른 팀 식당/없음은 `404 TEAM_RESTAURANT_NOT_FOUND`.
성공: `201`.

```json
{ "data": { "candidateId": "candidate_1", "restaurantId": "restaurant_1", "name": "맛있는 식당", "category": "한식", "address": "서울 강남구", "averageRating": 4.5, "voteCount": 0, "isMyVote": false } }
```

averageRating은 해당 팀 식당 리뷰 평균이며 리뷰 없으면 null.

### PUT `{공통 경로}/{voteId}/vote`

```json
{ "voteCandidateId": 100 }
```

필수 양의 정수. participating=true인 본인만 투표 가능. **투표당 한 후보만 선택**.
처음에는 생성, 재투표는 같은 기록의 후보 변경. 성공은 모두 `200`이며 data는 위 후보 객체와 같은 형태.
불참/참여 행 없음: `409 VOTE_PARTICIPATION_REQUIRED`.
다른 투표 후보/없음: `404 VOTE_CANDIDATE_NOT_FOUND`.

### POST `{공통 경로}/{voteId}/confirm`

```json
{ "voteCandidateId": 100 }
```

필수 양의 정수. 해당 투표의 후보이면 확정 가능(최다 득표나 참여 상태를 요구하지 않음).
성공: `200`. 확정 결과는 투표당 하나이며 재확정은 `409 VOTE_ALREADY_CONFIRMED`.

```json
{ "data": { "voteId": "vote_1", "status": "CONFIRMED", "confirmedMenu": { "candidateId": "candidate_1", "restaurantId": "restaurant_1", "name": "맛있는 식당", "voteCount": 5, "confirmedAt": "2026-10-04T05:00:00Z" } } }
```

다른 투표 후보/없음: `404 VOTE_CANDIDATE_NOT_FOUND`.

### GET `{공통 경로}/history`

성공: `200`. CONFIRMED 투표만, confirmedAt 최신순. 현재 주간/월간 필터 없음.

```json
{ "data": [{ "voteId": "vote_1", "title": "오늘 점심 뭐 먹지?", "confirmedAt": "2026-10-04T05:00:00Z", "restaurantId": "restaurant_1", "restaurantName": "맛있는 식당", "voteCount": 5, "participantCount": 6 }] }
```

## 9. 오류 코드

| HTTP | code |
| --- | --- |
| 400 | VALIDATION_ERROR |
| 401 | SESSION_REQUIRED |
| 403 | NOT_TEAM_MEMBER, ADMIN_REQUIRED |
| 404 | TEAM_NOT_FOUND, MEMBER_NOT_FOUND, INVITATION_NOT_FOUND, TEAM_RESTAURANT_NOT_FOUND, REVIEW_NOT_FOUND, KAKAO_PLACE_NOT_FOUND, VOTE_NOT_FOUND, VOTE_CANDIDATE_NOT_FOUND |
| 409 | ADMIN_TRANSFER_REQUIRED, RESTAURANT_ALREADY_REGISTERED, VOTE_CANDIDATE_ALREADY_EXISTS, VOTE_ALREADY_CONFIRMED, VOTE_PARTICIPATION_REQUIRED |
| 502 | KAKAO_API_ERROR |
| 500 | INTERNAL_SERVER_ERROR |

## 10. 현재 제공하지 않는 API

- 내 팀 목록, 독립적인 팀원 목록, 명시적인 팀 삭제 API
- 관리자 위임·탈퇴 통합 API
- 로그아웃, 닉네임/프로필 수정
- 사무실 해제 및 `/office-location` 경로
- 투표 제목 수정·삭제·재시작·후보 삭제·투표 취소 API
- 복수 선택 투표, 종료 시간, 자동 확정, 추천
- `/lunch-history` 경로, 주간·월간 히스토리 필터

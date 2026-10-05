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

### PATCH `/session/me`

현재 세션 사용자의 공통 닉네임을 변경한다. 성공: `200`.

```json
{ "nickname": "익명4d88d" }
```

- nickname 필수. 앞뒤 공백 제거 후 2~12자이며 blank·제어문자·줄바꿈은 금지한다.
- 기존 사용자의 ID/세션은 유지한다. 새 User나 쿠키는 발급하지 않는다.
- 변경한 이름은 참여 중인 모든 팀의 팀원·리뷰·투표 작성자 조회에 반영된다.
- 유효한 세션이 없으면 `401 SESSION_REQUIRED`.
- 검증 실패는 `400 VALIDATION_ERROR` ProblemDetail 및 `fieldErrors.nickname`.
- `/session/me`에서 지원하지 않는 메서드는 `405 METHOD_NOT_ALLOWED` ProblemDetail과 Allow 헤더를 반환한다.

```json
{ "data": { "id": "user_1", "nickname": "익명4d88d" } }
```

## 3. 팀과 초대

### GET `/teams`

현재 유효한 익명 세션의 ACTIVE 소속 팀을 페이지네이션 없이 이름순(동일 이름은 ID순) 반환한다.
세션 누락·무효: `401 SESSION_REQUIRED`. 사용자를 새로 만들지 않는다.
유효한 사용자에게 소속 팀이 없으면 `200`과 빈 배열을 반환한다. 먼저 `/session/me`로 사용자 식별 가능.
현재 sort 쿼리와 최근 확정 점심 필드는 제공하지 않는다.

```json
{ "data": [{ "teamId": "team_1", "name": "솔로몬 개발팀", "description": "점심 메뉴를 함께 정해요", "myRole": "ADMIN", "memberCount": 6 }] }
```

`teamId`는 팀 상세의 `id`와 같은 문자열 ID다. memberCount는 ACTIVE 팀원만 계산한다.

### GET `/teams/{teamId}/members`

권한: ACTIVE 팀원. 성공: `200`. ACTIVE 멤버만 joinedAt·ID 오름차순으로 반환한다.
`isMe`는 현재 세션 사용자의 팀 멤버 여부다. 비팀원·탈퇴자·세션 누락은 `403 NOT_TEAM_MEMBER`.

```json
{ "data": [{ "teamMemberId": "member_1", "userId": "user_1", "nickname": "익명 사용자 1234", "role": "ADMIN", "joinedAt": "2026-10-04T04:00:00Z", "isMe": true }] }
```

### DELETE `/teams/{teamId}`

권한: ACTIVE ADMIN. 성공: `204`, 본문 없음.
팀의 투표·확정 결과, 리뷰, 팀 식당 연결, 팀원, 팀을 한 트랜잭션으로 삭제한다.
다른 팀 데이터와 공유 Restaurant, User는 유지한다.
오류: `NOT_TEAM_MEMBER`, `ADMIN_REQUIRED`, `TEAM_NOT_FOUND`.

### POST `/teams/{teamId}/transfer-and-leave`

권한: ACTIVE ADMIN. 성공: `204`, 본문 없음.

```json
{ "targetTeamMemberId": 10 }
```

필수 양의 정수. 같은 팀의 다른 ACTIVE MEMBER에게 ADMIN을 위임하고,
현재 ADMIN을 MEMBER로 변경한 뒤 탈퇴 처리한다. 두 변경은 하나의 트랜잭션으로 커밋/롤백된다.
팀은 유지되며 기존 admin-transfer API도 유지된다.
오류: `VALIDATION_ERROR`, `NOT_TEAM_MEMBER`, `ADMIN_REQUIRED`, `MEMBER_NOT_FOUND`.

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
    "memberCount": 1, "isAlreadyMember": false, "suggestedNickname": "익명a1b2c3",
    "members": [{ "id": "member_1", "role": "ADMIN", "joinedAt": "2026-10-04T04:00:00Z", "user": { "id": "user_1", "nickname": "익명 사용자 1234" } }]
  }
}
```

팀원 목록/개수는 ACTIVE 기준. 유효한 기존 세션이 있으면 `isAlreadyMember`를 계산하며 없으면 false.
기존 세션 사용자는 현재 닉네임을 `suggestedNickname`으로 반환한다. 세션이 없으면 짧은 랜덤 닉네임(예: `익명a1b2c3`)을 추천하며 User/세션을 생성하지 않는다. FE는 최초 응답으로 입력창을 초기화하고 사용자가 수정한 값을 가입 요청으로 보낸다. 추천값은 재조회 시 달라질 수 있다.
무효·재발급된 토큰: `404 INVITATION_NOT_FOUND`.

### POST `/invitations/{inviteToken}/join`

선택 JSON 본문:

```json
{ "nickname": "수달4821" }
```

본문을 보내면 `nickname`은 필수이며 앞뒤 공백 제거 후 2~12자, blank·제어문자·줄바꿈은 금지한다. 오류는 `400 VALIDATION_ERROR` ProblemDetail과 `fieldErrors.nickname`으로 반환한다. 닉네임 중복은 허용한다.

기존 본문 없는 요청도 지원하며 기존 사용자 이름을 유지하거나 짧은 자동 닉네임을 생성한다. 필요 시 세션 자동 발급. 새 멤버 생성: `201`, 기존 멤버: `200`.
닉네임은 User 공통 값으로 모든 팀에 반영된다. User 생성/닉네임 변경/가입은 하나의 트랜잭션이다. 기존 ACTIVE 멤버의 중복 요청은 멱등 처리하며 닉네임을 변경하지 않는다. 탈퇴 멤버는 기존 멤버 행을 MEMBER로 재활성화하며 `200`을 반환한다.

```json
{ "data": { "id": "member_2", "teamId": "team_1", "userId": "user_2", "nickname": "수달4821", "role": "MEMBER", "joinedAt": "2026-10-04T04:00:00Z" } }
```

오류: `INVITATION_NOT_FOUND`.

### GET `/teams/{teamId}/invitation`

권한: ACTIVE 팀원(ADMIN과 MEMBER 모두). 성공: `200`.

```json
{ "data": { "inviteUrl": "https://example.com/invite/{token}" } }
```

### POST `/teams/{teamId}/invitation/regenerate`

권한: ACTIVE ADMIN. 본문 없음. 성공: `200`, 응답은 위 초대 링크 응답과 동일.
기존 토큰은 즉시 무효화된다. 링크의 Origin은 `FRONTEND_ORIGIN` 사용.
초대 링크 조회 오류: `NOT_TEAM_MEMBER`, `TEAM_NOT_FOUND`. 재발급은 추가로 `ADMIN_REQUIRED` 반환 가능.

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
- 관리자 위임과 탈퇴를 한 번에 처리하려면 `/teams/{teamId}/transfer-and-leave`를 사용한다.

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

## 8. 점심 투표 — 복수 선택 UI 계약

전체 요청·응답 DTO, 상태·마감·추천·히스토리 정책은 [투표 API 상세 명세](lunch-vote.md)를 따른다.
기존 단일 선택 계약은 대체되며 아래 경로는 모두 ACTIVE 팀원 전용이다.
Base path는 `/api/teams/{teamId}`다.

| Method | 경로 | 성공 | 설명 |
| --- | --- | --- | --- |
| POST | /votes | 201 | closesAt 필수·name 선택(title 별칭 지원) |
| GET | /votes | 200 | 최신순, 마감 정산, 고유 투표자 수·내 선택 전체 |
| GET | /votes/{voteId} | 200 | session·creatorNickname·decision |
| PATCH | /votes/{voteId} | 200 | 이름은 모든 상태, 종료 시간은 OPEN·마감 전에서 부분 수정 |
| DELETE | /votes/{voteId} | 204 | 상태와 무관하게 삭제 |
| POST | /votes/{voteId}/close | 200 | 모든 ACTIVE 팀원이 OPEN·마감 전 투표를 즉시 정산 |
| POST | /votes/{voteId}/restart | 200 | 생성자, 후보/참여 유지·표 초기화·3시간 뒤 마감 |
| GET | /votes/{voteId}/participants | 200 | 참여자 목록 |
| PUT | /votes/{voteId}/participants/{targetTeamMemberId} | 200 | 다른 팀원도 변경 가능, 불참 시 표 전체 삭제 |
| GET | /votes/{voteId}/candidates | 200 | 후보 목록, 리뷰 없으면 averageRating=0 |
| POST | /votes/{voteId}/candidates | 201 | kakaoPlaceId + source |
| DELETE | /votes/{voteId}/candidates/{candidateId} | 204 | 후보 및 연결된 표 삭제 |
| GET | /votes/{voteId}/recommendations?cursor=0 | 200 | 최근 메뉴·후보·불참 리뷰 제외, 한 건 추천 |
| PUT | /votes/{voteId}/ballots/me | 200 | candidateIds 배열로 내 복수 선택 전체 교체 |
| DELETE | /votes/{voteId}/ballots/me | 204 | 내 투표 전체 취소 |
| GET | /votes/{voteId}/results | 200 | 득표·고유 투표자 기준 득표율·ballot 전체 |
| POST | /votes/{voteId}/decision | 201 | 생성자가 CLOSED 공동 최다 후보 또는 단독 0표 후보를 확정 |
| PATCH | /votes/{voteId}/decision | 200 | 생성자가 후보 식당으로 확정 수정 |
| DELETE | /votes/{voteId}/decision | 204 | 생성자가 확정 삭제·CLOSED 전환 |
| GET | /lunch-history?view=WEEK&date=2026-10-04 | 200 | 한국 시간, 월요일 시작 |
| GET | /lunch-history?view=MONTH&month=2026-10 | 200 | 한국 시간, 월간 |

생성 요청은 `{ "name": "asf", "closesAt": "2026-10-04T07:00:00Z" }`이며 name은 선택이다.
기존 프론트의 `title` 필드도 생성 요청에서 name 별칭으로 받는다. 응답 필드는 항상 name이다.
name/title을 보내면 blank 불가·최대 40자이며, 생략/null이면 null을 반환한다.
closesAt은 필수이며 서버 현재 시각보다 이후여야 한다.

상태는 OPEN/CLOSED/CONFIRMED. 스케줄러와 목록·상세·결과 조회에서 마감 정산한다.
투표 생성·마감 시간 수정에서 `closesAt`이 서버 현재 시각보다 미래가 아니면
`400 VALIDATION_ERROR`, `detail: "마감 시간은 현재 시각 이후여야 합니다."`,
`fieldErrors.closesAt: "선택한 마감 시간이 지났습니다. 다시 설정해 주세요."`를 반환한다.
서버는 최소 1분을 강제하거나 전송받은 시간을 보정하지 않는다.

`POST /votes/{voteId}/close`는 본문 없이 호출한다. 종료 시간을 서버 현재 시각으로 변경한 뒤
같은 트랜잭션에서 정산하여 VoteSession을 반환한다. 이미 마감됐거나 시간이 지난 투표는
`409 VOTE_NOT_OPEN`이다. 생성자 전용 기능이 아니며 모든 ACTIVE 팀원이 사용할 수 있다.

단독 양수 최다 득표는 AUTO 확정, 동점 또는 무투표는 CLOSED. 후보가 정확히 하나이고 0표이면 생성자가 해당 후보를 수동 확정할 수 있다.
복수 선택은 후보별 행으로 저장하며 `(session_id, candidate_id, team_member_id)` UNIQUE를 사용한다.
기존 MySQL 스키마 전환 절차는 상세 명세와 [전환 SQL](migrations/20261004-vote-ui-contract.sql)에 있다.

## 9. 오류 코드

| HTTP | code |
| --- | --- |
| 400 | VALIDATION_ERROR |
| 401 | SESSION_REQUIRED |
| 403 | NOT_TEAM_MEMBER, ADMIN_REQUIRED, VOTE_CREATOR_REQUIRED |
| 404 | TEAM_NOT_FOUND, MEMBER_NOT_FOUND, INVITATION_NOT_FOUND, TEAM_RESTAURANT_NOT_FOUND, REVIEW_NOT_FOUND, KAKAO_PLACE_NOT_FOUND, VOTE_NOT_FOUND, VOTE_CANDIDATE_NOT_FOUND, TEAM_MEMBER_NOT_FOUND, DECISION_NOT_FOUND |
| 409 | ADMIN_TRANSFER_REQUIRED, RESTAURANT_ALREADY_REGISTERED, VOTE_CANDIDATE_ALREADY_EXISTS, VOTE_ALREADY_CONFIRMED, VOTE_PARTICIPATION_REQUIRED, VOTE_NOT_OPEN, VOTE_NOT_CLOSED, INVALID_DECISION_CANDIDATE |
| 502 | KAKAO_API_ERROR |
| 500 | INTERNAL_SERVER_ERROR |

## 10. 현재 제공하지 않는 API

- 로그아웃, 닉네임/프로필 수정
- 사무실 해제 및 `/office-location` 경로

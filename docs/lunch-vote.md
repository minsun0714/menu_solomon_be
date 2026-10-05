# 메뉴솔로몬 투표 API — 현재 UI 계약

기존 단일 선택 투표 계약을 대체한다. Base path는 `/api/teams/{teamId}`다.

## 공통

- 모든 요청은 해당 팀의 ACTIVE 팀원(`leftAt == null`)만 가능하다.
- 세션 쿠키 `menu_solomon_session`을 사용하며 프론트 요청에 `credentials: include`를 설정한다.
- 이 API에서는 사용자나 세션을 새로 만들지 않는다. 비팀원·탈퇴자·세션 누락/무효는 `403 NOT_TEAM_MEMBER`.
- JSON 성공 응답은 `{ "data": ... }`. `204`는 본문 없음.
- 오류 응답은 Spring ProblemDetail과 `application/problem+json` 사용.
- 날짜는 ISO 8601 UTC, 날짜 경계는 `Asia/Seoul`.
- 응답 ID는 문자열 접두사, 요청 JSON의 FK ID는 양의 정수다.
- 경로의 team/vote/member/candidate ID는 숫자 또는 `team_1`, `vote_1`, `member_1`, `candidate_1` 허용.

상태: `OPEN`(진행 중), `CLOSED`(마감·미확정), `CONFIRMED`(확정).
후보 출처: `MANUAL`, `RECOMMENDED`. 확정 유형: `AUTO`, `MANUAL`.

## 공통 DTO

### VoteSession

```json
{
  "id": "vote_1", "teamId": "team_1", "name": "오늘 점심 투표",
  "createdByTeamMemberId": "member_1", "status": "OPEN",
  "closesAt": "2026-10-04T07:00:00Z", "createdAt": "2026-10-04T04:00:00Z"
}
```

생성 요청에 이름이 없으면 `name=null`. 프론트에서 `점심 투표 #{id}`처럼 표시할 수 있다.

### Participant

```json
{
  "id": "participant_1", "sessionId": "vote_1", "teamMemberId": "member_1",
  "nickname": "익명 사용자 1234", "participating": true
}
```

생성 시점의 ACTIVE 팀원을 true로 초기화한다. 이후 가입자는 참여 변경 API를 통해 참여 행을 생성한다.
참여 상태는 팀원 자체가 아니라 투표별로 저장된다.

### Restaurant

```json
{
  "id": "restaurant_1", "kakaoPlaceId": "12345678", "name": "맛있는 식당",
  "address": "서울 강남구", "latitude": 37.501, "longitude": 127.039,
  "category": "한식", "kakaoPlaceUrl": "https://place.map.kakao.com/12345678"
}
```

### Candidate

```json
{
  "id": "candidate_1", "sessionId": "vote_1", "restaurantId": "restaurant_1", "source": "MANUAL",
  "restaurant": {
    "id": "restaurant_1", "kakaoPlaceId": "12345678", "name": "맛있는 식당",
    "address": "서울 강남구", "latitude": 37.501, "longitude": 127.039,
    "category": "한식", "kakaoPlaceUrl": "https://place.map.kakao.com/12345678"
  },
  "averageRating": 4.5
}
```

평균 별점은 현재 팀의 식당 리뷰 기준. 리뷰가 없거나 팀 식당 연결이 없으면 **0**이다.
카카오 검색에서 캐시된 Restaurant는 팀 맛집으로 등록되지 않았어도 후보로 추가할 수 있다.

### Ballot

```json
{
  "id": "ballot_1", "sessionId": "vote_1", "candidateId": "candidate_1", "teamMemberId": "member_1",
  "createdAt": "2026-10-04T04:30:00Z", "updatedAt": "2026-10-04T04:30:00Z"
}
```

선택 후보마다 한 행을 저장한다. 동일 팀원은 한 투표에서 여러 후보를 선택할 수 있다.
전체 교체 시 기존 행을 삭제하고 새 행을 생성하므로 ballot ID/시각이 바뀔 수 있다.

### Decision

```json
{
  "id": "decision_1", "sessionId": "vote_1", "restaurantId": "restaurant_1",
  "confirmedByTeamMemberId": "member_1", "confirmationType": "MANUAL",
  "confirmedAt": "2026-10-04T07:00:00Z"
}
```

자동 확정은 confirmedByTeamMemberId=null. 수정은 동일 decision ID를 유지하며 MANUAL로 변경되고 수정 시각으로 confirmedAt을 갱신한다.

## 1. 투표 세션

### POST `/votes` — 생성

모든 ACTIVE 팀원, `201 Created`. 한 팀에 OPEN 투표 여러 개 가능.

```json
{ "name": "asf", "closesAt": "2026-10-04T07:00:00Z" }
```

`name`은 선택이며 지정하면 blank 불가·최대 40자다. 현재 프론트 요청과 호환되도록
`title`을 `name`의 별칭으로도 받는다. 두 필드를 동시에 보내지 않는다.

```json
{ "title": "asf", "closesAt": "2026-10-04T07:00:00Z" }
```

위 두 요청 모두 응답에는 `name: "asf"`로 반환하고 DB의 `name` 컬럼에 저장한다.
이름을 생략하거나 null로 보내면 기존처럼 `name=null`이다.
closesAt 필수·현재 시각 이후. 3시간 기본값은 프론트에서 계산해 전달한다.
생성 시 ACTIVE 팀원 전체를 participating=true로 초기화한다. 응답 data는 VoteSession이며 name은 요청한 이름(미지정 시 null)이다.

### GET `/votes` — 목록

`200 OK`. createdAt 최신순, 동일 시각은 ID 내림차순. 먼저 마감된 OPEN 투표를 정산한다.
이전 status 필터 계약은 사용하지 않는다.

```json
{
  "data": [{
    "id": "vote_1", "teamId": "team_1", "name": "오늘 점심 투표", "createdByTeamMemberId": "member_1",
    "creatorNickname": "익명 사용자 1234", "status": "OPEN", "closesAt": "2026-10-04T07:00:00Z",
    "createdAt": "2026-10-04T04:00:00Z", "participantCount": 6, "candidateCount": 3,
    "ballotCount": 4, "myBallotCandidateIds": ["candidate_1", "candidate_3"]
  }]
}
```

participantCount는 true인 참여 행 수. ballotCount는 한 개 이상 선택한 **고유 팀원 수**.
myBallotCandidateIds는 현재 팀원 선택 전체이며 없으면 빈 배열이다.

### GET `/votes/{voteId}` — 상세

`200 OK`. 마감 시간이 지났으면 먼저 정산한다.

```json
{
  "data": {
    "session": {
      "id": "vote_1", "teamId": "team_1", "name": "오늘 점심 투표", "createdByTeamMemberId": "member_1",
      "status": "OPEN", "closesAt": "2026-10-04T07:00:00Z", "createdAt": "2026-10-04T04:00:00Z"
    },
    "creatorNickname": "익명 사용자 1234", "decision": null
  }
}
```

참여자·후보·결과는 아래 개별 API로 조회한다. 확정 상태에서는 decision이 Decision 객체다.
다른 팀의 투표 ID는 `404 VOTE_NOT_FOUND`.

### PATCH `/votes/{voteId}` — 이름·종료 시간 수정

모든 ACTIVE 팀원. `200`, data=VoteSession. 이름만 변경하는 요청은 OPEN·CLOSED·CONFIRMED 모두 가능하며, 마감 시간이 지나도 허용한다. 종료 시간 변경은 OPEN·마감 전에서만 가능하다.

```json
{ "name": "금요일 점심 투표", "closesAt": "2026-10-04T08:00:00Z" }
```

부분 수정: 미전송 필드 유지. name은 blank 불가·최대 40자. closesAt은 현재 시각 이후. 마감/확정 상태에서 이름과 종료 시간을 함께 보내면 요청 전체를 거절하고 이름도 변경하지 않는다. 이름 변경은 상태·종료 시간·확정 결과를 유지한다.
빈 요청, 명시적 null, 잘못된 타입/시간은 `400 VALIDATION_ERROR`.

생성·마감 시간 수정 요청의 `closesAt`은 서버가 처리하는 시점보다 미래여야 한다.
서버는 최소 1분을 강제하지 않고 시간을 임의로 보정하지 않는다. 과거 또는 현재와 같은 시간은
`400 VALIDATION_ERROR` ProblemDetail로 반환하며, 시간 필드 아래 표시할 안내를 포함한다.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "마감 시간은 현재 시각 이후여야 합니다.",
  "instance": "/api/teams/team_15/votes",
  "code": "VALIDATION_ERROR",
  "fieldErrors": {
    "closesAt": "선택한 마감 시간이 지났습니다. 다시 설정해 주세요."
  }
}
```

프론트는 모달을 열 때 현재 시각 + 1분을 분 단위로 올림하여 로컬 `datetime-local`의
`min`을 설정하고, 제출 시 유효한 날짜인지와 최신 최소 시간을 다시 검증한다.
유효한 입력은 ISO 8601 UTC로 변환해 전송한다. 클라이언트/서버 시간 검증 실패 시 모달을 유지하고
입력창 아래 오류를 표시한다. 입력 변경 시 오류를 지우며 필드 오류에 대한 중복 토스트는 생략한다.

### DELETE `/votes/{voteId}` — 삭제

모든 ACTIVE 팀원. OPEN/CLOSED/CONFIRMED 모두 가능. 참여자·후보·투표·확정 결과 함께 삭제.
공유 Restaurant는 유지. `204 No Content`.

### POST `/votes/{voteId}/restart` — 다시 시작

투표 생성자만, OPEN/CLOSED만. 본문 없음. `200`, data=VoteSession.
후보와 참여 상태 유지, 모든 ballot 삭제, 상태 OPEN, 종료 시간 실행 시점부터 3시간 뒤.
CONFIRMED는 `409 VOTE_ALREADY_CONFIRMED`.

## 2. 참여 상태

### GET `/votes/{voteId}/participants`

`200`, data=Participant[]. 저장된 참여 행을 ID순 반환한다.

### PUT `/votes/{voteId}/participants/{targetTeamMemberId}`

모든 ACTIVE 팀원은 **본인과 다른 팀원** 상태를 변경할 수 있다. OPEN·마감 전.
대상도 같은 팀의 ACTIVE 팀원이어야 한다. 참여 행이 없으면 생성한다.

```json
{ "participating": false }
```

필수 boolean. false로 변경하면 대상 팀원의 해당 투표 ballot **전체 삭제**.
다른 투표의 참여/선택은 유지. `200`, data=Participant.
대상이 없거나 다른 팀/탈퇴자이면 `404 TEAM_MEMBER_NOT_FOUND`.

## 3. 후보

### GET `/votes/{voteId}/candidates`

`200`, data=Candidate[]. 등록 시각·ID 오름차순.

### POST `/votes/{voteId}/candidates`

모든 ACTIVE 팀원(불참자 포함), OPEN·마감 전.

```json
{ "kakaoPlaceId": "12345678", "source": "MANUAL" }
```

kakaoPlaceId 필수·blank 불가. source 필수, MANUAL/RECOMMENDED.
`GET /api/places/search`로 서버에 저장된 식당을 선택한다. 없는 캐시 ID는 `404 KAKAO_PLACE_NOT_FOUND`.
같은 투표에서 Restaurant 중복이면 `409 VOTE_CANDIDATE_ALREADY_EXISTS`.
`201 Created`, data=Candidate.

### DELETE `/votes/{voteId}/candidates/{candidateId}`

모든 ACTIVE 팀원, OPEN·마감 전. 해당 후보의 모든 ballot 함께 삭제. `204`.
다른 투표의 후보/없는 후보는 `404 VOTE_CANDIDATE_NOT_FOUND`.

## 4. 추천 점심

### GET `/votes/{voteId}/recommendations?cursor=0`

모든 ACTIVE 팀원. cursor는 0 이상 정수, 기본 0. 버튼을 누를 때 호출한다.
팀에 등록된 식당에서 아래 조건을 DB query로 적용해 한 번에 한 곳 반환한다.

- 해당 투표의 기존 후보 제외.
- 해당 팀의 최근 7일 내 확정 식당 제외(현재 시각에서 한국 시간으로 7일 전 이후).
- 해당 투표에서 participating=false인 팀원의 리뷰 제외.
- 평균 별점 내림차순, 동일 평점은 Restaurant ID 오름차순. 리뷰 없으면 0.

```json
{
  "data": {
    "items": [{
      "restaurant": {
        "id": "restaurant_1", "kakaoPlaceId": "12345678", "name": "맛있는 식당",
        "address": "서울 강남구", "latitude": 37.501, "longitude": 127.039,
        "category": "한식", "kakaoPlaceUrl": "https://place.map.kakao.com/12345678"
      },
      "averageRating": 4.5, "reason": "참여자 리뷰 평점 반영 · 최근 7일 내 선택 안 함"
    }],
    "nextCursor": 1
  }
}
```

추천 결과가 있으면 nextCursor=cursor+1. 다음 cursor에 결과가 없으면 items=[], nextCursor=null.
후보/리뷰 변경에 따라 추천 순서는 달라질 수 있다. 실제 후보 추가는 source=RECOMMENDED로 위 후보 API 호출.

## 5. 복수 선택 투표

### PUT `/votes/{voteId}/ballots/me` — 저장·변경

현재 팀원의 participating=true 필요. OPEN·마감 전.

```json
{ "candidateIds": [10, 12, 15] }
```

필수 비어 있지 않은 양의 정수 배열. 후보 수 제한 없음. 중복 ID는 서버에서 제거한다.
모든 후보가 해당 투표 소속인지 **먼저 검증**한 뒤 내 기존 ballot을 요청 목록으로 전부 교체한다.
검증 실패 시 기존 선택은 유지된다. 삭제·생성은 한 트랜잭션.
`200 OK`, data=Ballot[].

### DELETE `/votes/{voteId}/ballots/me` — 전체 취소

참여 중인 본인, OPEN·마감 전. 내 ballot 전체 삭제. `204`.

### GET `/votes/{voteId}/results` — 결과

마감 시간이 지났으면 먼저 정산. `200`.

```json
{
  "data": {
    "results": [{ "candidateId": "candidate_1", "voteCount": 3, "percentage": 75.0 }],
    "ballots": [{
      "id": "ballot_1", "sessionId": "vote_1", "candidateId": "candidate_1", "teamMemberId": "member_1",
      "createdAt": "2026-10-04T04:30:00Z", "updatedAt": "2026-10-04T04:30:00Z"
    }]
  }
}
```

배열은 예시로 축약했다. voteCount=해당 후보를 선택한 팀원 수.
percentage=`voteCount / 한 개 이상 투표한 고유 팀원 수 * 100` (소수점 가능, 투표자 없으면 0).
복수 선택이므로 percentage 합계는 100%를 초과할 수 있다.
ballots는 해당 투표 선택 행 전체. 후보별 집계는 DB에서 수행한다.

## 6. 마감·확정

스케줄러는 10초마다 한 번에 최대 100개 마감 투표를 독립 트랜잭션으로 정산한다.
목록·상세·결과 조회에도 지연 정산을 수행한다.
현재 시각이 closesAt과 같거나 이후이면 마감이다.

- 양수 득표의 단독 최다 후보: CONFIRMED, AUTO, confirmedByTeamMemberId=null.
- 공동 최다 또는 무투표(후보 없음 포함): CLOSED, 확정 결과 없음.
- confirmedAt은 실제 정산 시각.
- 세션 비관적 잠금과 decision 유니크 제약으로 중복 정산/확정을 방지한다.

### POST `/votes/{voteId}/decision` — 수동 확정

투표 생성자만, CLOSED에서만 가능. **공동 최다 후보 중 하나**를 선택하거나, **후보가 정확히 하나이고 0표인 경우 해당 후보**를 선택할 수 있다.
무투표 상태도 후보가 두 개 이상이면 0표 공동 최다 후보 중 수동 선택할 수 있다. 후보가 하나이면 0표여도 수동 확정 가능하다. 후보가 없으면 수동 확정할 수 없으며 다시 시작해 후보를 추가할 수 있다.

```json
{ "restaurantId": 10 }
```

필수 양의 정수 Restaurant ID(후보 ID 아님). `201`, data=Decision.
OPEN이면 `VOTE_NOT_CLOSED`, CONFIRMED이면 `VOTE_ALREADY_CONFIRMED`, 허용되지 않는 식당은 `INVALID_DECISION_CANDIDATE`.

### PATCH `/votes/{voteId}/decision` — 확정 수정

생성자만, 확정 결과가 있어야 한다. 요청은 위 restaurantId 형식.
득표수와 무관하게 해당 투표 후보 식당으로 변경 가능.
`200`, data=변경된 Decision. AUTO 결과 수정도 MANUAL로 전환한다.

### DELETE `/votes/{voteId}/decision` — 확정 삭제

생성자만. 확정 결과 삭제와 CLOSED 전환을 한 트랜잭션으로 처리한다. `204`.
후보·참여·ballot은 유지. 없는 결과는 `404 DECISION_NOT_FOUND`.

## 7. 확정 메뉴 히스토리

### GET `/lunch-history?view=WEEK&date=2026-10-04`

date가 속한 주, 월요일 시작. 한국 시간의 월요일 00:00 이상·다음 월요일 00:00 미만.

### GET `/lunch-history?view=MONTH&month=2026-10`

한국 시간의 해당 월 1일 00:00 이상·다음 달 1일 00:00 미만.
view 필수 WEEK/MONTH. WEEK에는 date, MONTH에는 month가 필수이며 다른 기간 필드는 보내지 않는다.
잘못된 날짜/조회 조합은 `400 VALIDATION_ERROR`.

`200`, 현재 존재하는 CONFIRMED 결과만 confirmedAt·decision ID 최신순.

```json
{
  "data": [{
    "decisionId": "decision_1", "sessionId": "vote_1", "confirmedAt": "2026-10-04T07:00:00Z",
    "restaurant": {
      "id": "restaurant_1", "kakaoPlaceId": "12345678", "name": "맛있는 식당",
      "address": "서울 강남구", "latitude": 37.501, "longitude": 127.039,
      "category": "한식", "kakaoPlaceUrl": "https://place.map.kakao.com/12345678"
    },
    "confirmationType": "AUTO", "confirmedByNickname": null
  }]
}
```

자동 확정은 confirmedByNickname=null. 확정 결과를 삭제하면 히스토리에서도 제외된다.
과거 모달은 세션 상세·참여자·후보·결과 API를 그대로 사용한다.

## 8. 권한·오류

| 기능 | 권한 | 상태 |
| --- | --- | --- |
| 생성 | ACTIVE 팀원 | 제한 없음 |
| 조회 | ACTIVE 팀원 | 전체 |
| 이름 수정 | ACTIVE 팀원 | OPEN·CLOSED·CONFIRMED |
| 종료 시간 수정, 후보 추가·삭제, 참여 변경 | ACTIVE 팀원 | OPEN·마감 전 |
| 투표 삭제 | ACTIVE 팀원 | 전체 |
| 참여 상태 변경 | ACTIVE 팀원, 다른 팀원 대상 가능 | OPEN·마감 전 |
| 복수 선택 저장·취소 | 참여 중인 본인 | OPEN·마감 전 |
| 다시 시작 | 생성자 | OPEN/CLOSED |
| 수동 확정 | 생성자 | CLOSED |
| 확정 수정·삭제 | 생성자 | CONFIRMED |

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 요청 이름·시간·ID·배열 검증 실패 |
| 403 | NOT_TEAM_MEMBER | ACTIVE 팀원 아님 |
| 403 | VOTE_CREATOR_REQUIRED | 생성자 전용 |
| 404 | VOTE_NOT_FOUND | 해당 팀 투표 없음 |
| 404 | TEAM_MEMBER_NOT_FOUND | 참여 상태 변경 대상 없음/탈퇴 |
| 404 | VOTE_CANDIDATE_NOT_FOUND | 해당 투표 후보 없음 |
| 404 | DECISION_NOT_FOUND | 확정 결과 없음 |
| 404 | KAKAO_PLACE_NOT_FOUND | 검색으로 저장된 장소 없음 |
| 409 | VOTE_NOT_OPEN | CLOSED이거나 마감된 OPEN |
| 409 | VOTE_ALREADY_CONFIRMED | 확정 이후 금지된 변경 |
| 409 | VOTE_PARTICIPATION_REQUIRED | 불참/참여 행 없음 |
| 409 | VOTE_CANDIDATE_ALREADY_EXISTS | 같은 식당 후보 중복 |
| 409 | VOTE_NOT_CLOSED | CLOSED가 아닌 상태의 수동 확정 |
| 409 | INVALID_DECISION_CANDIDATE | 확정 가능한 후보 아님 |

```json
{
  "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "진행 중인 투표에서만 사용할 수 있습니다.",
  "instance": "/api/teams/team_1/votes/vote_1/ballots/me", "code": "VOTE_NOT_OPEN"
}
```

## 9. DB·트랜잭션·기존 데이터 전환

```text
UNIQUE(lunch_participants.session_id, lunch_participants.team_member_id)
UNIQUE(lunch_candidates.session_id, lunch_candidates.restaurant_id)
UNIQUE(lunch_ballots.session_id, lunch_ballots.candidate_id, lunch_ballots.team_member_id)
UNIQUE(lunch_decisions.session_id)
```

FK는 Long scalar ID. JPA 관계 매핑은 사용하지 않는다.
변경·정산은 세션 PESSIMISTIC_WRITE 잠금과 READ_COMMITTED 트랜잭션을 공유한다.
복수 선택 전체 교체, 불참+표 삭제, 후보+표 삭제, 재시작+표 삭제, 정산+자동 확정,
확정 삭제+CLOSED 전환은 각각 하나의 트랜잭션이다.

이전 단일 선택 스키마를 쓰는 MySQL은 새 앱 배포 전에
[전환 SQL](migrations/20261004-vote-ui-contract.sql)을 **앱을 멈추고 백업 후 한 번만** 적용한다.
예시 적용 순서(먼저 별도 백업 필요):

```sh
docker compose stop app
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' < docs/migrations/20261004-vote-ui-contract.sql
docker compose up --build -d app
```

Hibernate update가 이미 새 테이블과 name 컬럼을 만들었지만 예전 `title NOT NULL`이 남아 있는
혼합 스키마라면 [혼합 스키마 복구 SQL](migrations/20261004-vote-hybrid-schema-repair.sql)을 사용한다.
이 SQL은 예전 참여자·후보·선택·확정 테이블이 비어 있을 때만 진행하고, 기존 name 데이터는 유지한다.
현재 개발 Compose DB에는 백업 후 이 복구 SQL을 적용해 title 컬럼을 제거하고 CLOSED 상태를 허용했다.

빈 DB는 SQL이 필요 없으며 현재 Entity로 생성할 수 있다.
기존 name/title이 40자를 넘는 행은 SQL 실행 전에 처리해야 한다(무단 문자열 절단을 하지 않음).
기존 제목·참여·선택·확정 데이터를 새 컬럼/테이블로 옮기며 기존 확정 결과는 MANUAL로 해석한다.
기존 OPEN 종료 시간은 createdAt+3시간으로 채우므로 이미 지난 투표는 새 정산 정책을 따른다.
Hibernate `ddl-auto=update`만으로는 테이블 rename과 기존 2컬럼 ballot 유니크 제거를 처리할 수 없다.
MySQL DDL은 자동 커밋하므로 이 SQL 전체가 하나의 롤백 가능한 트랜잭션은 아니다.

동시성 테스트는 H2에서 애플리케이션 불변식을 검증한다. MySQL의 실제 동시 잠금 동작과 순수 레거시 전환 SQL은 별도 환경에서 검증해야 한다. 혼합 스키마 복구 SQL은 MySQL 8.4 임시 DB와 현재 개발 DB에서 검증했다.

## 10. 교체된 이전 경로

- `/votes/{voteId}/vote` → `/votes/{voteId}/ballots/me`
- `/votes/{voteId}/participants/me` → `/votes/{voteId}/participants/{targetTeamMemberId}`
- `/votes/{voteId}/confirm` → `/votes/{voteId}/decision`
- `/votes/history` → `/lunch-history`의 주간/월간 조회
- 생성은 closesAt 필수·name 선택. title도 생성 요청 별칭으로 허용하며 응답은 항상 name 사용.
- candidate 요청의 teamRestaurantId → kakaoPlaceId + source

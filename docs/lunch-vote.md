# 점심 투표

모든 endpoint는 기존 익명 세션의 ACTIVE 팀원 전용이며 ADMIN/MEMBER 권한 차이가 없다. 세션을 생성하지 않는다. 같은 팀에 OPEN 투표를 여러 개 만들 수 있다. 제목은 필수이며 최대 100자다.

| Method | Endpoint | 성공 |
| --- | --- | --- |
| POST | /api/teams/{teamId}/votes | 201 |
| GET | /api/teams/{teamId}/votes | 200 |
| GET | /api/teams/{teamId}/votes/{voteId} | 200 |
| PUT | /api/teams/{teamId}/votes/{voteId}/participants/me | 200 |
| POST | /api/teams/{teamId}/votes/{voteId}/candidates | 201 |
| PUT | /api/teams/{teamId}/votes/{voteId}/vote | 200 |
| POST | /api/teams/{teamId}/votes/{voteId}/confirm | 200 |
| GET | /api/teams/{teamId}/votes/history | 200 |

목록은 기본적으로 전체 투표를 반환하며 OPEN 우선, createdAt과 ID 내림차순이다. status=OPEN/CONFIRMED 필터를 지원한다. URL에는 숫자 ID와 응답의 team_ / vote_ 접두사 ID 모두 사용할 수 있다. 후보 추가와 투표/확정 요청의 ID 필드는 숫자다.

투표 생성 당시 ACTIVE 팀원을 participating=true로 초기화한다. 이후 가입자는 본인의 참여 여부를 먼저 설정한다. 참가자 행이 없으면 myParticipation=false이며 투표할 수 없다. participantCount는 해당 투표에서 participating=true인 행 수다. 참가자는 투표별로 저장하며 생성 당시 참가자와 닉네임을 조회한다. 불참자는 후보를 등록할 수 있지만 투표할 수 없다. 불참 전환은 해당 투표의 본인 VoteRecord만 삭제한다.

후보는 현재 팀에 등록된 Restaurant 원본 ID를 사용한다. 한 투표/식당 후보는 하나이며, 리뷰 평점은 그 팀의 팀 식당 리뷰에서 계산한다. 후보 득표수, 참가자 닉네임, 투표 요약, 확정 결과와 히스토리는 JPQL projection으로 조회한다. 히스토리는 ConfirmedMenu와 CONFIRMED 세션을 최신 confirmedAt 순으로 조회하며 별도 히스토리 테이블을 사용하지 않는다.

투표는 단일 선택 Upsert다. 재투표는 같은 VoteRecord의 후보와 updatedAt을 바꾸며 createdAt은 유지한다. 모든 변경 요청은 먼저 팀 범위로 세션 행을 PESSIMISTIC_WRITE 잠금한 후 OPEN을 검사한다. 변경 트랜잭션은 READ_COMMITTED를 사용해 세션 잠금 대기 뒤 집계와 중복 검사에서 이전 스냅샷을 읽지 않도록 한다. VoteRecord와 VoteParticipant의 변경 조회도 쓰기 잠금을 사용해 기존 행을 확인한다. UNIQUE 제약을 후보(세션/식당), 참가자(세션/팀원), 투표 기록(세션/팀원), 확정 결과(세션)에 둔다.

최다 득표가 아닌 후보도 확정할 수 있다. 확정 결과 생성과 세션 상태 전이는 한 트랜잭션이며, 이후 후보 추가·참여 변경·투표 변경·재확정은 모두 409 VOTE_ALREADY_CONFIRMED다. 다른 투표의 후보는 404 VOTE_CANDIDATE_NOT_FOUND, 다른 팀의 투표는 404 VOTE_NOT_FOUND다. 오류는 기존 ProblemDetail 형식이다.

마지막 관리자 탈퇴로 팀을 삭제할 때 확정 결과 → 투표 기록 → 참가자 → 후보 → 세션도 일괄 정리한다. 이는 팀 삭제의 내부 정리이며 투표 삭제/취소 endpoint는 제공하지 않는다. 공유 Restaurant와 User는 유지한다.

H2 integration 테스트는 두 요청을 별도 스레드/트랜잭션에서 동시에 시작해 VoteRecord와 ConfirmedMenu가 각각 한 행만 남는지 검증한다. H2 통과는 애플리케이션 불변식 검증이며 MySQL 운영 동시성 보장은 별도 MySQL 환경에서 검증해야 한다.

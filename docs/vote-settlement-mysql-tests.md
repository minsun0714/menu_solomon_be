# 투표 정산 MySQL 회귀 테스트

기존 구현의 데드락을 재현하는 red 단계 테스트를 먼저 작성한 뒤, Repository/Service를 최소 수정해 green 단계로 전환했다.

## 수정된 정산 흐름

1. `LunchVoteSessionRepository.findDueIdsForTeam(teamId, now)`는 ID만 일반 SELECT로 조회한다. 잠금 annotation이 없다.
2. 후보가 있을 때 `findAllForUpdate(ids)`로 ID 조건만 포함한 PK locking read를 수행한다. `ORDER BY id` 및 `PESSIMISTIC_WRITE`를 유지한다.
3. `VoteServiceImpl.getVotes()`는 잠금 조회 결과를 기존 `settle(vote, now)`에 전달한다.
4. `settle()`의 기존 `isDue(now)` 검사로 탐색 이후 이미 CLOSED/CONFIRMED가 된 투표를 건너뛴다.
5. 트랜잭션 경계, Domain behavior, 스케줄러의 PK 잠금 정산은 유지한다. 후보가 없으면 잠금 조회를 생략한다.

스키마/인덱스 변경은 없다. 기존 조건 검색의 locking read만 ID 탐색과 PK 잠금으로 분리했다.

테스트: `app/src/test/java/com/menusolomon/vote/integration/VoteSettlementMySqlTest.java`

테스트 본문은 Spring Data JPA Repository와 `TransactionTemplate`을 사용한다.
PK 잠금은 기존 `sessions.findForUpdate()`, 상태 변경은 `vote.close()` 및 `sessions.flush()`로 수행한다.
반복 초기화도 `vote.restart()`로 처리하며 불필요한 Decision 삭제는 제거했다.
MySQL 버전·기존 인덱스 구성·잠금 대기 관찰·EXPLAIN은 테스트 전용
`VoteSettlementProbeRepository`의 native query로 분리했다. 프로덕션 Repository에 진단 메서드는 추가하지 않았다.

## 실행

실제 MySQL 8.4 전용 DB를 사용한다. Testcontainers/H2를 사용하지 않는다.
`MYSQL_VOTE_TEST_URL`이 `/vote_lock_test` DB를 지정할 때만 실행한다.
Hibernate `create-drop`이므로 다른 앱이 사용하는 DB에 연결하면 안 된다.
테스트 계정은 `performance_schema.data_locks`, `data_lock_waits` 조회 권한이 필요하다.

```bash
docker run -d --name menu-solomon-vote-mysql-test \
  -p 127.0.0.1:13307:3306 \
  -e MYSQL_ROOT_PASSWORD=vote-test-only \
  -e MYSQL_DATABASE=vote_lock_test mysql:8.4
```

MySQL이 준비된 뒤 실행한다. 아래 비밀번호는 격리된 테스트 컨테이너 전용 값이다.

```bash
MYSQL_VOTE_TEST_URL='jdbc:mysql://127.0.0.1:13307/vote_lock_test?connectionTimeZone=UTC' \
MYSQL_VOTE_TEST_USER=root \
MYSQL_VOTE_TEST_PASSWORD=vote-test-only \
./gradlew :app:test --tests '*VoteSettlementMySqlTest' --no-daemon
```

환경 변수를 지정하지 않은 일반 테스트 실행에서는 이 클래스가 skip된다.
종료 후 `docker stop menu-solomon-vote-mysql-test`로 테스트 DB를 중지한다.

## 검증과 현재 결과

실제 MySQL 8.4 회귀 테스트 5개 모두 통과했고, MySQL 테스트를 포함한 전체 425개 테스트도 실패·skip 없이 통과했다.

| 검증 | 수정 전 | 수정 후 |
| --- | --- | --- |
| A: 동일 voteId 두 요청 정산 → 둘 다 정상 종료, Decision 1건, CONFIRMED | 통과 | 통과 |
| B: REPEATABLE READ에서 후보 탐색 후 다른 트랜잭션 확정 → PK locking read는 최신 CONFIRMED 확인, 재정산 생략 | 통과 | 통과 |
| C: PK 보유 트랜잭션의 status UPDATE와 실제 getVotes 정산 경쟁 | 실패: 실제 MySQL deadlock | 통과: 5회 반복 |
| D: EXPLAIN으로 후보 탐색 인덱스 및 PK 접근 확인 | 통과 | 통과: 여러 ID locking read |
| 서비스 SQL 계약: 후보 탐색은 ID-only consistent read, 이후 PK-only locking read | 실패: 조건 검색에 FOR UPDATE | 통과 |

A는 PK 잠금을 먼저 보유해 두 요청을 대기시키고, MySQL lock wait 두 건을 확인한 뒤 잠금을 해제한다.
B는 일반 SELECT로 snapshot을 만들고 latch로 다른 트랜잭션의 커밋을 기다린 뒤 PK locking read를 수행한다.
C는 SQL StatementInspector의 latch로 실제 locking SQL 진입을 확인하고, performance_schema에서 잠금 대기를 관찰한 뒤 PK 보유 트랜잭션의 UPDATE를 진행한다.
단순 sleep으로 경쟁 시점을 추측하지 않는다. 잠금 관찰의 bounded polling 간격은 5ms다.
C는 수정 전 첫 데드락에서 실패했고, 수정 후 동일 경쟁을 5회 반복해 모두 정상 종료한다.

## 실행 SQL과 EXPLAIN

수정 전 Hibernate가 실행한 충돌 SELECT(별칭만 간소화):

```sql
SELECT id, closes_at, created_at, created_by_team_member_id, name, status, team_id
FROM lunch_vote_sessions
WHERE team_id = ? AND status = 'OPEN' AND closes_at <= ?
ORDER BY id
FOR UPDATE;
```

경쟁 트랜잭션:

```sql
SELECT id FROM lunch_vote_sessions WHERE id = ? FOR UPDATE;
UPDATE lunch_vote_sessions SET closes_at = ?, name = ?, status = 'CLOSED' WHERE id = ?;
```

테스트 MySQL InnoDB 기록에서 다음 순환 대기를 확인했다.

- getVotes SELECT: `idx_vote_session_team_status` X-lock 보유 → PRIMARY X-lock 대기
- status UPDATE: PRIMARY X-lock 보유 → `idx_vote_session_team_status` X-lock 대기
- MySQL은 SELECT 트랜잭션을 롤백했고 `CannotAcquireLockException`의 원인은 `MySQLTransactionRollbackException: Deadlock found when trying to get lock`이었다.

수정 후 서비스에서 실제 실행하는 SQL(별칭만 간소화):

```sql
SELECT id FROM lunch_vote_sessions
WHERE team_id = ? AND status = 'OPEN' AND closes_at <= ?
ORDER BY id;

SELECT id, closes_at, created_at, created_by_team_member_id, name, status, team_id
FROM lunch_vote_sessions
WHERE id IN (...) ORDER BY id FOR UPDATE;
```

`StatementInspector`로 서비스 실행 SQL을 수집해 후보 탐색에 `FOR UPDATE`가 없고,
locking query에 status/team 조건이 없음을 검증한다.
후보 탐색 SQL을 EXPLAIN한 결과:

```sql
SELECT id FROM lunch_vote_sessions
WHERE team_id = ? AND status = 'OPEN' AND closes_at <= ?;
```

`key=idx_vote_session_team_status`, `type=ref`, `Extra=Using index condition; Using where`.
테스트는 기존 배포 DB와 동일한 `(team_id,status,created_at)` 인덱스를 생성한다.
PK는 secondary leaf에 포함되지만 `closes_at`은 포함되지 않으므로 **전체 조건을 covering하지 않는다**.
일반 consistent read이므로 clustered index 접근 여부와 무관하게 X-lock을 잡지 않는 방향은 유효하다.

PK locking read EXPLAIN:

```sql
SELECT * FROM lunch_vote_sessions WHERE id IN (?) ORDER BY id FOR UPDATE;
```

`key=PRIMARY`, 여러 ID에서는 `type=range`. B는 실제 프로덕션 Repository의 ID 탐색 및 PK locking read를 사용한다.
D는 단일 ID뿐 아니라 여러 ID에 대한 IN 쿼리의 실행계획도 확인한다.
실행계획은 테스트 fixture 기준으로 확인한 값이며 데이터 규모에 따라 optimizer 선택은 달라질 수 있다.

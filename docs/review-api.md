# 리뷰 API와 식당 집계

기존 익명 세션의 ACTIVE 팀원만 접근할 수 있다. 탈퇴한 팀원과 비팀원은 403 NOT_TEAM_MEMBER이며, 요청 팀에 속하지 않은 팀 식당은 404 TEAM_RESTAURANT_NOT_FOUND다. 세션을 자동 발급하지 않는다.

- `PUT /api/teams/{teamId}/restaurants/{teamRestaurantId}/reviews/me`: rating(1~5), content(필수, blank 불가, 최대 200자)를 저장한다. 신규는 201, 기존 리뷰 덮어쓰기는 200이며 ID와 createdAt은 유지한다.
- `GET /api/teams/{teamId}/restaurants/{teamRestaurantId}/reviews`: updatedAt 내림차순, 동점은 리뷰 ID 내림차순으로 조회한다. 작성자 닉네임과 현재 팀원 기준 isMine을 포함한다.
- `DELETE /api/teams/{teamId}/restaurants/{teamRestaurantId}/reviews/me`: 본인의 리뷰를 삭제하고 204를 반환한다. 없으면 404 REVIEW_NOT_FOUND다.

오류는 기존 ProblemDetail 형식이다. 도메인 검증과 요청 Bean Validation을 모두 적용하고, DB UNIQUE(team_restaurant_id, team_member_id)를 둔다. 변경 요청은 팀 식당과 리뷰를 같은 순서로 잠근다. 조회 시점을 앞당긴 트랜잭션에서도 최신 리뷰를 확인하도록 리뷰 조회에 쓰기 잠금을 적용한다.

식당 목록과 상세는 averageRating, reviewCount, latestReview를 포함한다. 리뷰가 없으면 averageRating과 latestReview는 null, reviewCount는 0이다. latestReview는 updatedAt이 가장 최근인 리뷰이며, 같은 시각이면 리뷰 ID가 큰 행을 선택한다. 전체 리뷰 목록은 별도 리뷰 API로 조회한다.

목록의 totalReviewCount는 검색/카테고리 필터와 무관하게 요청 팀 전체 리뷰 수다. RATING_DESC는 평균 별점 내림차순, 동점은 팀 식당 createdAt과 ID 내림차순이며 리뷰 없는 식당은 뒤에 배치한다. 집계와 최신 리뷰 선택은 DB query에서 수행하고 행별 Repository 조회는 하지 않는다. 팀 식당 삭제 시 해당 리뷰도 삭제하며 식당 원본은 유지한다.

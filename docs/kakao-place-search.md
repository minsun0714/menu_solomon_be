# 카카오 장소 검색과 팀 식당 등록

프로젝트 루트의 `.env`에 `KAKAO_REST_API_KEY=`를 추가했다. 카카오 REST API 키를 값으로 설정하면 Spring 설정에서 읽는다. `.env`는 Git에서 제외하며, 배포 환경에서는 같은 이름의 환경 변수로 설정한다.

`GET /api/places/search?query=역삼%20한식&page=1&size=5`는 [카카오 공식 키워드 장소 검색 API](https://developers.kakao.com/docs/ko/local/dev-guide#search-by-keyword)를 호출한다. 검색어는 필수이며, page는 1~45, size는 1~15다. 기본값은 각각 1과 5다. 검색 결과의 도로명 주소를 우선 사용하고, 없으면 지번 주소를 사용한다. 좌표는 y를 위도, x를 경도로 변환한다. 음식점 카테고리는 카카오 분류의 음식점 바로 아래 항목(한식, 양식 등)을 사용한다. totalCount는 전체 검색 건수, totalPages는 실제 노출 가능한 pageable_count를 기준으로 계산한다.

카카오 공식 API에는 장소 ID만 받는 상세 조회 endpoint가 없다. 따라서 서버가 받은 검색 결과를 공유 Restaurant 테이블에 보관하고, 팀 식당 등록은 해당 kakaoPlaceId의 정보를 재사용한다. 검색 전에 임의의 장소 ID를 등록하면 404 KAKAO_PLACE_NOT_FOUND다. 검색은 팀 식당 연결을 만들지 않으며 익명 사용자나 세션도 생성하지 않는다. 외부 호출은 DB 트랜잭션을 시작하기 전에 수행한다.

`POST /api/teams/{teamId}/restaurants` 요청은 `{ "kakaoPlaceId": "검색 결과의 ID" }` 형식을 유지한다. 팀 식당의 등록·조회·삭제는 기존 세션의 ACTIVE 팀원만 가능하다. 팀 식당을 삭제해도 원본 Restaurant는 남는다.

목록의 totalCount와 categoryCounts는 검색·카테고리 필터 적용 전 팀 전체 식당을 기준으로 반환한다. 검색과 정렬은 DB에서 수행한다. RATING_DESC는 리뷰의 평균 별점 내림차순이며 리뷰가 없는 식당은 뒤에 배치한다. 동점은 최근 등록 시각, 팀 식당 ID 내림차순으로 정렬한다.

REST API 키가 비어 있거나 카카오 호출·응답 처리가 실패하면 502 KAKAO_API_ERROR ProblemDetail을 반환한다. 테스트는 실제 키나 외부 네트워크 없이 MockRestServiceServer로 HTTP 요청과 응답을 검증한다.

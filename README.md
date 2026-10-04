# menu_solomon_be
메뉴 솔로몬 백엔드

현재 구현 기준 [API 명세](docs/api-spec.md).

## Docker로 실행

`.env.example`을 참고해 `.env`에 환경변수를 설정한다. 기존 `.env`가 있으면
MySQL 항목만 추가하고 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`를 입력한다.
카카오 검색을 사용하려면 `KAKAO_REST_API_KEY`도 입력한다.

```sh
docker compose up --build -d
docker compose logs -f app
```

app은 `http://localhost:8080`에서 접근한다. MySQL은 Compose 내부에서만
접근하며 데이터는 `mysql-data` 볼륨에 보관한다. app은 MySQL 연결 확인 후 시작한다.
Java 21 이미지에서 Gradle Wrapper로 실행 JAR를 빌드한다.

이 Compose는 개발 실행용이며 JPA `ddl-auto=update`로 스키마를 생성·갱신한다.

```sh
docker compose down
```

위 종료 명령은 MySQL 데이터 볼륨을 유지한다.

# menu_solomon_be
메뉴 솔로몬 백엔드

현재 구현 기준 [API 명세](docs/api-spec.md).

## 개발 환경 실행

`.env.example`을 참고해 `.env`에 환경변수를 설정한다. 기존 `.env`가 있으면
MySQL 항목만 추가하고 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`를 입력한다.
카카오 검색을 사용하려면 `KAKAO_REST_API_KEY`도 입력한다.

```sh
docker compose -f compose-dev.yml up --build -d
docker compose -f compose-dev.yml logs -f app
```

app은 `http://localhost:8080`에서 접근한다. MySQL은 Compose 내부에서만
접근하며 데이터는 `mysql-data` 볼륨에 보관한다. app은 MySQL 연결 확인 후 시작한다.
Java 21 이미지에서 Gradle Wrapper로 실행 JAR를 빌드한다.

이 Compose는 개발 실행용이며 JPA `ddl-auto=update`로 스키마를 생성·갱신한다.

```sh
docker compose -f compose-dev.yml down
```

위 종료 명령은 MySQL 데이터 볼륨을 유지한다.

## 배포 환경 실행

`compose.yml`은 GHCR의 app 이미지와 Caddy를 실행한다. MySQL은 포함하지 않으므로
외부 DB 연결 정보를 `.env`에 설정한다.

```dotenv
APP_IMAGE=ghcr.io/사용자/menu-solomon:latest
API_DOMAIN=api.example.com
FRONTEND_ORIGIN=https://example.com
CORS_ALLOWED_ORIGINS=https://example.com
KAKAO_REST_API_KEY=실제키
SPRING_DATASOURCE_URL=jdbc:mysql://DB호스트:3306/menu_solomon?connectionTimeZone=UTC
SPRING_DATASOURCE_USERNAME=DB사용자
SPRING_DATASOURCE_PASSWORD=DB비밀번호
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_JPA_PROPERTIES_HIBERNATE_JDBC_TIME_ZONE=UTC
```

`APP_IMAGE`는 실제 게시한 이미지 주소로 변경한다. DB 스키마는 배포 전에 준비한다.
`API_DOMAIN`의 DNS를 서버에 연결하고 서버의 80/443 포트를 개방한다.
Caddy가 HTTPS 인증서를 자동 발급하며 app의 8080 포트는 외부에 공개하지 않는다.
비공개 GHCR 이미지는 먼저 `docker login ghcr.io`로 인증한다.

```sh
docker compose pull
docker compose up -d
docker compose logs -f app caddy
```

이미지 갱신 시에도 `pull` 후 `up -d`를 실행한다. 배포 서버에서는 로컬 빌드를 하지 않는다.

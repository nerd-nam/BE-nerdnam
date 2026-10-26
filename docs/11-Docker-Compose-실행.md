# 11. 다른 개발자 장비에서 Docker Compose로 실행

## 처음 실행

이 레포와 Docker Engine/Desktop + Compose가 필요하다. 호스트 Java·Gradle·AI 레포는 기본 실행에 필요 없다. 개인 장비의 개발·연결 테스트용이며 localhost에만 포트를 연다.

```bash
cd BE-nerdnam
docker compose up
```

처음에는 Java 21 이미지와 Gradle 의존성을 내려받아 실행 jar를 만든다. PostgreSQL·Redis 상태 확인이 성공하면 API가 시작되고 Flyway가 테이블·로컬 시드 회원을 준비한다. 기존 DB 볼륨은 그대로 사용한다.

```bash
docker compose ps
curl http://localhost:8080/actuator/health
curl -i http://localhost:8080/api/me
```

첫 응답은 `{"status":"UP"}`, 로그인 전 두 번째 응답은 `401 UNAUTHORIZED`가 정상이다. `/api/me`의 404는 정상 결과가 아니다. 코드 변경 뒤에는 `docker compose up --build -d`로 다시 빌드한다.

## 소셜 로그인과 FE

`.env`가 없을 때 `.env.example`을 복사하고 본인이 발급한 키를 입력한다. 기존 `.env`가 있으면 필요한 항목만 추가한다. `.env`는 Git과 Docker 빌드 컨텍스트에서 제외된다.

```dotenv
NERDNAM_AUTH_MODE=session
GOOGLE_CLIENT_ID=발급받은_ID
GOOGLE_CLIENT_SECRET=발급받은_보안_비밀번호
KAKAO_CLIENT_ID=발급받은_ID
KAKAO_CLIENT_SECRET=발급받은_보안_비밀번호
WEB_ORIGIN=http://localhost:3000
```

Google만 테스트할 때는 카카오 값을 비워 둬도 된다. 키가 없는 제공자는 실제 로그인을 완료할 수 없다. Compose는 명시한 `.env` 변수를 API에 전달한다. 호스트의 IntelliJ·`bootRun`은 환경변수를 따로 설정해야 한다.

제공자 콘솔에 등록할 콜백 주소:

```text
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/kakao
```

FE에는 `API_ORIGIN=http://localhost:8080`을 넣는다. 기본 로그인 성공 주소는 `http://localhost:3000/`다. FE 프록시를 거쳐도 콜백은 브라우저가 접속하는 BE 주소로 고정했다. BE의 `/oauth2/authorization/google` 또는 `/oauth2/authorization/kakao`에서 제공자 화면으로 이동하는지 확인한다.

`.env` 변경 후 `docker compose up -d`로 컨테이너를 다시 구성한다. `docker compose restart`는 기존 환경변수로 다시 시작하므로 새 키를 읽지 않는다.

## 로그인 없이 API 테스트

```bash
NERDNAM_AUTH_MODE=header docker compose up -d api
curl -H 'X-Member-Id: 1' http://localhost:8080/api/me
```

로컬 시드 회원 1번을 사용한다. Compose는 `local` 프로필을 명시하므로 이 개발용 모드를 허용한다. 이 모드에는 소셜 로그인 시작 주소와 CSRF API가 없다. FE 로그인 확인으로 돌아갈 때는:

```bash
NERDNAM_AUTH_MODE=session docker compose up -d api
```

## AI 서버 연결

기본 `NERDNAM_AI_STUB=true`는 Java 내부 가짜 AI다. 모델 품질·실제 FastAPI 왕복을 검증하는 설정은 아니다. 외부 AI를 붙이려면 `.env`를 바꾸고 API를 다시 구성한다.

```dotenv
NERDNAM_AI_STUB=false
NERDNAM_AI_BASE_URL=http://host.docker.internal:8000
```

`host.docker.internal`은 컨테이너에서 호스트 PC로 가는 주소다. API 컨테이너 안의 `localhost`는 API 컨테이너 자신이다. Linux에서도 주소를 찾도록 `host-gateway`를 설정했다. AI 서버는 Docker에서 접근 가능한 인터페이스에서 수신해야 하며, Linux에서는 `127.0.0.1`에만 바인딩한 서버에 연결되지 않는다.

FastAPI의 `AI_MODE=stub`은 가짜 응답, `AI_MODE=real`은 실제 모델 모드다. 실제 모델을 Mac에서 실행할 때는 AI README와 SPEC 5.5에 따라 읽기 제한도 별도로 조정해야 한다. AI 컨테이너·모델 다운로드는 이 Compose에 포함하지 않는다.

## 포트 충돌과 IDE 개발

IntelliJ나 터미널의 BE가 8080을 쓰고 있으면 먼저 그 서버를 멈춘다. 동시에 필요하다면 `.env`에서 호스트 포트를 바꾼다.

```dotenv
API_PORT=8090
POSTGRES_PORT=5433
REDIS_PORT=6380
```

FE 주소는 `API_ORIGIN=http://localhost:8090`, OAuth 콜백은 `http://localhost:8090/login/oauth2/code/google`로 맞춘다. 컨테이너끼리는 계속 `postgres:5432`, `redis:6379`로 연결된다. `API_PUBLIC_URL`을 따로 지정했다면 콜백은 그 값을 우선한다. 호스트 DB 클라이언트·`bootRun`의 연결 포트는 별도로 맞춰야 한다.

IDE에서 API를 실행할 때는:

```bash
docker compose stop api
docker compose up -d postgres redis
./gradlew bootRun
```

## 종료와 확인

```bash
docker compose logs --tail=100 api
docker compose logs --tail=100 postgres redis
docker compose down
```

`down`은 PostgreSQL 볼륨을 보존한다. `down -v`는 DB 데이터까지 지우므로 일반 종료에는 사용하지 않는다. Redis는 기존 개발 구성대로 영구 볼륨이 없어 재생성되면 다시 로그인해야 한다.

자동 테스트는 호스트 Java 21 + `./gradlew test`로 실행한다. Testcontainers가 테스트 DB·Redis를 따로 만든다. 이미지 빌드는 `bootJar`만 실행하므로 빌드 컨테이너에 Docker 소켓을 넘길 필요가 없다.

공식 참고: [Compose 시작 순서](https://docs.docker.com/compose/how-tos/startup-order/), [환경변수와 .env](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/), [컨테이너 네트워크](https://docs.docker.com/compose/how-tos/networking/).

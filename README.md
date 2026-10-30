# dagong-auth

맛다공·여다공 공통 계정 및 SSO 서버. 프런트는 React + TypeScript, 백엔드는 Java 21 / Spring Boot입니다.

계정과 OAuth 인가 정보는 JDBC로 읽고 저장하며, DB 구조와 칼럼 설명은 Flyway SQL에서 관리합니다.

## 구조

```text
dagong-auth/
├─ backend/
│  ├─ build.gradle
│  └─ src/
│     ├─ main/
│     │  ├─ java/com/dagong/auth/
│     │  │  ├─ account/     공통 계정·소셜 연결·서비스 가입·계정 상태 확인
│     │  │  └─ security/    인증 설정, 토큰 발급 규칙, 로그인·세션 컨트롤러
│     │  └─ resources/
│     │     ├─ application.yml
│     │     └─ db/migration/
│     └─ test/            내장 PostgreSQL로 Flyway 스키마와 토큰 발급 규칙 검증
├─ frontend/
│  ├─ src/
│  │  ├─ main.tsx       React 시작
│  │  ├─ App.tsx
│  │  ├─ pages/LoginPage.tsx
│  │  ├─ components/    LoginHero, SocialLoginButton, AccountFooter, NoticeToast
│  │  └─ styles/login.css
│  ├─ public/assets/   로그인 화면 이미지
│  └─ package.json
├─ assets/             Kakao Developers 앱 아이콘 원본
├─ .run/               공유 IntelliJ 실행 구성
├─ build.gradle        루트 집계
├─ settings.gradle
└─ gradlew.bat
```

frontend/index.html은 React 진입 HTML이며 화면 코드는 LoginPage.tsx에 있습니다. Kakao Developers에 등록하는 앱 아이콘은 `assets/kakao-developers-app-icon.png`에 보관하며, 웹 화면의 정적 이미지와 분리해 번들에 포함하지 않습니다.

## 처음 실행

1. IntelliJ에서 루트 build.gradle을 다시 불러옵니다. Java SDK는 21입니다.
2. Node.js 24.22.1 이상(24 LTS 계열)을 설치하고 frontend 폴더에서 npm ci를 실행합니다. 사용할 버전은 `frontend/.nvmrc`에 기록합니다.
3. PostgreSQL을 실행하고 루트의 `.env`를 `.env.example`에서 복사해 DB 접속 정보, 고유한 서명 키 비밀번호, Kakao REST API 키와 Client Secret을 설정합니다. Kakao Developers에서 카카오 로그인을 켜고 아래 Redirect URI를 등록합니다. 그 비밀번호로 로컬 서명 키를 한 번 생성합니다.

   ```text
   http://localhost:4840/login/oauth2/code/kakao
   ```

   ```powershell
   New-Item -ItemType Directory "$env:USERPROFILE\.dagong-auth" -Force
   $keyPassword = Read-Host "AUTH_KEYSTORE_PASSWORD"
   keytool -genkeypair -alias dagong-auth-signing -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore "$env:USERPROFILE\.dagong-auth\auth-signing.p12" -storepass $keyPassword -keypass $keyPassword -dname "CN=Dagong Auth Development" -validity 3650
   ```

   최초 실행 시 Flyway가 V1부터 현재 버전까지 스키마를 순서대로 적용합니다.
4. Gradle 창에서 backend → Tasks → application → bootRun을 실행합니다.
   터미널에서는 루트에서 .\gradlew.bat :backend:bootRun 을 실행합니다. 인증 API는 5840 포트에서 시작합니다.
5. React 화면 개발 서버를 실행한 뒤 http://localhost:4840/login 에 접속합니다.

processResources가 React 빌드를 자동 실행하고 결과를 backend/build/resources/main/static/auth에 포함합니다. IntelliJ Java 클래스 직접 실행보다 Gradle bootRun을 권장합니다. 직접 실행 시에는 먼저 :backend:classes를 실행하고 backend 모듈 classpath를 사용하세요.

백엔드 테스트는 내장 PostgreSQL을 내려받아 실행하므로 Docker나 공유 DB가 필요 없습니다. React 빌드를 건너뛰려면 루트에서 `.\gradlew.bat :backend:test -x buildFrontend`를 실행합니다.

## 화면 개발

프로젝트 루트 또는 frontend에서 npm run dev 실행 후 http://localhost:4840/login 접속합니다. React 화면 수정은 즉시 반영됩니다.
화면은 4840, 인증 API 서버는 5840에서 실행합니다. Vite가 OAuth 요청과 Kakao callback 경로를 인증 API 서버로 전달합니다. Kakao Developers의 callback 주소는 http://localhost:4840/login/oauth2/code/kakao 입니다.

## 배포

frontend에서 npm ci 실행 후 루트에서 .\gradlew.bat :backend:bootJar 실행.
backend/build/libs/dagong-auth.jar에 React 빌드가 포함됩니다.
Dockerfile도 이 JAR를 사용합니다. 로컬 화면 개발에는 Vite 서버(4840)를 사용하며, 통합 JAR는 자체 정적 화면도 제공합니다.
프런트 빌드 결과를 src/main/resources에 수동 복사하지 않습니다.

## 현재 기능 범위

- React 로그인 화면: 시안의 로고·배경·캐릭터가 이어지는 상단, 네이버·카카오·Apple 버튼.
- Kakao OAuth2 로그인은 공통 계정을 생성하거나 기존 계정에 연결합니다. REST API 키와 Client Secret은 루트 `.env`에 설정하고, Kakao Developers에 위 Redirect URI를 등록해야 실제 인증할 수 있습니다.
- 네이버·Apple 버튼은 준비 중 안내를 표시합니다. 맛다공은 `/auth/callback`에서 인가 코드를 토큰으로 교환하고 서비스 가입 또는 홈으로 이어집니다.
- /login?error, /login?logout 안내를 React 상태로 표시합니다.
- 계정·소셜 연결·서비스 이용 현황·관리자 조치·권한·OAuth 인가/동의 저장소는 Flyway 스크립트로 관리합니다.
- 개발 OAuth client_id: matdagong-web-dev, Authorization Code + PKCE, redirect_uri 기본값: http://localhost:4845/auth/callback.
- client 설정은 `application.yml`의 `dagong.auth.matdagong-client-id` / `dagong.auth.matdagong-redirect-uri`로 변경 가능합니다. 설정한 redirect URI와 access token 수명은 서버 시작 시 개발용 client 항목에 반영됩니다.
- access token 수명 기본값은 30분(`PT30M`)입니다. 브라우저 공개 클라이언트에는 refresh token을 발급하지 않으므로, 개발 PC에서만 `.env`의 `AUTH_ACCESS_TOKEN_TTL`(ISO-8601, 예: `PT8H`)로 늘립니다.
- access token에는 client_id를 audience로, `account_roles`의 활성 권한(ADMIN, SUPER_TASTER)을 `roles` 클레임으로 넣습니다.
- 차단(BLOCKED)·탈퇴 처리 중(DELETION_PENDING)·탈퇴(DELETED) 계정은 Kakao 로그인, SSO 세션으로 받는 인가 코드, 토큰 발급, 맛다공 가입 API(`/api/v1/matdagong/membership`)에서 모두 거부합니다. 이미 발급된 access token은 만료 전까지 서명상 유효하므로, 즉시 차단이 필요한 API는 맛다공 가입 API처럼 계정 상태를 따로 확인합니다.
- 클라이언트·인가·동의 저장소는 PostgreSQL을 사용합니다. 개발용 RSA 서명 키는 사용자 홈의 PKCS12 파일에서 읽으므로 재시작 뒤에도 같은 키로 토큰을 검증할 수 있습니다.
- 운영 배포에서는 `AUTH_KEYSTORE_PATH`와 `AUTH_KEYSTORE_PASSWORD`를 운영 비밀 관리 체계에 맞게 설정해야 합니다.

## DB 경계

공통 인증 서버만 dagong_auth DB에 접근합니다. 맛다공·여다공은 공통 사용자 ID와 인증 서버가 발급한 토큰으로 연동합니다.

## 여러 PC에서 공유 개발 DB 사용

집 컴퓨터에서 이어서 설정할 때는 [집 컴퓨터 개발 환경 설정 안내](docs/home-development-setup.md)를 따라갑니다. 이 안내는 저장소와 함께 Git으로 받을 수 있으며, 회사 컴퓨터의 비밀 파일을 복사할 필요가 없습니다.

Flyway는 Spring Boot에 연결한 데이터베이스 마이그레이션 도구입니다. Git은 SQL 파일을 보관하고, Flyway는 앱 시작 시 `backend/src/main/resources/db/migration`에서 아직 적용하지 않은 버전을 순서대로 실행합니다. 적용 기록은 `flyway_schema_history`에서 확인합니다. 이미 적용된 SQL은 수정하지 않고 새 버전 파일을 추가합니다.

회사와 집에서 같은 테스트 데이터를 이어서 사용하도록 Neon 무료 프로젝트 `dagong`의 개발 데이터베이스를 연결했습니다. 프로젝트는 싱가포르 리전이고, DB는 `dagong_auth_dev`, 회사 PC의 앱 계정은 `dagong_auth_app`입니다. Spring Boot와 Flyway가 사용하는 직접 연결 주소는 다음과 같습니다. 각 PC의 루트 `.env`에 접속 정보를 설정하고, 실제 비밀번호는 Git에 올리지 않습니다.

```text
DB_URL=jdbc:postgresql://<Neon 직접 연결 호스트>:5432/dagong_auth_dev?sslmode=require
DB_USERNAME=dagong_auth_app
DB_PASSWORD=<Neon에서 받은 앱 계정 비밀번호>
```

각 PC의 비밀 값은 해당 PC에서 직접 설정합니다. 회사 PC의 `.env`나 Neon 자격 증명 다운로드 파일을 Git에 추가하거나 채팅에 붙여 넣지 않습니다. 집 PC 전용 DB 계정 설정과 `.env` 작성 방법은 위 안내 문서를 참고합니다.

기존 원격 DB에 연결하면 Flyway가 적용을 마친 버전은 건너뜁니다. 초기 스키마 재정의는 V2에 기록돼 있으며, 이후 정리는 데이터를 보존하는 별도 마이그레이션으로 적용합니다. Neon 연결 문자열은 TLS를 요구하며, 개발용 테스트 데이터만 저장합니다.

## 계정 데이터

- `accounts`: 공통 사용자 UUID, 선택 이메일, 계정 상태, 로그인·탈퇴·차단 시각을 보관합니다. 이메일 없이 소셜 계정을 만들 수 있습니다.
- `social_identities`: 제공자와 제공자 사용자 ID를 연결합니다. 같은 제공자 계정은 한 계정에만 연결되며, 다른 제공자는 별도 계정으로 가입할 수 있습니다.
- `account_admin_actions`: 관리자 차단·해제와 탈퇴 처리 이력을 기록할 테이블입니다. 관리자 기능을 도입할 때 사용하며 현재는 기록하는 코드가 없습니다.
- `account_services`: 계정의 맛다공·여다공별 가입 상태, 탈퇴 상태, 마지막 이용 시각을 기록합니다. 현재는 맛다공 가입과 최근 이용 시각만 기록하며, 여다공 가입과 관리자용 서비스별 이용 현황 조회는 이후에 연결합니다.
- `account_roles`: 관리자(ADMIN)·슈퍼 맛객(SUPER_TASTER) 권한과 부여·회수 이력을 보관합니다. 토큰 발급 시 활성 권한을 읽으며, 권한 부여·회수 화면은 이후 관리자 서비스에서 제공합니다.
- `oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent`: 서비스 클라이언트, 발급된 인가·토큰 상태, 사용자 동의를 저장해 서버 재시작 뒤에도 SSO 상태를 유지합니다.
- 향후 본인 확인을 위해 전화번호와 CI 해시 필드를 비워 두었습니다. 본인 확인 연동이나 CI 값 저장은 현재 활성화되지 않았습니다. CI 원문은 저장하지 않습니다.

이메일은 선택 연락처이며 로그인 식별자가 아니므로, 같은 이메일을 가진 소셜 계정도 별도 계정으로 만들 수 있습니다. 한 공통 계정이 두 서비스 모두에 가입하면 `account_services`에 서비스별 행이 각각 생깁니다. 테이블·칼럼 설명은 PostgreSQL `COMMENT` 메타데이터에 기록됩니다.

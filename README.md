# dagong-auth

맛다공·여다공 공통 계정 및 SSO 서버. 프런트는 React + TypeScript, 백엔드는 Java 21 / Spring Boot입니다.

## 구조

```text
dagong-auth/
├─ backend/
│  ├─ build.gradle
│  └─ src/main/
│     ├─ java/com/dagong/auth/
│     │  ├─ account/     기존 계정 엔티티
│     │  └─ security/    인증 설정과 로그인 진입 컨트롤러
│     └─ resources/
│        ├─ application.yml
│        └─ db/migration/
├─ frontend/
│  ├─ src/
│  │  ├─ main.tsx       React 시작
│  │  ├─ App.tsx
│  │  ├─ pages/LoginPage.tsx
│  │  ├─ components/    LoginHero, SocialLoginButton, AccountFooter
│  │  └─ styles/login.css
│  ├─ public/assets/   로그인 화면 이미지
│  └─ package.json
├─ build.gradle        루트 집계
├─ settings.gradle
└─ gradlew.bat
```

frontend/index.html은 React 진입 HTML이며 화면 코드는 LoginPage.tsx에 있습니다.

## 처음 실행

1. IntelliJ에서 루트 build.gradle을 다시 불러옵니다. Java SDK는 21입니다.
2. frontend 폴더에서 npm ci를 실행합니다. Node는 Vite 7 요구 버전인 20.19+ 또는 22.12+를 사용합니다.
3. PostgreSQL을 실행하고 루트의 `.env`를 `.env.example`에서 복사해 DB 접속 정보와 고유한 서명 키 비밀번호를 설정합니다. 그 비밀번호로 로컬 서명 키를 한 번 생성합니다.

   ```powershell
   New-Item -ItemType Directory "$env:USERPROFILE\.dagong-auth" -Force
   $keyPassword = Read-Host "AUTH_KEYSTORE_PASSWORD"
   keytool -genkeypair -alias dagong-auth-signing -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore "$env:USERPROFILE\.dagong-auth\auth-signing.p12" -storepass $keyPassword -keypass $keyPassword -dname "CN=Dagong Auth Development" -validity 3650
   ```

   최초 실행 시 V1 스키마가 적용됩니다.
4. Gradle 창에서 backend → Tasks → application → bootRun을 실행합니다.
   터미널에서는 루트에서 .\gradlew.bat :backend:bootRun 을 실행합니다.
5. http://localhost:4840/login 접속.

processResources가 React 빌드를 자동 실행하고 결과를 backend/build/resources/main/static/auth에 포함합니다. IntelliJ Java 클래스 직접 실행보다 Gradle bootRun을 권장합니다. 직접 실행 시에는 먼저 :backend:classes를 실행하고 backend 모듈 classpath를 사용하세요.

## 화면 개발

프로젝트 루트 또는 frontend에서 npm run dev 실행 후 http://localhost:5850/login 접속합니다. React 화면 수정은 즉시 반영됩니다.
공통 인증 서버는 4840, React 개발 화면은 5850에서 실행합니다. 개발 화면의 OAuth 요청은 Vite가 인증 서버로 전달하며, 맛다공의 4845와 겹치지 않습니다.
실제 OAuth callback은 제공자 연결 작업 시 확정해야 합니다. Gradle 빌드에 포함된 React 로그인 화면과 인증 서버는 http://localhost:4840/login 에서 제공합니다.

## 배포

frontend에서 npm ci 실행 후 루트에서 .\gradlew.bat :backend:bootJar 실행.
backend/build/libs/dagong-auth.jar에 React 빌드가 포함됩니다.
Dockerfile도 이 JAR를 사용합니다. 운영 화면에 Vite 개발 서버는 필요 없습니다.
프런트 빌드 결과를 src/main/resources에 수동 복사하지 않습니다.

## 현재 기능 범위

- React 로그인 화면: 시안의 로고·배경·캐릭터가 이어지는 상단, 네이버·카카오·Apple 버튼.
- 버튼은 연결 전 안내만 표시합니다. 실제 소셜 인증과 맛다공 콜백은 아직 미구현입니다.
- /login?error, /login?logout 안내를 React 상태로 표시합니다.
- 계정·소셜 연결·서비스 이용 현황·관리자 조치·OAuth 인가/동의 저장소가 V1 스키마에 포함됩니다.
- 개발 OAuth client_id: matdagong-web-dev, Authorization Code + PKCE, redirect_uri 기본값: http://localhost:4845/auth/callback.
- client 설정은 `dagong.auth.matdagong-client-id` / `dagong.auth.matdagong-redirect-uri`로 변경 가능합니다. 설정한 redirect URI는 서버 시작 시 개발용 client 항목에 반영됩니다.
- 클라이언트·인가·동의 저장소는 PostgreSQL을 사용합니다. 개발용 RSA 서명 키는 사용자 홈의 PKCS12 파일에 저장해 재시작 뒤에도 유지됩니다.
- 운영 배포에서는 `AUTH_KEYSTORE_PATH`와 `AUTH_KEYSTORE_PASSWORD`를 운영 비밀 관리 체계에 맞게 설정해야 합니다.

## DB 경계

공통 인증 서버만 dagong_auth DB에 접근합니다. 맛다공·여다공은 공통 사용자 ID와 인증 서버가 발급한 토큰으로 연동합니다.

## 계정 데이터

- `accounts`: 공통 사용자 UUID, 선택 이메일, 계정 상태, 로그인·탈퇴·차단 시각을 보관합니다. 이메일 없이 소셜 계정을 만들 수 있습니다.
- `social_identities`: 제공자와 제공자 사용자 ID를 연결합니다. 같은 제공자 계정은 한 계정에만 연결되며, 다른 제공자는 별도 계정으로 가입할 수 있습니다.
- `account_admin_actions`: 관리자 차단·해제와 탈퇴 처리 이력을 기록합니다.
- `account_services`: 계정의 맛다공·여다공별 가입 상태, 탈퇴 상태, 마지막 이용 시각을 기록합니다. 관리자는 서비스별 이용 현황을 조회할 수 있습니다.
- `oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent`: 서비스 클라이언트, 발급된 인가·토큰 상태, 사용자 동의를 저장해 서버 재시작 뒤에도 SSO 상태를 유지합니다.
- 향후 본인 확인을 위해 전화번호와 CI 해시 필드를 비워 두었습니다. 본인 확인 연동이나 CI 값 저장은 현재 활성화되지 않았습니다. CI 원문은 저장하지 않습니다.

`V1__initial_sso_schema.sql`은 개발 시작 시점의 전체 스키마입니다. 이메일은 선택 연락처이며 로그인 식별자가 아니므로, 같은 이메일을 가진 소셜 계정도 별도 계정으로 만들 수 있습니다. 한 공통 계정이 두 서비스 모두에 가입하면 `account_services`에 서비스별 행이 각각 생깁니다. 테이블·칼럼 설명은 PostgreSQL `COMMENT` 메타데이터에 기록됩니다.

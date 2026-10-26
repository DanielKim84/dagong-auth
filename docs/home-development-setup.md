# 집 컴퓨터에서 개발 이어하기

이 문서는 회사 컴퓨터의 비밀 파일을 옮기지 않고 집 컴퓨터에서 `dagong-auth`를 실행하기 위한 안내입니다. 코드는 Git으로 받고, 두 컴퓨터는 Neon의 같은 개발 DB를 사용합니다. 비밀번호와 개인 키는 각 컴퓨터에 따로 둡니다.

## 현재 공유 개발 DB

- Neon 프로젝트: `dagong`
- Neon 기본 브랜치 표시명: `production` (개발용으로 쓰는 기본 브랜치 이름이며 운영 환경을 뜻하지 않음)
- 데이터베이스: `dagong_auth_dev`
- 리전: Singapore
- 호스트: `ep-crimson-mud-az60gpd9.c-3.ap-southeast-1.aws.neon.tech`
- 포트: `5432`
- SSL: `require`
- 회사 PC DB 사용자: `dagong_auth_app`

집 컴퓨터에서는 Neon 콘솔에 로그인해 집 전용 DB 사용자를 준비합니다. 회사 PC 사용자 비밀번호는 확인하거나 옮길 수 없으므로, 집 사용자와 비밀번호를 별도로 만들고 `dagong_auth_dev`의 기존 스키마를 사용할 권한을 부여합니다. Flyway가 마이그레이션을 적용할 수 있도록 필요한 스키마 및 테이블 권한을 설정합니다. 콘솔에서 권한 오류가 발생하면 오류 문구를 확인해 필요한 권한만 추가합니다.

## 집 컴퓨터 설정

1. Java 21과 Node.js 20.19+ 또는 22.12+를 설치하고 저장소를 `C:\workspace\dagong-auth`에 clone하거나 최신 `main`을 pull 합니다.
2. 저장소 루트에서 `.env.example`을 `.env`로 복사합니다. `.env`는 Git에 올라가지 않습니다.
3. `.env`의 `DB_URL`을 아래 주소로, `DB_USERNAME`을 집 전용 Neon 사용자로, `DB_PASSWORD`를 그 사용자의 비밀번호로 설정합니다.

   ```text
   DB_URL=jdbc:postgresql://ep-crimson-mud-az60gpd9.c-3.ap-southeast-1.aws.neon.tech:5432/dagong_auth_dev?sslmode=require
   DB_USERNAME=<집 전용 DB 사용자>
   DB_PASSWORD=<집 전용 DB 비밀번호>
   ```

4. Kakao Developers에 로그인해 현재 개발 앱의 REST API 키와 Client Secret을 확인해 `.env`에 입력합니다. 필요한 경우 앱에 아래 로컬 Redirect URI가 등록되어 있는지 확인합니다.

   ```text
   http://localhost:4840/login/oauth2/code/kakao
   ```

5. `.env`에 직접 입력한 `AUTH_KEYSTORE_PASSWORD`를 사용해 서명 키를 집 컴퓨터에서 생성합니다. 회사 키 파일을 복사하지 않습니다.

   ```powershell
   New-Item -ItemType Directory "$env:USERPROFILE\.dagong-auth" -Force
   $keyPassword = Read-Host "AUTH_KEYSTORE_PASSWORD"
   keytool -genkeypair -alias dagong-auth-signing -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore "$env:USERPROFILE\.dagong-auth\auth-signing.p12" -storepass $keyPassword -keypass $keyPassword -dname "CN=Dagong Auth Development" -validity 3650
   ```

6. `frontend` 폴더에서 `npm ci`를 실행합니다. 백엔드는 저장소 루트에서 `.\gradlew.bat :backend:bootRun`, 화면은 `frontend` 폴더에서 `npm run dev`로 실행합니다.
7. `http://localhost:4840/login`에 접속합니다. 앱 시작 로그에서 Neon 연결 성공을 확인합니다. 이미 원격 DB에 적용된 V1과 V2는 다시 초기화되지 않으며, 이후 추가되는 Flyway 마이그레이션이 자동 적용됩니다.

## 보안 주의

- 비밀 값은 채팅, Git, 일반 메신저에 입력하지 않습니다.
- 회사 PC의 `.env`, Neon 비밀번호 파일, `auth-signing.p12`를 집으로 복사하지 않습니다.
- 집 PC의 `.env`와 개인 키는 그 PC 안에만 보관합니다.
- Neon DB에는 개발 테스트 데이터만 저장합니다.

새로운 Codex 대화에서 설정 도움을 받을 때는 다음처럼 요청합니다.

> 저장소의 `docs/home-development-setup.md`를 읽고 집 컴퓨터 설정을 이어서 안내해줘. 비밀 값은 채팅에 요청하거나 출력하지 말고, 내가 Neon과 Kakao 콘솔 및 로컬 `.env`에 직접 입력하도록 단계별로 진행해줘.

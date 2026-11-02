# AI 작업 지침 (dagong-auth)

Claude Code, Codex 등 AI 도구는 이 저장소에서 작업하기 전에 이 파일을 따릅니다. 사람도 같은 규칙을 따릅니다.

## 먼저 읽을 문서

맛다공 저장소(같은 상위 폴더의 `dagong-matdagong`)에 두 저장소 공통 문서가 있습니다.

- 코딩 스타일: `../dagong-matdagong/docs/coding-style.md` (GitHub: https://github.com/DanielKim84/matdagong/blob/main/docs/coding-style.md)
- 개발 환경(다른 PC 세팅 포함): `../dagong-matdagong/docs/home-development-setup.md`
- 이 저장소 구조와 인증 흐름: [README.md](README.md)

## 꼭 지킬 것

- 사용자에게 하는 답변·진행 안내·주석·문서는 **한국어**로 씁니다. 작업 중인지 끝났는지 분명히 알립니다.
- 비밀 값(.env, `~/.dagong-auth/auth-signing.p12`)을 출력·커밋하지 않고, `.env.example`에 실제 값을 넣지 않습니다. `.env` 수정은 사용자 허락 후에만.
- 이미 적용된 Flyway 마이그레이션은 고치지 않고 새 버전 파일을 추가합니다.
- 카카오 로그인 흐름·토큰 발급·보안 설정을 바꿀 때는 이유를 먼저 설명하고 사용자 확인을 받습니다.
- 포트(4840·5840)와 `localhost` 주소를 바꾸지 않습니다. 커밋·푸시는 사용자가 요청할 때만 합니다.

## 작업을 마치기 전에

```powershell
npm.cmd run format                               # 화면 서식 (저장소 루트)
.\gradlew.bat :backend:spotlessApply             # Java 서식
cd frontend; npx tsc --noEmit -p .; cd ..
.\gradlew.bat :backend:test -x buildFrontend
```

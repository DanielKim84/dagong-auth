import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ command }) => ({
  // 개발 중에는 /login 경로를 직접 쓰고, 배포 빌드는 Spring Boot의 /auth 정적 경로에 맞춥니다.
  base: command === "serve" ? "/" : "/auth/",
  plugins: [react()],
  server: {
    // 화면 접속 포트(4840)에서 React를 제공하고 인증 API는 백엔드(5840)로 전달합니다.
    port: 4840,
    strictPort: true,
    proxy: {
      // 소셜 인증 시작(/oauth2/authorization/kakao·naver)과 callback(/login/oauth2/code/kakao·naver)을 전달합니다.
      "/oauth2": "http://localhost:5840",
      "/login/oauth2": "http://localhost:5840",
      // 한 기기 로그인 확인(계속·취소)과 다른 기기 로그인으로 로그아웃됐다는 안내 확인입니다.
      "/auth/device": "http://localhost:5840",
    },
  },
}));

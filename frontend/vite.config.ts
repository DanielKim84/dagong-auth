import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ command }) => ({
  // 개발 중에는 /login 경로를 직접 쓰고, 배포 빌드는 Spring Boot의 /auth 정적 경로에 맞춥니다.
  base: command === 'serve' ? '/' : '/auth/',
  plugins: [react()],
  server: {
    // 인증 서버(4840)와 구분되는 React 개발 포트 대역에서 실행합니다.
    port: 5850,
    strictPort: true,
    proxy: {
      // React 화면은 Vite에서 제공하고 OAuth 요청은 Spring Boot로 전달합니다.
      '/oauth2': 'http://localhost:4840',
      '/login/oauth2': 'http://localhost:4840',
    },
  },
}));

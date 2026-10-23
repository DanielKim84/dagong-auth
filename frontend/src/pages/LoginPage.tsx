import { useState } from 'react';
import LoginHero from '../components/LoginHero';
import SocialLoginButton from '../components/SocialLoginButton';
import AccountFooter from '../components/AccountFooter';

function initialMessage(): string {
  const parameters = new URLSearchParams(window.location.search);

  // Spring Security가 로그인 실패나 로그아웃 뒤에 붙이는 query parameter를 화면 안내로 바꿉니다.
  if (parameters.has('error')) return '로그인하지 못했어요. 잠시 후 다시 시도해 주세요.';
  if (parameters.has('logout')) return '로그아웃했어요.';
  if (parameters.get('login') === 'success') return '로그인이 완료됐어요.';

  return '';
}

export default function LoginPage() {
  const [message, setMessage] = useState<string>(initialMessage);

  return (
    <main className="login-card" aria-label="다공 계정 로그인">
      <LoginHero />
      <section className="login-content" aria-label="소셜 계정 로그인">
        <p className="tagline">맛있는 걸 함께하는 공간</p>
        <div className="social-buttons">
          <SocialLoginButton provider="naver" onUnavailable={setMessage} />
          <SocialLoginButton provider="kakao" onUnavailable={setMessage} />
          <SocialLoginButton provider="apple" onUnavailable={setMessage} />
        </div>
        <p className="login-status" role="status" aria-live="polite">
          {message}
        </p>
      </section>
      <AccountFooter />
    </main>
  );
}

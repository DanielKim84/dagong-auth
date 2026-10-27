import { useEffect, useState } from 'react';
import LoginHero from '../components/LoginHero';
import SocialLoginButton, { type SocialProvider } from '../components/SocialLoginButton';
import AccountFooter from '../components/AccountFooter';
import NoticeToast, { type NoticeKind } from '../components/NoticeToast';

type Notice = { kind: NoticeKind; title: string; message: string };
const socialProviders: SocialProvider[] = ['naver', 'kakao', 'apple'];

function initialNotice(): Notice | null {
  const url = new URL(window.location.href);
  const parameters = url.searchParams;

  // Spring Security가 로그인 실패나 로그아웃 뒤에 붙이는 query parameter를 화면 안내로 바꿉니다.
  let notice: Notice | null = null;
  if (parameters.has('error')) {
    notice = { kind: 'error', title: '로그인을 완료하지 못했어요', message: '잠시 후 다시 시도해 주세요.' };
  } else if (parameters.has('logout')) {
    notice = { kind: 'success', title: '로그아웃했어요', message: '다음에 또 맛있는 곳을 함께 찾아요.' };
  } else if (parameters.get('login') === 'success') {
    notice = { kind: 'success', title: '로그인됐어요', message: '다공 서비스를 이어서 이용해 주세요.' };
  }

  return notice;
}

export default function LoginPage() {
  const [notice, setNotice] = useState<Notice | null>(initialNotice);

  useEffect(() => {
    const url = new URL(window.location.href);
    if (
      !url.searchParams.has('error') &&
      !url.searchParams.has('logout') &&
      url.searchParams.get('login') !== 'success'
    ) {
      return;
    }
    url.searchParams.delete('error');
    url.searchParams.delete('logout');
    url.searchParams.delete('login');
    // 새로고침 때 같은 인증 결과 안내가 반복되지 않도록 주소에서 결과값을 지웁니다.
    window.history.replaceState(null, '', `${url.pathname}${url.search}${url.hash}`);
  }, []);

  return (
    <main className="login-card" aria-label="다공 계정 로그인">
      <LoginHero />
      <section className="login-content" aria-label="소셜 계정 로그인">
        <p className="tagline">맛있는 걸 함께하는 공간</p>
        <div className="social-buttons">
          {socialProviders.map((provider) => (
            <SocialLoginButton
              key={provider}
              provider={provider}
              onUnavailable={(providerName) => setNotice({
                kind: 'info',
                title: `${providerName} 로그인은 준비 중이에요`,
                message: '지금은 카카오로 가입하고 로그인할 수 있어요.',
              })}
            />
          ))}
        </div>
      </section>
      <AccountFooter />
      {notice && (
        <NoticeToast
          kind={notice.kind}
          title={notice.title}
          message={notice.message}
          onDismiss={() => setNotice(null)}
        />
      )}
    </main>
  );
}

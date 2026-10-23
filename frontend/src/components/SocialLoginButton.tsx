export type SocialProvider = 'naver' | 'kakao' | 'apple';

const providerNames: Record<SocialProvider, string> = {
  naver: '네이버',
  kakao: '카카오',
  apple: 'Apple',
};

const kakaoIconPath =
  'M12 4C6.5 4 2 7.3 2 11.3c0 2.6 1.9 4.8 4.7 6.1l-.9 3.1c-.1.3.2.5.4.3l3.7-2.4c.7.1 1.4.2 2.1.2 5.5 0 10-3.3 10-7.3S17.5 4 12 4Z';
const appleIconPath =
  'M16.6 3.2c-.7.9-1.8 1.5-2.9 1.4-.2-1.1.4-2.3 1.1-3 .8-.8 1.9-1.4 3-1.5.1 1.1-.4 2.3-1.2 3.1ZM20.8 17c-.5 1.2-.8 1.8-1.5 2.8-.9 1.2-2.1 2.7-3.5 2.7-1.2 0-1.6-.8-3.3-.8s-2.2.8-3.3.8c-1.4.1-2.5-1.3-3.4-2.6C3.3 16.4 2.7 11.9 4.2 9.5c1-1.7 2.6-2.7 4.2-2.7 1.3 0 2.2.8 3.3.8s1.8-.8 3.3-.8c1.3 0 2.7.7 3.7 1.9-3.3 1.8-2.8 6.5.1 8.3Z';

interface SocialLoginButtonProps {
  provider: SocialProvider;
  onUnavailable: (message: string) => void;
}

export default function SocialLoginButton({ provider, onUnavailable }: SocialLoginButtonProps) {
  const providerName = providerNames[provider];

  // 화면 개발 서버(4840)와 인증 API 서버(5840)를 분리해 Kakao 인증은 백엔드에서 시작합니다.
  function handleClick() {
    if (provider === 'kakao') {
      // 개발 중에는 Vite가 5840으로 전달하고, 배포에서는 같은 서버가 이 경로를 처리합니다.
      window.location.assign('/oauth2/authorization/kakao');
      return;
    }

    // 아직 연동하지 않은 다른 제공자는 기존 안내를 유지합니다.
    onUnavailable(`${providerName} 로그인은 아직 연결 전이에요. 연결이 완료되면 여기서 시작할 수 있어요.`);
  }

  return (
    <button className={`social-button ${provider}`} type="button" onClick={handleClick}>
      {provider === 'naver' ? (
        <span className="provider-icon naver-icon" aria-hidden="true">
          N
        </span>
      ) : (
        <svg className="provider-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path fill="currentColor" d={provider === 'kakao' ? kakaoIconPath : appleIconPath} />
        </svg>
      )}
      <span>{providerName}로 시작하기</span>
    </button>
  );
}

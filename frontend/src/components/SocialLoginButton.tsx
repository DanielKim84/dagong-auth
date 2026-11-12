/** 맛다공 설정 화면(AccountSettingsScreen)의 연결된 계정 목록과 같은 제공자입니다. */
export type SocialProvider = "naver" | "kakao";

const PROVIDER_NAMES: Record<SocialProvider, string> = {
  naver: "네이버",
  kakao: "카카오",
};

const KAKAO_ICON_PATH =
  "M12 4C6.5 4 2 7.3 2 11.3c0 2.6 1.9 4.8 4.7 6.1l-.9 3.1c-.1.3.2.5.4.3l3.7-2.4c.7.1 1.4.2 2.1.2 5.5 0 10-3.3 10-7.3S17.5 4 12 4Z";
const NAVER_ICON_PATH = "M5 5h4.6l4.8 7V5H19v14h-4.6l-4.8-7v7H5z";
const ICON_PATHS: Record<SocialProvider, string> = {
  kakao: KAKAO_ICON_PATH,
  naver: NAVER_ICON_PATH,
};

export default function SocialLoginButton({ provider }: { provider: SocialProvider }) {
  const providerName = PROVIDER_NAMES[provider];

  function startLogin() {
    // 소셜 인증은 백엔드에서 시작합니다. 개발 중에는 Vite가 5840으로 전달하고, 배포에서는 같은 서버가 처리합니다.
    window.location.assign(`/oauth2/authorization/${provider}`);
  }

  return (
    <button className={`social-button ${provider}`} type="button" onClick={startLogin}>
      <svg className="provider-icon" viewBox="0 0 24 24" aria-hidden="true">
        <path fill="currentColor" d={ICON_PATHS[provider]} />
      </svg>
      <span>{providerName}로 시작하기</span>
    </button>
  );
}

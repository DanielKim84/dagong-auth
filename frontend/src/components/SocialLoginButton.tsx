export type SocialProvider = "naver" | "kakao" | "apple";

const PROVIDER_NAMES: Record<SocialProvider, string> = {
  naver: "네이버",
  kakao: "카카오",
  apple: "Apple",
};

const KAKAO_ICON_PATH =
  "M12 4C6.5 4 2 7.3 2 11.3c0 2.6 1.9 4.8 4.7 6.1l-.9 3.1c-.1.3.2.5.4.3l3.7-2.4c.7.1 1.4.2 2.1.2 5.5 0 10-3.3 10-7.3S17.5 4 12 4Z";
const NAVER_ICON_PATH = "M5 5h4.6l4.8 7V5H19v14h-4.6l-4.8-7v7H5z";
const APPLE_ICON_PATH =
  "M16.6 3.2c-.7.9-1.8 1.5-2.9 1.4-.2-1.1.4-2.3 1.1-3 .8-.8 1.9-1.4 3-1.5.1 1.1-.4 2.3-1.2 3.1ZM20.8 17c-.5 1.2-.8 1.8-1.5 2.8-.9 1.2-2.1 2.7-3.5 2.7-1.2 0-1.6-.8-3.3-.8s-2.2.8-3.3.8c-1.4.1-2.5-1.3-3.4-2.6C3.3 16.4 2.7 11.9 4.2 9.5c1-1.7 2.6-2.7 4.2-2.7 1.3 0 2.2.8 3.3.8s1.8-.8 3.3-.8c1.3 0 2.7.7 3.7 1.9-3.3 1.8-2.8 6.5.1 8.3Z";
const ICON_PATHS: Record<SocialProvider, string> = {
  kakao: KAKAO_ICON_PATH,
  naver: NAVER_ICON_PATH,
  apple: APPLE_ICON_PATH,
};

export default function SocialLoginButton({
  provider,
  onUnavailable,
}: {
  provider: SocialProvider;
  onUnavailable: (providerName: string) => void;
}) {
  const providerName = PROVIDER_NAMES[provider];
  const isUnavailable = provider === "apple";

  // 카카오·네이버는 연동했고, Apple은 앱 출시 뒤 붙일 예정이라 준비 중 안내를 표시합니다.
  function startLogin() {
    if (isUnavailable) {
      onUnavailable(providerName);
      return;
    }
    // 소셜 인증은 백엔드에서 시작합니다. 개발 중에는 Vite가 5840으로 전달하고, 배포에서는 같은 서버가 처리합니다.
    window.location.assign(`/oauth2/authorization/${provider}`);
  }

  return (
    <button
      className={`social-button ${provider}${isUnavailable ? " is-unavailable" : ""}`}
      type="button"
      aria-disabled={isUnavailable || undefined}
      onClick={startLogin}
    >
      <svg className="provider-icon" viewBox="0 0 24 24" aria-hidden="true">
        <path fill="currentColor" d={ICON_PATHS[provider]} />
      </svg>
      <span>{providerName}로 시작하기</span>
      {isUnavailable && <span className="coming-soon">준비 중</span>}
    </button>
  );
}

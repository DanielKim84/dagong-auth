import { useEffect, useState } from "react";
import AccountFooter from "../components/AccountFooter";
import AuthToast, { type AuthToastKind } from "../components/AuthToast";
import LoginHero from "../components/LoginHero";
import SocialLoginButton, { type SocialProvider } from "../components/SocialLoginButton";

type Toast = { kind: AuthToastKind; title: string; message: string };
// 연동된 카카오를 맨 위에 두고, 준비 중인 네이버·Apple은 아래에 비활성으로 보여줍니다.
const SOCIAL_PROVIDERS: SocialProvider[] = ["kakao", "naver", "apple"];

function initialToast(): Toast | null {
  const url = new URL(window.location.href);
  const parameters = url.searchParams;

  // 로그인 실패(?error)·로그아웃(?logout)·로그인 성공(?login=success, AUTH_LOGIN_SUCCESS_URL에서 지정) 뒤
  // 붙는 query parameter를 화면 안내로 바꿉니다.
  let toast: Toast | null = null;
  if (parameters.has("error")) {
    toast = { kind: "error", title: "로그인을 완료하지 못했어요", message: "잠시 후 다시 시도해 주세요." };
  } else if (parameters.has("logout")) {
    toast = { kind: "success", title: "로그아웃했어요", message: "다음에 또 맛있는 곳을 함께 찾아요." };
  } else if (parameters.get("login") === "success") {
    toast = { kind: "success", title: "로그인됐어요", message: "다공 서비스를 이어서 이용해 주세요." };
  }

  return toast;
}

export default function LoginPage() {
  const [toast, setToast] = useState<Toast | null>(initialToast);

  useEffect(() => {
    if (initialToast() === null) return;
    const url = new URL(window.location.href);
    url.searchParams.delete("error");
    url.searchParams.delete("logout");
    url.searchParams.delete("login");
    // 새로고침 때 같은 인증 결과 안내가 반복되지 않도록 주소에서 결과값을 지웁니다.
    window.history.replaceState(null, "", `${url.pathname}${url.search}${url.hash}`);
  }, []);

  return (
    <main className="login-card" aria-label="맛다공 로그인">
      <LoginHero />
      <section className="login-content" aria-label="소셜 계정 로그인">
        <div className="social-buttons">
          {SOCIAL_PROVIDERS.map((provider) => (
            <SocialLoginButton
              key={provider}
              provider={provider}
              onUnavailable={(providerName) =>
                setToast({
                  kind: "info",
                  title: `${providerName} 로그인은 준비 중이에요`,
                  message: "지금은 카카오로 가입하고 로그인할 수 있어요.",
                })
              }
            />
          ))}
        </div>
      </section>
      <AccountFooter />
      {toast && (
        <AuthToast kind={toast.kind} title={toast.title} message={toast.message} onDismiss={() => setToast(null)} />
      )}
    </main>
  );
}

import { useEffect, useState } from "react";
import AccountFooter from "../components/AccountFooter";
import AuthToast, { type AuthToastKind } from "../components/AuthToast";
import DeviceConfirm, { type DeviceType } from "../components/DeviceConfirm";
import LoginHero from "../components/LoginHero";
import SocialLoginButton, { type SocialProvider } from "../components/SocialLoginButton";

type Toast = { kind: AuthToastKind; title: string; message: string };
// 연동된 카카오·네이버만 보여 줍니다. Apple은 아이폰 앱을 출시할 때 iOS에서만 보이게 붙일 예정입니다.
const SOCIAL_PROVIDERS: SocialProvider[] = ["kakao", "naver"];

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

/** 인증 서버가 한 기기 로그인 확인을 위해 붙인 기기 종류입니다. 없거나 모르는 값이면 null입니다. */
function confirmDeviceType(): DeviceType | null {
  const value = new URL(window.location.href).searchParams.get("device-confirm");
  return value === "MOBILE" || value === "PC" ? value : null;
}

export default function LoginPage() {
  const [toast, setToast] = useState<Toast | null>(initialToast);
  const [deviceType] = useState(confirmDeviceType);
  const [isSignedOutByOtherDevice, setIsSignedOutByOtherDevice] = useState(false);

  // 다른 기기 로그인으로 이 기기가 로그아웃됐으면 한 번만 안내합니다. 확인하지 못해도 로그인은 그대로 할 수 있습니다.
  // 서버가 한 번 답하면 표시를 지우므로, 개발 모드에서 effect가 두 번 돌아도 먼저 온 true를 버리지 않게 늦은 응답을 막지 않습니다.
  useEffect(() => {
    if (deviceType) return;
    fetch("/auth/device/notice")
      .then((response) => (response.ok ? response.json() : null))
      .then((body: unknown) => {
        if (
          body &&
          typeof body === "object" &&
          "signedOutByOtherDevice" in body &&
          body.signedOutByOtherDevice === true
        ) {
          setIsSignedOutByOtherDevice(true);
        }
      })
      .catch(() => {});
  }, [deviceType]);

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
      {deviceType ? (
        <section className="login-content">
          <DeviceConfirm deviceType={deviceType} />
        </section>
      ) : (
        <section className="login-content" aria-label="소셜 계정 로그인">
          {isSignedOutByOtherDevice && (
            <p className="device-signed-out" role="status">
              다른 기기에서 로그인되어 이 기기는 로그아웃됐어요.
            </p>
          )}
          <div className="social-buttons">
            {SOCIAL_PROVIDERS.map((provider) => (
              <SocialLoginButton key={provider} provider={provider} />
            ))}
          </div>
        </section>
      )}
      <AccountFooter />
      {toast && (
        <AuthToast kind={toast.kind} title={toast.title} message={toast.message} onDismiss={() => setToast(null)} />
      )}
    </main>
  );
}

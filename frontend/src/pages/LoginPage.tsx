import { useEffect, useState } from "react";
import AuthToast, { type AuthToastKind } from "../components/AuthToast";
import DagongIntro from "../components/DagongIntro";
import DeviceConfirm from "../components/DeviceConfirm";
import LoginError from "../components/LoginError";
import SocialLoginButton, { type SocialProvider } from "../components/SocialLoginButton";

type Toast = { kind: AuthToastKind; title: string; message: string };
// 연동된 카카오·네이버만 보여 줍니다. Apple은 아이폰 앱을 출시할 때 iOS에서만 보이게 붙일 예정입니다.
const SOCIAL_PROVIDERS: SocialProvider[] = ["kakao", "naver"];
const FOOD_PHOTO_URL = `${import.meta.env.BASE_URL}assets/login-food.jpg`;

function initialToast(): Toast | null {
  const parameters = new URL(window.location.href).searchParams;

  // 로그아웃(?logout)·로그인 성공(?login=success, AUTH_LOGIN_SUCCESS_URL에서 지정) 뒤 붙는 query parameter를 화면 안내로 바꿉니다.
  // 로그인 실패(?error)는 안내가 아니라 따로 된 오류 화면(LoginError)으로 보여 줍니다.
  if (parameters.has("logout")) {
    return { kind: "success", title: "로그아웃했어요", message: "다음에 또 맛있는 곳을 함께 찾아요." };
  }
  if (parameters.get("login") === "success") {
    return { kind: "success", title: "로그인됐어요", message: "다공 서비스를 이어서 이용해 주세요." };
  }
  return null;
}

/** 인증 서버가 한 기기 로그인 확인을 위해 붙인 기기 종류(?device-confirm=MOBILE|PC)가 있는지 봅니다. */
function needsDeviceConfirm(): boolean {
  const value = new URL(window.location.href).searchParams.get("device-confirm");
  return value === "MOBILE" || value === "PC";
}

export default function LoginPage() {
  const [toast, setToast] = useState<Toast | null>(initialToast);
  const [isDeviceConfirm] = useState(needsDeviceConfirm);
  const [hasError, setHasError] = useState(() => new URL(window.location.href).searchParams.has("error"));
  const [isSignedOutByOtherDevice, setIsSignedOutByOtherDevice] = useState(false);

  // 다른 기기 로그인으로 이 기기가 로그아웃됐으면 한 번만 안내합니다. 확인하지 못해도 로그인은 그대로 할 수 있습니다.
  // 서버가 한 번 답하면 표시를 지우므로, 개발 모드에서 effect가 두 번 돌아도 먼저 온 true를 버리지 않게 늦은 응답을 막지 않습니다.
  useEffect(() => {
    if (isDeviceConfirm) return;
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
  }, [isDeviceConfirm]);

  useEffect(() => {
    const url = new URL(window.location.href);
    if (!["error", "logout", "login"].some((name) => url.searchParams.has(name))) return;
    url.searchParams.delete("error");
    url.searchParams.delete("logout");
    url.searchParams.delete("login");
    // 새로고침 때 같은 인증 결과 안내가 반복되지 않도록 주소에서 결과값을 지웁니다.
    window.history.replaceState(null, "", `${url.pathname}${url.search}${url.hash}`);
  }, []);

  let content;
  if (isDeviceConfirm) {
    content = <DeviceConfirm />;
  } else if (hasError) {
    content = <LoginError onRetry={() => setHasError(false)} />;
  } else {
    content = (
      <>
        <DagongIntro />
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
        <img className="login-food" src={FOOD_PHOTO_URL} alt="" />
      </>
    );
  }

  return (
    <main className={`login-card${hasError || isDeviceConfirm ? "" : " is-login"}`} aria-label="다공 계정 로그인">
      {content}
      {toast && (
        <AuthToast kind={toast.kind} title={toast.title} message={toast.message} onDismiss={() => setToast(null)} />
      )}
    </main>
  );
}

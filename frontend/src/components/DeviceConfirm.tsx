import { useState } from "react";
import MatdagongMark from "./MatdagongMark";

const SYMBOL_URL = `${import.meta.env.BASE_URL}assets/matdagong-symbol.png`;

/** 맛다공이 켜진 휴대폰과 노트북 그림입니다. 장식이라 읽히지 않습니다. */
function DevicesArt() {
  return (
    <div className="devices-art" aria-hidden="true">
      <span className="devices-art__laptop">
        <span className="devices-art__screen">
          <img src={SYMBOL_URL} alt="" />
        </span>
        <span className="devices-art__base" />
      </span>
      <span className="devices-art__phone">
        <span className="devices-art__screen">
          <img src={SYMBOL_URL} alt="" />
        </span>
      </span>
    </div>
  );
}

/**
 * 같은 종류의 다른 기기에 맛다공 로그인이 남아 있을 때, 그 기기를 로그아웃하고 계속할지 묻습니다(보드 02-04).
 * 인증 서버가 인가 요청을 멈추고 이 화면(?device-confirm=MOBILE|PC)으로 보냅니다.
 * 계속하면 이전 기기는 로그아웃되고, 취소하면 로그인하지 않고 처음 로그인 화면으로 돌아갑니다.
 */
export default function DeviceConfirm() {
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState("");

  async function answer(choice: "confirm" | "cancel") {
    setIsSending(true);
    setError("");
    try {
      const response = await fetch(`/auth/device/${choice}`, { method: "POST" });
      const body: unknown = response.ok ? await response.json() : null;
      const redirectUrl =
        body && typeof body === "object" && "redirectUrl" in body && typeof body.redirectUrl === "string"
          ? body.redirectUrl
          : null;
      if (!redirectUrl) throw new Error("no redirect");
      window.location.assign(redirectUrl);
    } catch {
      // 확인을 기다리던 로그인이 사라졌거나(시간 초과·새로고침) 서버에 닿지 못한 경우입니다.
      setError("로그인을 이어 가지 못했어요. 맛다공에서 다시 로그인해 주세요.");
      setIsSending(false);
    }
  }

  return (
    <section className="auth-screen" aria-labelledby="device-confirm-title">
      <MatdagongMark />
      <div className="auth-screen__body is-compact">
        <DevicesArt />
        <h1 id="device-confirm-title">
          다른 기기에서
          <br />
          로그인할까요?
        </h1>
        <p>
          맛다공은 휴대폰 1대와 PC 1대까지
          <br />
          동시에 이용할 수 있어요.
          <br />
          지금 기기로 로그인하면
          <br />
          같은 종류의 다른 기기에서는 로그아웃돼요.
        </p>
        {error && (
          <p className="auth-screen__error" role="alert">
            {error}
          </p>
        )}
      </div>
      <div className="auth-screen__actions">
        <button
          type="button"
          className="auth-button is-primary"
          disabled={isSending}
          onClick={() => void answer("confirm")}
        >
          {isSending ? "확인하고 있어요…" : "계속하기"}
        </button>
        <button type="button" className="auth-button" disabled={isSending} onClick={() => void answer("cancel")}>
          취소
        </button>
      </div>
    </section>
  );
}

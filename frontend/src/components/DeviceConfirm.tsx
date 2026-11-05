import { useState } from "react";

export type DeviceType = "MOBILE" | "PC";

const DEVICE_LABEL: Record<DeviceType, string> = { MOBILE: "휴대폰", PC: "PC" };

/**
 * 같은 종류의 다른 기기에 맛다공 로그인이 남아 있을 때, 그 기기를 로그아웃하고 계속할지 묻습니다.
 * 인증 서버가 인가 요청을 멈추고 이 화면(?device-confirm=MOBILE|PC)으로 보냅니다.
 * 계속하면 이전 기기는 로그아웃되고, 취소하면 로그인하지 않고 처음 로그인 화면으로 돌아갑니다.
 */
export default function DeviceConfirm({ deviceType }: { deviceType: DeviceType }) {
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState("");
  const label = DEVICE_LABEL[deviceType];

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
    <section className="device-confirm" aria-labelledby="device-confirm-title">
      <h1 id="device-confirm-title">다른 {label}에서 로그인되어 있어요</h1>
      <p>
        계속하면 그 {label}의 맛다공은 로그아웃돼요.
        <br />
        맛다공은 {label} 한 대에서만 로그인할 수 있어요.
      </p>
      {error && (
        <p className="device-confirm__error" role="alert">
          {error}
        </p>
      )}
      <div className="device-confirm__actions">
        <button
          type="button"
          className="device-confirm__button is-primary"
          disabled={isSending}
          onClick={() => void answer("confirm")}
        >
          {isSending ? "확인하고 있어요…" : "계속"}
        </button>
        <button
          type="button"
          className="device-confirm__button"
          disabled={isSending}
          onClick={() => void answer("cancel")}
        >
          취소
        </button>
      </div>
    </section>
  );
}

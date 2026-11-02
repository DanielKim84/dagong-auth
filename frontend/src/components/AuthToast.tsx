import { useEffect } from "react";

export type AuthToastKind = "info" | "success" | "error";

/** 로그인 화면 아래쪽에 잠깐 떠서 로그인 결과·안내를 보여주고 스스로 닫히는 알림입니다. */
export default function AuthToast({
  kind,
  title,
  message,
  onDismiss,
}: {
  kind: AuthToastKind;
  title: string;
  message: string;
  onDismiss: () => void;
}) {
  useEffect(() => {
    const timeout = window.setTimeout(onDismiss, 3500);
    return () => window.clearTimeout(timeout);
  }, [message, onDismiss]);

  return (
    <div
      className={`auth-toast auth-toast--${kind}`}
      role={kind === "error" ? "alert" : "status"}
      aria-live={kind === "error" ? "assertive" : "polite"}
    >
      <span className="auth-toast__icon" aria-hidden="true">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          {kind === "success" ? (
            <path d="m5 12 4 4L19 6" />
          ) : kind === "error" ? (
            <path d="M12 8v5m0 3h.01M10.3 3.9 2.8 17a2 2 0 0 0 1.7 3h15a2 2 0 0 0 1.7-3l-7.5-13.1a2 2 0 0 0-3.4 0Z" />
          ) : (
            <>
              <path d="M12 11v5" />
              <path d="M12 8h.01" />
              <circle cx="12" cy="12" r="9" />
            </>
          )}
        </svg>
      </span>
      <span className="auth-toast__copy">
        <b>{title}</b>
        <span>{message}</span>
      </span>
      <button className="auth-toast__close" type="button" onClick={onDismiss} aria-label="알림 닫기">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          aria-hidden="true"
        >
          <path d="m6 6 12 12M18 6 6 18" />
        </svg>
      </button>
    </div>
  );
}

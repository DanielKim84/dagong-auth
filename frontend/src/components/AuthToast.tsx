import { useEffect } from "react";

/**
 * 로그인 화면 아래쪽에 잠깐 떠서 로그인·로그아웃 결과를 보여주고 스스로 닫히는 알림입니다(초록 체크).
 * 로그인 실패는 이 알림이 아니라 따로 된 오류 화면(LoginError)으로 보여 줍니다.
 */
export default function AuthToast({
  title,
  message,
  onDismiss,
}: {
  title: string;
  message: string;
  onDismiss: () => void;
}) {
  useEffect(() => {
    const timeout = window.setTimeout(onDismiss, 3500);
    return () => window.clearTimeout(timeout);
  }, [message, onDismiss]);

  return (
    <div className="auth-toast" role="status" aria-live="polite">
      <span className="auth-toast__icon" aria-hidden="true">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="m5 12 4 4L19 6" />
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

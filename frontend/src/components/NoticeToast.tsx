import { useEffect } from 'react';

export type NoticeKind = 'info' | 'success' | 'error';

type Props = {
  kind: NoticeKind;
  title: string;
  message: string;
  onDismiss: () => void;
};

/** 페이지 배치를 바꾸지 않고 상태와 안내를 함께 보여주는 공통 플로팅 알림입니다. */
export default function NoticeToast({ kind, title, message, onDismiss }: Props) {
  useEffect(() => {
    const timeout = window.setTimeout(onDismiss, 3500);
    return () => window.clearTimeout(timeout);
  }, [message, onDismiss]);

  return (
    <div
      className={`global-notice global-notice--${kind}`}
      role={kind === 'error' ? 'alert' : 'status'}
      aria-live={kind === 'error' ? 'assertive' : 'polite'}
    >
      <span className="global-notice__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          {kind === 'success' ? (
            <path d="m5 12 4 4L19 6" />
          ) : kind === 'error' ? (
            <path d="M12 8v5m0 3h.01M10.3 3.9 2.8 17a2 2 0 0 0 1.7 3h15a2 2 0 0 0 1.7-3l-7.5-13.1a2 2 0 0 0-3.4 0Z" />
          ) : (
            <><path d="M12 11v5" /><path d="M12 8h.01" /><circle cx="12" cy="12" r="9" /></>
          )}
        </svg>
      </span>
      <span className="global-notice__copy">
        <b>{title}</b>
        <span>{message}</span>
      </span>
      <button
        className="global-notice__close"
        type="button"
        onClick={onDismiss}
        aria-label="알림 닫기"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
          <path d="m6 6 12 12M18 6 6 18" />
        </svg>
      </button>
    </div>
  );
}

import MatdagongMark from "./MatdagongMark";

/** 소셜 로그인을 마치지 못하고 돌아왔을 때(?error)의 화면입니다(보드 02-03). 다시 로그인하면 처음 로그인 화면으로 돌아갑니다. */
export default function LoginError({ onRetry }: { onRetry: () => void }) {
  return (
    <section className="auth-screen" aria-labelledby="login-error-title">
      <MatdagongMark />
      <div className="auth-screen__body" role="alert">
        <span className="login-error__mark" aria-hidden="true">
          !
        </span>
        <h1 id="login-error-title">로그인할 수 없어요</h1>
        <p>
          네트워크 연결이 원활하지 않거나
          <br />
          일시적으로 인증에 문제가 발생했어요.
          <br />
          다시 로그인해 주세요.
        </p>
      </div>
      <div className="auth-screen__actions">
        <button type="button" className="auth-button is-primary" onClick={onRetry}>
          다시 로그인
        </button>
      </div>
    </section>
  );
}

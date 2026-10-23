export default function AccountFooter() {
  return (
    <footer className="account-footer">
      <div className="shared-account">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <rect x="5" y="10" width="14" height="11" rx="3" />
          <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
        </svg>
        <span>하나의 다공 계정으로 함께해요</span>
      </div>
      <p>
        맛다공 <span>·</span> 여다공
      </p>
    </footer>
  );
}

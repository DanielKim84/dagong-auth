const ASSET_BASE = `${import.meta.env.BASE_URL}assets/`;

/** 다공 공통 계정 로그인 화면 위쪽입니다(보드 02-02): 다공 D 가로 로고, 제목, 두 줄 설명. */
export default function DagongIntro() {
  return (
    <header className="dagong-intro">
      <img
        className="dagong-intro__logo"
        src={`${ASSET_BASE}brand/brand-logo.png`}
        width="2172"
        height="724"
        alt="dagong"
      />
      <h1>
        다공 계정으로
        <br />
        맛다공을 시작하세요
      </h1>
      <p>
        이미 다공 계정이 있다면
        <br />
        지금 바로 로그인할 수 있어요.
      </p>
    </header>
  );
}

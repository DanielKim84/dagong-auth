const assetBase = `${import.meta.env.BASE_URL}assets/`;

/** 맛다공 로고, 한 줄 소개, 프라이팬을 든 캐릭터를 차례로 보여줍니다. */
export default function LoginHero() {
  return (
    <header className="login-hero">
      <h1 className="sr-only">맛다공, 맛있는 순간을 함께하는 사람들</h1>
      <img
        className="brand-logo"
        src={`${assetBase}matdagong-logo.png`}
        width="660"
        height="330"
        alt="맛다공"
      />
      <p className="tagline">맛있는 순간을 함께하는 사람들</p>
      <img
        className="hero-mascot"
        src={`${assetBase}matdagong-chef.png`}
        width="400"
        height="400"
        alt="프라이팬을 든 맛다공 캐릭터"
      />
    </header>
  );
}

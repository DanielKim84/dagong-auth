export default function LoginHero() {
  return (
    <header className="login-hero">
      <h1 className="sr-only">맛다공, 맛있는 걸 함께하는 공간</h1>
      <img
        className="hero-art"
        src={`${import.meta.env.BASE_URL}assets/login-hero.png`}
        width="1208"
        height="1302"
        alt="따뜻한 주방 배경 안의 맛다공 로고와 요리사 캐릭터"
      />
    </header>
  );
}

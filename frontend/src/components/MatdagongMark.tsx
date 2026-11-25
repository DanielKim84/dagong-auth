const ASSET_BASE = `${import.meta.env.BASE_URL}assets/`;

/** 맛다공 세로 로고(입체 심볼 위, "맛다공" 글자 아래)입니다. 로그인 오류·다른 기기 확인 화면 위쪽에 둡니다(보드 02-03·02-04). */
export default function MatdagongMark() {
  return (
    <div className="matdagong-mark">
      <img src={`${ASSET_BASE}matdagong-symbol.png`} width="512" height="512" alt="" />
      <b>맛다공</b>
    </div>
  );
}

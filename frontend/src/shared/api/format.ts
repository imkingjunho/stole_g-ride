export const won = (amount: number) => `${amount.toLocaleString('ko-KR')}원`;
export const koreaTime = (value: string) =>
  new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}+09:00`);
export const koreanDate = (value: string) =>
  koreaTime(value).toLocaleDateString('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

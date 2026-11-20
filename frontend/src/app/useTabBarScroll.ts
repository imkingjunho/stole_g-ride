import { useEffect, useState } from 'react';

export function useTabBarScroll(pathname: string) {
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    setHidden(false);
    const root = document.scrollingElement ?? document.documentElement;
    let source = root;
    let previousTop = root.scrollTop;
    let previousLimit = root.scrollHeight - root.clientHeight;
    let travel = 0;

    function onScroll(event: Event) {
      const element = event.target === document ? root : event.target;
      if (!(element instanceof Element) || (element !== root && !element.matches('.room-log'))) {
        return;
      }
      const limit = Math.max(0, element.scrollHeight - element.clientHeight);
      const top = Math.max(0, Math.min(limit, element.scrollTop));
      // 새 메시지나 입력창 크기 변경으로 달라진 채팅 위치는 스크롤 방향에 포함하지 않는다.
      const layoutChanged = element !== source || (element !== root && limit !== previousLimit);
      const delta = layoutChanged ? 0 : top - previousTop;
      source = element;
      previousTop = top;
      previousLimit = limit;
      if (layoutChanged || travel * delta < 0) travel = 0;
      travel += delta;

      if (top <= 2 || travel <= -12) {
        setHidden(false);
        travel = 0;
      } else if (travel >= 18) {
        setHidden(true);
        travel = 0;
      }
    }
    document.addEventListener('scroll', onScroll, { capture: true, passive: true });
    return () => document.removeEventListener('scroll', onScroll, true);
  }, [pathname]);

  return hidden;
}

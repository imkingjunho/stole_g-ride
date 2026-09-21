import { useEffect, useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { Avatar, Badge, Button, Card, Icon } from '../../shared/ui';
import { PreviewState } from '../../shared/preview/PreviewState';

export function ChatPage() {
  // 입력 문장을 이 탭에 전시할 뿐 서버나 다른 사용자에게 전송하지 않는다.
  const [bubbles, setBubbles] = useState<string[]>([]);
  const [draft, setDraft] = useState('');
  const list = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (list.current) list.current.scrollTop = list.current.scrollHeight;
  }, [bubbles]);
  function addPreview(event?: FormEvent) {
    event?.preventDefault();
    const text = draft.trim();
    if (!text) return;
    setBubbles((previous) => [...previous, text.slice(0, 500)]);
    setDraft('');
  }
  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (
      event.key === 'Enter' &&
      !event.shiftKey &&
      !event.nativeEvent.isComposing &&
      event.keyCode !== 229
    ) {
      event.preventDefault();
      addPreview();
    }
  }
  return (
    <div className="page-enter chat-page">
      <div className="page-heading">
        <span className="eyebrow">만날 장소를 함께 정해요</span>
        <h1>오늘의 동행 채팅</h1>
        <p>닉네임으로 편하게 이야기해요.</p>
      </div>
      <PreviewState emptyTitle="아직 대화가 없어요">
        <Card className="chat-card">
          <header className="chat-header">
            <div className="stacked-avatars">
              <Avatar label="후문" small />
              <Avatar label="초록" tone="peach" small />
              <Avatar label="노을" tone="lavender" small />
            </div>
            <div>
              <h2>전남대 후문에서 함께</h2>
              <p>후문산책러 · 초록발걸음 · 노을따라</p>
            </div>
            <Link to="/groups/demo" className="icon-button" aria-label="동승 정보 보기">
              <Icon name="info" />
            </Link>
          </header>
          <div className="chat-privacy">
            <Icon name="lock" />
            <span>대화 보관·삭제 안내가 표시될 자리예요.</span>
            <Badge tone="neutral">시안</Badge>
          </div>
          <div
            className="chat-messages"
            ref={list}
            role="log"
            aria-label="예시 대화"
            aria-live="polite"
            aria-relevant="additions"
          >
            <p className="chat-date">화면 구성을 위한 예시 대화</p>
            <div className="system-message">세 명의 동행이 모였어요. 반갑게 인사해 볼까요?</div>
            <div className="chat-row">
              <Avatar label="초록" tone="peach" small />
              <div>
                <span className="sender-label">초록발걸음</span>
                <div className="bubble">안녕하세요! 후문 앞 편의점에서 만날까요?</div>
              </div>
              <time>17:20</time>
            </div>
            <div className="chat-row chat-row-me">
              <time>17:21</time>
              <div>
                <span className="sender-label">나 · 예시</span>
                <div className="bubble">좋아요! 저는 편의점 앞에 있어요.</div>
              </div>
            </div>
            <div className="chat-row">
              <Avatar label="노을" tone="lavender" small />
              <div>
                <span className="sender-label">노을따라</span>
                <div className="bubble">저도 곧 도착해요. 같이 가요!</div>
              </div>
              <time>17:21</time>
            </div>
            {bubbles.map((text, index) => (
              <div className="chat-row chat-row-me" key={index}>
                <div>
                  <span className="sender-label">내 말풍선 미리보기</span>
                  <div className="bubble">{text}</div>
                </div>
              </div>
            ))}
          </div>
          <form className="chat-composer" onSubmit={addPreview}>
            <label className="sr-only" htmlFor="chat-draft">
              말풍선에 넣어 볼 문장
            </label>
            <textarea
              id="chat-draft"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={onKeyDown}
              maxLength={500}
              rows={2}
              placeholder="문장을 입력해 말풍선을 확인해 보세요"
            />
            <Button type="submit" disabled={!draft.trim()} aria-label="말풍선 미리보기 추가">
              <Icon name="send" />
            </Button>
            <div className="composer-note">
              <span>이 화면에만 표시되며 실제로 전송되지 않아요.</span>
              <span>{draft.length}/500</span>
            </div>
          </form>
        </Card>
      </PreviewState>
    </div>
  );
}

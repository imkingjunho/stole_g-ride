import { useEffect, useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { Avatar, Icon } from '../../shared/ui';
import { PreviewState } from '../../shared/preview/PreviewState';

export function ChatPage() {
  // 입력 문장을 이 탭에만 표시한다. 서버나 다른 사용자에게 전송하지 않는다.
  const [bubbles, setBubbles] = useState<string[]>([]);
  const [draft, setDraft] = useState('');
  const list = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
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
      <h1 className="sr-only">오늘의 동행 채팅</h1>
      <PreviewState emptyTitle="아직 대화가 없어요">
        <div className="chat-privacy">
          <Icon name="lock" />
          <p>
            <strong>편하게 만나고, 안전하게 이동해요</strong>
            <span>이 대화는 시안이에요. 새로고침하면 입력한 말풍선이 사라져요.</span>
          </p>
        </div>
        <div
          className="chat-messages"
          ref={list}
          role="log"
          aria-label="예시 대화"
          aria-live="polite"
          aria-relevant="additions"
        >
          <p className="chat-date">
            <span>9월 18일 · 예시 대화</span>
          </p>
          <Link to="/groups/demo" className="system-message">
            <span className="success-icon">
              <Icon name="check" />
            </span>
            <span>
              <strong>매칭이 성사됐어요</strong>
              <small>전남대 후문에서 함께 출발해요</small>
            </span>
            <Icon name="chevron" />
          </Link>
          <div className="chat-row">
            <Avatar label="초록" tone="peach" small />
            <div className="message-content">
              <span className="sender-label">초록발걸음</span>
              <div className="bubble">안녕하세요! 후문 앞 편의점에서 만날까요?</div>
            </div>
            <time>17:20</time>
          </div>
          <div className="chat-row chat-row-me">
            <time>17:21</time>
            <div className="message-content">
              <span className="sr-only">나 · 예시</span>
              <div className="bubble">좋아요! 저는 편의점 앞에 있어요.</div>
            </div>
          </div>
          <div className="chat-row">
            <Avatar label="노을" tone="lavender" small />
            <div className="message-content">
              <span className="sender-label">노을따라</span>
              <div className="bubble">
                저도 5분 안에 도착해요.
                <br />
                같이 가요!
              </div>
            </div>
            <time>17:21</time>
          </div>
          <p className="chat-inline-note">
            <Icon name="shield" width="14" height="14" />
            만날 장소는 공개된 곳으로 정해 주세요.
          </p>
          {bubbles.map((text, index) => (
            <div className="chat-row chat-row-me" key={index}>
              <div className="message-content">
                <span className="sender-label">내 말풍선 미리보기</span>
                <div className="bubble">{text}</div>
              </div>
            </div>
          ))}
        </div>
        <div className="composer-area">
          <div className="quick-replies" aria-label="빠른 문장 선택">
            {(
              [
                { text: '도착했어요', icon: 'check' },
                { text: '5분 늦어요', icon: 'clock' },
                { text: '출발할까요?', icon: 'car' },
              ] as const
            ).map(({ text, icon }) => (
              <button
                key={text}
                type="button"
                onClick={() => {
                  setDraft(text);
                  input.current?.focus();
                }}
              >
                {text} <Icon name={icon} width="12" height="12" />
              </button>
            ))}
          </div>
          <form className="chat-composer" onSubmit={addPreview}>
            <label className="sr-only" htmlFor="chat-draft">
              말풍선에 넣어 볼 문장
            </label>
            <textarea
              ref={input}
              id="chat-draft"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={onKeyDown}
              maxLength={500}
              rows={1}
              placeholder="메시지를 입력해 보세요"
            />
            <button
              className="send-button"
              type="submit"
              disabled={!draft.trim()}
              aria-label="말풍선 미리보기 추가"
            >
              <Icon name="arrow" />
            </button>
            <div className="composer-note">
              <span>이 화면에만 표시돼요 · 실제 전송 없음</span>
              <span>{draft.length}/500</span>
            </div>
          </form>
        </div>
      </PreviewState>
    </div>
  );
}

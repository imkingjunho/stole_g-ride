import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Avatar, Badge, Button, Card, Icon, Modal } from '../../shared/ui';
import { historyExhibit } from '../../shared/preview/fixtures';
import { PreviewState } from '../../shared/preview/PreviewState';

export function HistoryPage() {
  const [selected, setSelected] = useState<number | null>(null);
  const item = selected === null ? null : historyExhibit[selected];
  return (
    <div className="page-enter">
      <div className="page-heading heading-with-action">
        <div>
          <span className="eyebrow">차곡차곡 쌓이는 동행</span>
          <h1>나의 이용 이력</h1>
          <p>함께 이동했던 순간을 모아 봤어요.</p>
        </div>
        <Link to="/me" className="button button-secondary">
          <Icon name="user" />내 프로필
        </Link>
      </div>
      <PreviewState emptyTitle="아직 함께한 동행이 없어요">
        <div className="history-summary">
          <Card>
            <span className="summary-icon">
              <Icon name="spark" />
            </span>
            <div>
              <span>예시 내역의 절감액 합계</span>
              <strong>
                14,200<small>원</small>
              </strong>
            </div>
          </Card>
          <Card>
            <span className="summary-icon summary-icon-beige">
              <Icon name="people" />
            </span>
            <div>
              <span>예시 동승 횟수</span>
              <strong>
                3<small>회</small>
              </strong>
            </div>
          </Card>
        </div>
        <div className="section-title history-list-title">
          <h2>최근 동승</h2>
          <Badge tone="neutral">실제 이용 기록이 아닌 예시</Badge>
        </div>
        <div className="history-list">
          {historyExhibit.map((record, index) => (
            <Card key={record.id} className="history-item">
              <div className="history-date">
                <Icon name="history" />
                <span>{record.date}</span>
                <Badge>완료 예시</Badge>
              </div>
              <div className="history-item-content">
                <div>
                  <h3>
                    전남대 후문 <Icon name="arrow" /> {record.destination}
                  </h3>
                  <p>
                    <Icon name="people" />
                    {record.people}명 동승
                  </p>
                </div>
                <div className="history-amount">
                  <strong>{record.paid}원</strong>
                  <span>{record.saving}원 절약</span>
                </div>
                <button
                  className="icon-button"
                  aria-label={`${record.destination} 예시 내역 상세 보기`}
                  onClick={() => setSelected(index)}
                >
                  <Icon name="chevron" />
                </button>
              </div>
            </Card>
          ))}
        </div>
      </PreviewState>
      <Modal open={item !== null} onClose={() => setSelected(null)} title="동승 내역 예시">
        {item && (
          <>
            <p className="dialog-copy">
              전남대 후문 → {item.destination}
              <br />
              {item.date} · {item.people}명 동승
            </p>
            <div className="info-box">
              예상 분담액 {item.paid}원 · 예상 절감액 {item.saving}원
            </div>
            {selected === 0 && (
              <Link
                className="button button-primary full-width"
                to="/groups/demo/fare"
                onClick={() => setSelected(null)}
              >
                구간별 정산 시안 보기
              </Link>
            )}
            <Button variant="secondary" className="full-width" onClick={() => setSelected(null)}>
              닫기
            </Button>
          </>
        )}
      </Modal>
    </div>
  );
}

export function ProfilePage() {
  return (
    <div className="page-enter profile-page">
      <Link className="back-link" to="/history">
        <Icon name="back" />
        이용 이력
      </Link>
      <div className="page-heading">
        <span className="eyebrow">MY PROFILE</span>
        <h1>내 프로필</h1>
        <p>프로필 배치를 확인하는 예시 화면이에요.</p>
      </div>
      <PreviewState>
        <Card className="profile-card">
          <Avatar label="후문" />
          <h2>후문산책러</h2>
          <Badge tone="neutral">가상 프로필</Badge>
          <dl>
            <div>
              <dt>소속</dt>
              <dd>전남대학교</dd>
            </div>
            <div>
              <dt>학교 이메일</dt>
              <dd>demo@jnu.ac.kr</dd>
            </div>
            <div>
              <dt>학과·학년</dt>
              <dd>입력하지 않음</dd>
            </div>
          </dl>
          <p className="micro-note">입력한 회원가입 정보와 연결되지 않은 시안이에요.</p>
          <Link to="/signup/profile" className="button button-secondary full-width">
            프로필 입력 화면 보기
          </Link>
        </Card>
      </PreviewState>
    </div>
  );
}

import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge, Button, Card, Icon, Modal } from '../../shared/ui';
import { historyExhibit } from '../../shared/preview/fixtures';
import { PreviewState } from '../../shared/preview/PreviewState';

const totalSaving = historyExhibit
  .reduce((sum, record) => sum + Number(record.saving.replaceAll(',', '')), 0)
  .toLocaleString('ko-KR');

export function HistoryPage() {
  const [selected, setSelected] = useState<number | null>(null);
  const [month, setMonth] = useState('전체');
  const item = selected === null ? null : historyExhibit[selected];
  const filtered = historyExhibit
    .map((record, index) => ({ record, index }))
    .filter(({ record }) => month === '전체' || record.date.startsWith(month));
  return (
    <div className="page-enter history-page">
      <h1 className="sr-only">나의 이용 이력</h1>
      <PreviewState emptyTitle="아직 함께한 동행이 없어요">
        <Card className="history-summary">
          <div>
            <span className="summary-label">
              <Icon name="spark" width="16" height="16" />
              함께 타서 가벼워진 이동
            </span>
            <h2>
              <strong>
                {totalSaving}
                <small>원</small>
              </strong>{' '}
              아꼈어요!
            </h2>
            <p>{historyExhibit.length}번의 동행이 쌓인 예시 기록이에요</p>
          </div>
          <div className="history-car" aria-hidden="true">
            <Icon name="car" width="35" height="35" />
          </div>
        </Card>
        <div className="filter-chips" role="group" aria-label="이용 이력 기간">
          {['전체', '9월', '8월'].map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={month === value}
              onClick={() => setMonth(value)}
            >
              {value}
            </button>
          ))}
        </div>
        <div className="history-list">
          {filtered.map(({ record, index }) => (
            <Card key={record.id} className="history-item">
              <div className="history-date">
                <span>{record.date}</span>
                <Badge tone="blue">{record.people}명 동승</Badge>
              </div>
              <button
                type="button"
                className="history-route"
                aria-label={`${record.destination} 예시 내역 상세 보기`}
                onClick={() => setSelected(index)}
              >
                <span className="route-bullet" />
                <strong>
                  전남대 후문 <Icon name="arrow" width="15" height="15" />
                  {record.destination}
                </strong>
                <Icon name="chevron" width="17" height="17" />
              </button>
              <div className="history-amount">
                <div>
                  <small>내 분담액</small>
                  <strong>{record.paid}원</strong>
                </div>
                <span className="saving-pill">
                  <Icon name="spark" width="14" height="14" />
                  {record.saving}원 절약
                </span>
              </div>
            </Card>
          ))}
        </div>
        {filtered.length === 0 ? (
          <Card className="state-card period-empty">
            <span className="state-icon">
              <Icon name="history" />
            </span>
            <h2>{month}에는 동승 내역이 없어요</h2>
            <p>다른 기간의 예시 기록을 확인해 보세요.</p>
            <Button variant="secondary" onClick={() => setMonth('전체')}>
              전체 내역 보기
            </Button>
          </Card>
        ) : (
          <div className="history-end">
            <span>
              <Icon name="check" width="17" height="17" />
            </span>
            <p>모든 예시 이력을 확인했어요</p>
          </div>
        )}
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
  const [aboutOpen, setAboutOpen] = useState(false);
  return (
    <div className="page-enter profile-page">
      <h1 className="sr-only">내 프로필</h1>
      <PreviewState>
        <Card className="profile-card">
          <span className="profile-avatar">
            후문
            <span>
              <Icon name="check" width="10" height="10" />
            </span>
          </span>
          <div>
            <h2>후문산책러</h2>
            <p>demo@jnu.ac.kr</p>
          </div>
          <Badge tone="blue">가상 프로필</Badge>
        </Card>
        <Card className="profile-savings">
          <div className="section-title">
            <span>함께 아낀 이동비</span>
            <Badge tone="blue">누적 예시</Badge>
          </div>
          <div className="profile-saving-amount">
            {totalSaving}
            <small>원</small>
          </div>
          <div className="profile-stats">
            <div>
              <span>함께한 횟수</span>
              <strong>
                {historyExhibit.length}
                <small>회</small>
              </strong>
            </div>
            <div>
              <span>소속 캠퍼스</span>
              <strong>전남대학교</strong>
            </div>
          </div>
        </Card>
        <div className="profile-banner">
          <Icon name="spark" />
          <p>
            같은 방향의 학우와 함께,
            <br />
            이동비도 조금씩 가벼워져요.
          </p>
        </div>
        <Card className="profile-menu">
          <Link className="menu-row" to="/signup/profile">
            <Icon name="user" />
            <span>프로필 입력 화면</span>
            <Icon name="chevron" />
          </Link>
          <Link className="menu-row" to="/history">
            <Icon name="history" />
            <span>이용 이력</span>
            <Icon name="chevron" />
          </Link>
          <button className="menu-row" onClick={() => setAboutOpen(true)}>
            <Icon name="info" />
            <span>서비스 안내</span>
            <Icon name="chevron" />
          </button>
          <Link className="menu-row muted" to="/login">
            <Icon name="back" />
            <span>로그인 화면으로</span>
            <Icon name="chevron" />
          </Link>
        </Card>
        <Card className="profile-note">
          <Icon name="lock" />
          <p>화면 확인용 프로필이에요. 회원가입 화면에 입력한 정보와 연결되지 않아요.</p>
        </Card>
      </PreviewState>
      <Modal open={aboutOpen} onClose={() => setAboutOpen(false)} title="함께 가는 캠퍼스, 가치가">
        <p className="dialog-copy">
          가치가는 전남대 학우의 동승자 매칭과 구간별 요금 분담을 돕는 프로젝트예요. 택시
          호출·운송·결제를 중개하지 않아요.
        </p>
        <div className="info-box">현재는 예시 데이터로 화면과 이동 흐름을 확인하는 시안이에요.</div>
        <Button className="full-width" onClick={() => setAboutOpen(false)}>
          확인했어요
        </Button>
      </Modal>
    </div>
  );
}

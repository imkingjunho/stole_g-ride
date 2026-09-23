import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Avatar, Badge, Button, Card, Icon, Modal, useToast } from '../../shared/ui';
import { fareExhibit, participants } from '../../shared/preview/fixtures';
import { PreviewState } from '../../shared/preview/PreviewState';

export function GroupPage() {
  const [completeOpen, setCompleteOpen] = useState(false);
  const [completed, setCompleted] = useState(false);
  const notify = useToast();
  return (
    <div className="page-enter group-page">
      <div className="match-heading">
        <span className="success-icon">
          <Icon name="check" />
        </span>
        <h1>{completed ? '동행을 마쳤어요.' : '함께 갈 학우를 찾았어요!'}</h1>
        <p>같은 출발지에서 시작하는 가벼운 동행</p>
        <Badge tone="blue">
          <Icon name="pin" width="13" height="13" />
          전남대 후문
        </Badge>
      </div>
      <PreviewState emptyTitle="아직 성사된 동승이 없어요">
        <Card className="fare-hero" aria-label="예상 분담액">
          <Badge tone="blue">
            <Icon name="people" width="14" height="14" />
            3명 매칭 완료
          </Badge>
          <p>내 예상 분담액</p>
          <span className="solo-fare">
            혼자 탈 때 <del>{fareExhibit.solo}원</del>
          </span>
          <div className="hero-amount">
            {fareExhibit.share}
            <span>원</span>
          </div>
          <span className="saving-pill">
            <Icon name="spark" width="15" height="15" />
            {fareExhibit.saving}원 아꼈어요 <b>{fareExhibit.savingPercent}%</b>
          </span>
          <p className="micro-note">구간별 요금 분담을 적용한 예시 금액이에요.</p>
        </Card>
        <Card className="route-card">
          <div className="section-title">
            <h2>함께 가는 경로</h2>
            <span>총 10.0 km · 예시</span>
          </div>
          <div
            className="route-track"
            aria-label="전남대 후문 출발, 경신여고 1번 하차, 유스퀘어 내 하차, 광주송정역 3번 하차"
          >
            {['전남대 후문', '경신여고', '유스퀘어', '광주송정역'].map((stop, index) => (
              <div key={stop} className={index === 2 ? 'stop stop-mine' : 'stop'}>
                <span className="stop-dot">
                  {index === 0 ? <Icon name="car" width="15" height="15" /> : index}
                </span>
                <strong>{stop}</strong>
                <small>
                  {index === 0 ? '함께 출발' : index === 2 ? '내 하차' : `${index}번째 하차`}
                </small>
              </div>
            ))}
          </div>
        </Card>
        <section className="companions-section">
          <div className="section-title">
            <h2>
              오늘의 동행 <span className="blue-text">3</span>
            </h2>
            <span>탑승 순서 · 모두 후문 출발</span>
          </div>
          <div className="companion-list">
            {participants.map((person, index) => (
              <Card key={person.name} className={`companion ${person.mine ? 'companion-me' : ''}`}>
                <Avatar label={person.name} tone={person.tone} />
                <div className="companion-info">
                  <strong>
                    {person.name}
                    {person.mine && <span className="me-label">나</span>}
                  </strong>
                  <span>
                    {index + 1}번째 하차 · {person.destination}
                  </span>
                </div>
                <div className="companion-fare">
                  <strong>
                    {person.fare}
                    <small>원</small>
                  </strong>
                  <span>예상 분담액</span>
                </div>
              </Card>
            ))}
          </div>
        </section>
        <Link to="/groups/demo/fare" className="card explanation-link">
          <span className="benefit-icon">
            <Icon name="calculator" />
          </span>
          <span>
            <strong>내 요금은 어떻게 정해졌나요?</strong>
            <small>함께 탄 구간만 공정하게 나눠요</small>
          </span>
          <Icon name="chevron" />
        </Link>
        <div className="group-actions">
          <Link to="/groups/demo/chat" className="button button-primary full-width">
            <Icon name="chat" />
            동승자와 채팅하기
          </Link>
          <Button
            variant="secondary"
            className="full-width"
            aria-label={completed ? '성사 화면으로 되돌리기' : '탑승 완료 안내 보기'}
            onClick={() => (completed ? setCompleted(false) : setCompleteOpen(true))}
          >
            <Icon name={completed ? 'refresh' : 'check'} />
            {completed ? '성사 화면으로 되돌리기' : '탑승을 완료했어요'}
          </Button>
        </div>
      </PreviewState>
      <Modal open={completeOpen} onClose={() => setCompleteOpen(false)} title="동행을 마치셨나요?">
        <div className="completion-art">
          <Icon name="check" width="30" height="30" />
        </div>
        <p className="dialog-copy">
          지금은 완료 상태의 화면만 미리 볼 수 있어요. 실제 이용 상태는 바뀌지 않아요.
        </p>
        <div className="dialog-actions">
          <Button variant="secondary" onClick={() => setCompleteOpen(false)}>
            돌아가기
          </Button>
          <Button
            onClick={() => {
              setCompleted(true);
              setCompleteOpen(false);
              notify('동승 완료 화면의 시안이에요.');
            }}
          >
            완료 화면 보기
          </Button>
        </div>
      </Modal>
    </div>
  );
}

export function FarePage() {
  const [helpOpen, setHelpOpen] = useState(false);
  return (
    <div className="page-enter fare-page">
      <div className="page-heading">
        <h1>
          실제로 탄 구간만
          <br />
          나눠서 계산했어요
        </h1>
        <p>내가 내린 뒤의 요금은 부담하지 않아요.</p>
        <div className="fare-overview">
          <Badge tone="neutral">총 10.0 km</Badge>
          <strong>{fareExhibit.total}원</strong>
          <span>예시 요금</span>
        </div>
      </div>
      <PreviewState>
        <Card className="distance-card">
          <div className="distance-bar" aria-label="첫 두 구간 6km 탑승, 마지막 4km 미탑승">
            <div style={{ flex: 3 }}>3 km</div>
            <div style={{ flex: 3 }}>3 km</div>
            <div style={{ flex: 4 }}>4 km</div>
          </div>
          <div className="distance-labels">
            <span>후문 (출발)</span>
            <span>경신여고</span>
            <span className="blue-text">유스퀘어 (나)</span>
            <span>송정역</span>
          </div>
        </Card>
        <div className="segments">
          {fareExhibit.segments.map((segment, index) => (
            <Card
              key={segment.id}
              className={`segment ${!segment.active ? 'segment-inactive' : ''}`}
            >
              <div className="segment-heading">
                <h2>
                  <i />
                  구간 {index + 1} · {segment.route}
                </h2>
                <Badge tone={segment.active ? 'blue' : 'neutral'}>{segment.people}명</Badge>
              </div>
              <div className="segment-calculation">
                <span>
                  {segment.distance} · {segment.fare}원 ÷ {segment.people}명
                </span>
                <strong>
                  <small>내 몫</small>
                  {segment.mine}원
                </strong>
              </div>
              {!segment.active && <p className="segment-note">하차 후 구간 · 내 분담액 0원</p>}
            </Card>
          ))}
        </div>
        <section className="fare-people">
          <div className="section-title">
            <h2>사람별 합계</h2>
            <span>100원 단위 보정 후</span>
          </div>
          <Card>
            {participants.map((person) => (
              <div
                key={person.name}
                className={`person-total ${person.mine ? 'person-total-me' : ''}`}
              >
                <div>
                  <Avatar label={person.name} tone={person.tone} small />
                  <strong>{person.name}</strong>
                  {person.mine && <span className="me-label">나</span>}
                  <b>{person.fare}원</b>
                </div>
                <div className="fare-progress" aria-hidden="true">
                  <span style={{ width: `${(person.amount / 15000) * 100}%` }} />
                </div>
              </div>
            ))}
            <div className="group-total">
              <span>전체 분담액 합계</span>
              <strong>{fareExhibit.total}원</strong>
            </div>
          </Card>
        </section>
        <Card className="receipt-card">
          <h2>내 요금 계산</h2>
          <dl>
            <div>
              <dt>함께 탄 S1 + S2 구간</dt>
              <dd>{fareExhibit.beforeRounding}원</dd>
            </div>
            <div>
              <dt>
                <button className="inline-info" onClick={() => setHelpOpen(true)}>
                  100원 단위 보정
                  <Icon name="info" width="16" height="16" />
                </button>
              </dt>
              <dd>{fareExhibit.adjustment}원</dd>
            </div>
          </dl>
          <div className="receipt-total">
            <span>최종 분담액</span>
            <strong>
              {fareExhibit.share}
              <small>원</small>
            </strong>
          </div>
        </Card>
        <div className="fair-note">
          <span className="benefit-icon">
            <Icon name="calculator" />
          </span>
          <div>
            <strong>똑같이 나누면 한 명당 5,000원</strong>
            <p>
              먼저 내린 사람은 적게 부담해요.
              <br />
              가치가는 함께 탄 구간만 나눠 내요.
            </p>
          </div>
        </div>
        <div className="dark-saving">
          <span>
            <Icon name="spark" />
            혼자 탈 때보다 아낀 금액
          </span>
          <strong>{fareExhibit.saving}원</strong>
        </div>
        <p className="micro-note">고정된 계산 예시이며, 실제 정산·결제는 진행하지 않아요.</p>
        <Link to="/groups/demo" className="button button-secondary full-width">
          동승 정보로 돌아가기
        </Link>
      </PreviewState>
      <Modal open={helpOpen} onClose={() => setHelpOpen(false)} title="100원 단위 보정이란?">
        <p className="dialog-copy">
          내 구간 분담액 3,750원을 100원 단위로 올려 3,800원으로 표시했어요. 전체 요금보다 늘어난
          금액은 분담액이 가장 큰 동승자에게서 차감해요.
        </p>
        <div className="info-box">1,500원 + 3,800원 + 9,700원 = 15,000원</div>
        <Button className="full-width" onClick={() => setHelpOpen(false)}>
          확인했어요
        </Button>
      </Modal>
    </div>
  );
}

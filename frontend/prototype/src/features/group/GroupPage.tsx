import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Avatar, Badge, Button, Card, Icon, Modal, useToast } from '../../shared/ui';
import { fareExhibit } from '../../shared/preview/fixtures';
import { PreviewState } from '../../shared/preview/PreviewState';

function PageHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <div className="page-heading">
      <span className="eyebrow">{eyebrow}</span>
      <h1>{title}</h1>
      <p>{description}</p>
    </div>
  );
}

export function GroupPage() {
  const [completeOpen, setCompleteOpen] = useState(false);
  const [completed, setCompleted] = useState(false);
  const notify = useToast();
  return (
    <div className="page-enter">
      <PageHeading
        eyebrow="같은 방향으로, 함께"
        title={completed ? '동행을 마쳤어요.' : '동승자가 모였어요!'}
        description="전남대 후문에서 함께 출발하는 동행이에요."
      />
      <PreviewState emptyTitle="아직 성사된 동승이 없어요">
        <div className="group-layout">
          <div className="group-primary">
            <section className="savings-card" aria-label="예상 분담액">
              <div className="savings-top">
                <span>
                  <Icon name="spark" />
                  함께 나누는 이동
                </span>
                <Badge tone="neutral">예시 요금</Badge>
              </div>
              <p>내 예상 분담액</p>
              <div className="savings-amount">
                {fareExhibit.share}
                <span>원</span>
                <span className="saving-percent">
                  {fareExhibit.savingPercent}%<small>절약</small>
                </span>
              </div>
              <div className="savings-compare">
                <span>
                  혼자라면 <del>{fareExhibit.solo}원</del>
                </span>
                <strong>{fareExhibit.saving}원 가벼워졌어요</strong>
              </div>
              <Link to="/groups/demo/fare" className="savings-detail">
                내 분담액은 어떻게 정해졌나요?
                <Icon name="arrow" />
              </Link>
            </section>
            <Card className="route-card">
              <div className="section-title">
                <h2>
                  <Icon name="pin" />
                  함께 가는 경로
                </h2>
                <span>경로 예시</span>
              </div>
              <div className="route-endpoints">
                <div>
                  <small>함께 출발</small>
                  <strong>전남대 후문</strong>
                </div>
                <Icon name="arrow" />
                <div>
                  <small>내 목적지</small>
                  <strong>유스퀘어</strong>
                </div>
              </div>
              <div className="map-placeholder">
                <span>
                  <Icon name="map" width="32" height="32" />
                </span>
                <strong>경로 지도가 들어갈 자리</strong>
                <p>지도 연동 전, 화면 배치만 확인해요.</p>
              </div>
              <div className="route-footnote">
                <Icon name="info" />
                <span>실제 경로·도착 시간은 지도 연결 후 표시돼요.</span>
              </div>
            </Card>
          </div>
          <div className="group-secondary">
            <Card className="companions-card">
              <div className="section-title">
                <h2>오늘의 동행</h2>
                <Badge>3명</Badge>
              </div>
              <p className="muted small">모두 전남대 후문에서 함께 탑승해요.</p>
              <div className="companion">
                <Avatar label="초록" tone="peach" />
                <div>
                  <strong>초록발걸음</strong>
                  <span>첫 번째 하차 · 1,500원</span>
                </div>
                <span className="order-number">1</span>
              </div>
              <div className="companion companion-me">
                <Avatar label="후문" />
                <div>
                  <strong>
                    후문산책러 <span className="me-label">나</span>
                  </strong>
                  <span>두 번째 하차 · 3,800원</span>
                </div>
                <span className="order-number">2</span>
              </div>
              <div className="companion">
                <Avatar label="노을" tone="lavender" />
                <div>
                  <strong>노을따라</strong>
                  <span>세 번째 하차 · 9,700원</span>
                </div>
                <span className="order-number">3</span>
              </div>
              <div className="group-total">
                <span>예상 요금 합계</span>
                <strong>{fareExhibit.total}원</strong>
              </div>
            </Card>
            <Card className="meeting-note">
              <span className="note-icon">
                <Icon name="chat" />
              </span>
              <h2>만날 장소를 정해 볼까요?</h2>
              <p>동승자와 대화하며 정확한 탑승 장소를 정할 수 있어요.</p>
              <Link to="/groups/demo/chat" className="button button-primary full-width">
                <Icon name="chat" />
                채팅 화면 보기
                <Icon name="arrow" />
              </Link>
            </Card>
            <Button
              variant="secondary"
              className="full-width"
              onClick={() => (completed ? setCompleted(false) : setCompleteOpen(true))}
            >
              {completed ? '성사 화면으로 되돌리기' : '탑승 완료 안내 보기'}
              <Icon name="check" />
            </Button>
            <p className="micro-note">모든 인물과 금액은 화면 구성을 위한 예시예요.</p>
          </div>
        </div>
      </PreviewState>
      <Modal open={completeOpen} onClose={() => setCompleteOpen(false)} title="동행을 마치셨나요?">
        <div className="completion-art">
          <Icon name="check" width="30" height="30" />
        </div>
        <p className="dialog-copy">
          실제 서비스에서는 동승 완료를 확인하는 단계예요. 지금은 완료 상태의 화면만 미리 볼 수
          있어요.
        </p>
        <div className="dialog-actions">
          <Button variant="secondary" onClick={() => setCompleteOpen(false)}>
            돌아가기
          </Button>
          <Button
            onClick={() => {
              setCompleted(true);
              setCompleteOpen(false);
              notify('완료 화면 시안입니다. 실제 이용 상태는 변경하지 않았어요.');
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
    <div className="page-enter">
      <Link className="back-link" to="/groups/demo">
        <Icon name="back" />
        매칭 성사 화면
      </Link>
      <PageHeading
        eyebrow="어디까지 함께 탔는지에 따라"
        title="내가 탄 만큼, 공정하게."
        description="각 구간의 요금을 함께 탄 인원으로 나눠요."
      />
      <PreviewState>
        <div className="fare-layout">
          <Card className="fare-main">
            <div className="section-title">
              <h2>구간별 요금 안내</h2>
              <Badge tone="neutral">예시</Badge>
            </div>
            <div className="fare-legend">
              <span>
                <i />
                내가 탑승한 구간
              </span>
              <span>
                <i />
                내가 내린 이후
              </span>
            </div>
            <div className="distance-bar" aria-label="첫 두 구간 6km 탑승, 마지막 4km 미탑승">
              <div style={{ flex: 3 }}>S1 · 3 km</div>
              <div style={{ flex: 3 }}>S2 · 3 km</div>
              <div style={{ flex: 4 }}>S3 · 4 km</div>
            </div>
            <div className="segments">
              {fareExhibit.segments.map((segment) => (
                <section
                  key={segment.id}
                  className={`segment ${segment.active ? 'segment-active' : ''}`}
                >
                  <div className="segment-index">{segment.id}</div>
                  <div className="segment-info">
                    <h3>{segment.route}</h3>
                    <p>
                      {segment.distance}
                      <span>·</span>
                      <Icon name="people" />
                      {segment.people}명 탑승
                    </p>
                    <div className="segment-calculation">
                      <span>구간 요금 {segment.fare}원</span>
                      <strong>내 몫 {segment.mine}원</strong>
                    </div>
                  </div>
                </section>
              ))}
            </div>
            <div className="fare-explanation">
              <Icon name="info" />
              <p>
                내가 내린 뒤의 구간 요금은 부담하지 않아요.
                <br />
                나는 S1과 S2 구간에만 함께 탔어요.
              </p>
            </div>
          </Card>
          <div className="fare-side">
            <Card className="receipt-card">
              <span className="eyebrow">MY SHARE</span>
              <h2>내 예상 분담액</h2>
              <dl>
                <div>
                  <dt>S1 · 3명과 함께</dt>
                  <dd>1,500원</dd>
                </div>
                <div>
                  <dt>S2 · 2명과 함께</dt>
                  <dd>2,250원</dd>
                </div>
                <div>
                  <dt>S3 · 하차 후</dt>
                  <dd>0원</dd>
                </div>
                <div className="receipt-subtotal">
                  <dt>구간 분담액 합계</dt>
                  <dd>{fareExhibit.beforeRounding}원</dd>
                </div>
                <div>
                  <dt>
                    <button className="inline-info" onClick={() => setHelpOpen(true)}>
                      100원 단위 보정 <Icon name="info" width="16" height="16" />
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
              <p className="receipt-saving">
                <Icon name="spark" />
                혼자 탈 때보다 {fareExhibit.saving}원 절약
              </p>
            </Card>
            <p className="micro-note">
              계산 결과를 보여주는 화면 시안이에요.
              <br />
              실제 정산 계산이나 결제는 진행하지 않아요.
            </p>
            <Link to="/groups/demo" className="button button-secondary full-width">
              동승 정보로 돌아가기
            </Link>
          </div>
        </div>
      </PreviewState>
      <Modal open={helpOpen} onClose={() => setHelpOpen(false)} title="100원 단위 보정이란?">
        <p className="dialog-copy">
          예시에서 내 구간 분담액 3,750원을 100원 단위로 올려 3,800원으로 표시했어요. 전체 요금보다
          늘어난 금액은 분담액이 가장 큰 동승자에게서 차감해요.
        </p>
        <div className="info-box">1,500원 + 3,800원 + 9,700원 = 15,000원</div>
        <Button className="full-width" onClick={() => setHelpOpen(false)}>
          확인했어요
        </Button>
      </Modal>
    </div>
  );
}

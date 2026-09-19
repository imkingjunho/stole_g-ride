/**
 * 모듈 간 알림용 이벤트 (PRD §14.2).
 *
 * <p><b>발행</b>은 {@code ApplicationEventPublisher#publishEvent} 로 한다.
 *
 * <pre>{@code
 * eventPublisher.publishEvent(new RideRequestCreated(requestId, userId, hubId));
 * }</pre>
 *
 * <p><b>수신</b>은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 로 한다. 커밋된 뒤에만
 * 받으므로 "저장은 롤백됐는데 알림만 나가는" 일이 없다.
 *
 * <pre>{@code
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * public void on(GroupConfirmed event) {
 *     try {
 *         // 채팅방 개설 등
 *     } catch (Exception e) {
 *         log.error("GroupConfirmed 처리 실패 groupId={}", event.groupId(), e);
 *     }
 * }
 * }</pre>
 *
 * <p><b>수신 측은 반드시 try/catch 로 감싼다.</b> 받는 쪽이 터져서 보내는 쪽 트랜잭션이 깨지면
 * 모듈을 나눈 의미가 없다 (CLAUDE.md §3).
 */
package com.gachiga.contract.event;

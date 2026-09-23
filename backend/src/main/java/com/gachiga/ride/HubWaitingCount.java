package com.gachiga.ride;

/**
 * 거점 하나의 대기 인원. {@link RideRequestRepository#countWaitingByHubIds} 의 결과 한 줄이다.
 *
 * <p>JPQL 생성자 식({@code select new ...})으로 만들어지므로 public 이어야 한다.
 *
 * @param hubId 거점 id
 * @param count 그 거점의 WAITING 요청 수
 */
public record HubWaitingCount(Long hubId, Long count) {}

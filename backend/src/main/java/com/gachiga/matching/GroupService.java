package com.gachiga.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;
import com.gachiga.contract.event.GroupProposed;
import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.*;
import com.gachiga.contract.user.UserPort;
import com.gachiga.matching.api.GroupResponse;
import com.gachiga.matching.domain.*;
import com.gachiga.matching.port.*;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {
    private final RideRequestPort rideRequestPort;
    private final UserPort userPort;
    private final MatchGroupRepository groups;
    private final MatchMemberRepository members;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    /** 일부 배정 실패도 전체 롤백한다. 이미 배정된 다른 요청을 수동으로 WAITING으로 되돌리지 않는다. */
    public Long create(MatchCandidate candidate) {
        String routeJson;
        try {
            routeJson = objectMapper.writeValueAsString(candidate.route());
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), e);
        }
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(
                status -> {
                    // DB 잠금 순서를 고정한다. 조건부 UPDATE가 PC를 비우므로 그룹 엔티티를 만들기 전에 끝낸다.
                    for (WaitingRequest r :
                            candidate.riders().stream()
                                    .sorted(Comparator.comparing(WaitingRequest::requestId))
                                    .toList()) {
                        if (!rideRequestPort.tryMarkMatched(r.requestId(), r.version())) {
                            status.setRollbackOnly();
                            return null;
                        }
                    }
                    HubInfo hub = candidate.hub();
                    RouteResult route = candidate.route();
                    MatchGroup group =
                            groups.save(
                                    MatchGroup.builder()
                                            .hubId(hub.id())
                                            .hubName(hub.name())
                                            .hubLat(hub.lat())
                                            .hubLng(hub.lng())
                                            .hubType(hub.type())
                                            .totalFare(route.totalFare())
                                            .totalDistance(route.totalDistance())
                                            .totalDuration(route.totalDuration())
                                            .estimated(route.estimated())
                                            .routeJson(routeJson)
                                            .build());
                    for (WaitingRequest r : candidate.riders()) {
                        int sharedDistance = candidate.sharedDistances().get(r.userId());
                        BigDecimal detour =
                                BigDecimal.valueOf(
                                                Math.max(
                                                        0L,
                                                        (long) sharedDistance - r.soloDistance()))
                                        .divide(
                                                BigDecimal.valueOf(r.soloDistance()),
                                                3,
                                                RoundingMode.HALF_UP);
                        members.save(
                                MatchMember.builder()
                                        .groupId(group.getId())
                                        .requestId(r.requestId())
                                        .userId(r.userId())
                                        .boardingOrder(1)
                                        .dropoffOrder(candidate.dropoffOrders().get(r.userId()))
                                        .destName(r.destName())
                                        .destLat(r.destination().lat())
                                        .destLng(r.destination().lng())
                                        .soloDistance(r.soloDistance())
                                        .soloFare(r.soloFare())
                                        .sharedDistance(sharedDistance)
                                        .detourRatio(detour)
                                        .shareAmount(candidate.shares().get(r.userId()))
                                        .savingAmount(candidate.savings().get(r.userId()))
                                        .accepted(true)
                                        .build());
                    }
                    group.confirm();
                    List<Long> userIds =
                            candidate.riders().stream().map(WaitingRequest::userId).toList();
                    // P0 자동 성사. 커밋 이후 ride가 groupId/CONFIRMED를, realtime이 알림·채팅을 처리한다.
                    events.publishEvent(new GroupProposed(group.getId(), userIds));
                    events.publishEvent(new GroupConfirmed(group.getId(), userIds));
                    return group.getId();
                });
    }

    @Transactional(readOnly = true)
    public GroupResponse detail(Long groupId, Long userId) {
        MatchGroup group =
                groups.findById(groupId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<MatchMember> roster = members.findByGroupId(groupId);
        requireMember(roster, userId);
        if (group.getRouteJson() == null
                || roster.stream()
                        .anyMatch(m -> m.getSoloDistance() == null || m.getDestName() == null)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "이전 버전 그룹에는 경로·정산 상세가 없습니다. 새 매칭을 이용해 주세요.");
        }
        RouteResult route;
        try {
            route = objectMapper.readValue(group.getRouteJson(), RouteResult.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), e);
        }
        var detailMembers =
                roster.stream()
                        .sorted(
                                Comparator.comparing(MatchMember::getDropoffOrder)
                                        .thenComparing(MatchMember::getUserId))
                        .map(
                                m ->
                                        new GroupResponse.MemberInfo(
                                                m.getUserId(),
                                                userPort.findById(m.getUserId())
                                                        .map(u -> u.nickname())
                                                        .orElse("알 수 없는 학우"),
                                                m.getUserId().equals(userId),
                                                m.getBoardingOrder(),
                                                m.getDropoffOrder(),
                                                m.getDestName(),
                                                m.getDestLat(),
                                                m.getDestLng(),
                                                m.getSoloDistance(),
                                                m.getShareAmount(),
                                                m.getSoloFare(),
                                                m.getSavingAmount(),
                                                m.getDetourRatio(),
                                                m.getAccepted()))
                        .toList();
        return new GroupResponse(
                group.getId(),
                group.getStatus().name(),
                group.isEstimated(),
                new HubInfo(
                        group.getHubId(),
                        group.getHubName(),
                        group.getHubLat(),
                        group.getHubLng(),
                        group.getHubType()),
                group.getTotalFare(),
                group.getTotalDistance(),
                group.getTotalDuration(),
                new GroupResponse.Route(route.sections()),
                detailMembers,
                null,
                group.getCreatedAt(),
                "DISTANCE_RATIO");
    }

    /** 각 멤버가 완료를 눌러야 전체 완료된다. 행 잠금으로 동시 완료·중복 이벤트를 막는다. */
    @Transactional
    public void complete(Long groupId, Long userId) {
        MatchGroup group =
                groups.findLockedById(groupId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<MatchMember> roster = members.findByGroupId(groupId);
        MatchMember member = requireMember(roster, userId);
        if (group.getStatus() == GroupStatus.COMPLETED) return;
        if (group.getStatus() != GroupStatus.CONFIRMED)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        member.complete();
        if (roster.stream().allMatch(m -> Boolean.TRUE.equals(m.getCompleted()))) {
            group.complete();
            events.publishEvent(
                    new GroupCompleted(
                            groupId, roster.stream().map(MatchMember::getUserId).toList()));
        }
    }

    private MatchMember requireMember(List<MatchMember> roster, Long userId) {
        return roster.stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_MEMBER));
    }
}

package com.gachiga.matching;

import com.gachiga.contract.event.GroupDissolved;
import java.time.LocalDateTime;
import java.util.Set;
import java.time.Clock;
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
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    public Set<String> offeredPairs(List<Long> requests) {
        return groups.offeredPairs(requests);
    }

    @org.springframework.beans.factory.annotation.Value("${gachiga.chat.retention-hours:3}")
    private int chatRetentionHours = 3;

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
                    String offer = MatchCandidate.pairOfferKey(candidate.riders());
                    var now = LocalDateTime.now(clock);
                    var earliestExpiry = candidate.riders().stream().map(WaitingRequest::expiresAt)
                            .min(Comparator.naturalOrder()).orElseThrow();
                    if (!earliestExpiry.isAfter(now)) {
                        status.setRollbackOnly();
                        return null;
                    }
                    var deadline = now.plusSeconds(60);
                    if (earliestExpiry.isBefore(deadline)) deadline = earliestExpiry;
                    MatchGroup group =
                            groups.save(
                                    MatchGroup.builder()
                                            .pairOfferKey(offer)
                                            .acceptDeadline(offer == null ? null : deadline)
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
                                        .accepted(offer == null)
                                        .build());
                    }
                    List<Long> userIds =
                            candidate.riders().stream().map(WaitingRequest::userId).toList();
                    // 3인 희망자의 2인 제안은 두 사람이 수락할 때 확정 이벤트를 발행한다.
                    events.publishEvent(new GroupProposed(group.getId(), userIds));
                    if (offer == null) {
                        group.confirm();
                        events.publishEvent(new GroupConfirmed(group.getId(), userIds));
                    }
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
                                                m.getAccepted(),
                                                Boolean.TRUE.equals(m.getCompleted())))
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
                group.getStatus() == GroupStatus.PENDING ? group.getAcceptDeadline() : null,
                group.getCreatedAt(),
                "DISTANCE_RATIO",
                group.getConfirmedAt() == null
                        ? null
                        : group.getConfirmedAt().plusHours(chatRetentionHours));
    }

    @Transactional
    public GroupResponse accept(Long groupId, Long userId) {
        MatchGroup group = lockedGroup(groupId);
        var roster = members.findByGroupId(groupId);
        var member = requireMember(roster, userId);
        if (group.getStatus() == GroupStatus.CONFIRMED && Boolean.TRUE.equals(member.getAccepted())) return detail(groupId, userId);
        if (group.getStatus() != GroupStatus.PENDING || group.getAcceptDeadline() == null
                || !LocalDateTime.now(clock).isBefore(group.getAcceptDeadline())) {
            throw new BusinessException(ErrorCode.MATCH_EXPIRED);
        }
        member.accept();
        if (roster.stream().allMatch(MatchMember::getAccepted)) {
            group.confirm();
            events.publishEvent(new GroupConfirmed(groupId, roster.stream().map(MatchMember::getUserId).toList()));
        }
        return detail(groupId, userId);
    }

    @Transactional
    public void rejectOffer(Long groupId, Long userId) {
        var group = lockedGroup(groupId);
        var roster = members.findByGroupId(groupId);
        requireMember(roster, userId);
        if (group.getStatus() == GroupStatus.DISSOLVED) return;
        if (group.getStatus() != GroupStatus.PENDING || group.getPairOfferKey() == null)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        dissolveOffer(group, roster, "PAIR_DECLINED");
    }

    @Transactional
    public void expireOffer(Long id) {
        var group = lockedGroup(id);
        if (group.getStatus() == GroupStatus.PENDING && group.getAcceptDeadline() != null
                && !LocalDateTime.now(clock).isBefore(group.getAcceptDeadline())) {
            dissolveOffer(group, members.findByGroupId(id), "PAIR_EXPIRED");
        }
    }

    private MatchGroup lockedGroup(Long id) {
        return groups.findLockedById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void dissolveOffer(MatchGroup group, List<MatchMember> roster, String reason) {
        roster.stream().map(MatchMember::getRequestId).sorted().forEach(rideRequestPort::markWaiting);
        group.setStatus(GroupStatus.DISSOLVED);
        group.setClosedAt(LocalDateTime.now(clock));
        events.publishEvent(new GroupDissolved(group.getId(),
                roster.stream().map(MatchMember::getUserId).toList(), reason));
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

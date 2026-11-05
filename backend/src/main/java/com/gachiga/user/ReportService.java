package com.gachiga.user;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.matching.MatchHistoryPort;
import com.gachiga.user.dto.ReportRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 접수 (T3-1, FR-04).
 *
 * <p>거절되는 경우는 두 가지다.
 *
 * <ul>
 *   <li>본인을 신고 → {@code INVALID_INPUT}
 *   <li>신고자·대상 둘 중 하나라도 그 그룹 구성원이 아님 → {@code GROUP_NOT_MEMBER}
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MatchHistoryPort matchHistoryPort;
    private final Clock clock;

    @Transactional
    public void report(Long reporterId, ReportRequest request) {
        requireNotSelfReport(reporterId, request.reportedUserId());
        requireSameGroup(request.groupId(), reporterId, request.reportedUserId());

        LocalDateTime now = LocalDateTime.now(clock);
        reportRepository.save(
                Report.create(
                        reporterId,
                        request.reportedUserId(),
                        request.groupId(),
                        request.reason(),
                        request.detail(),
                        now));

        User reported = findUser(request.reportedUserId());
        reported.applyReport(now);

        log.info(
                "신고 접수 reporterId={} reportedId={} groupId={} reason={} 누적={}건 status={}",
                reporterId,
                request.reportedUserId(),
                request.groupId(),
                request.reason(),
                reported.getReportCount(),
                reported.getStatus());
    }

    private void requireNotSelfReport(Long reporterId, Long reportedUserId) {
        if (reporterId.equals(reportedUserId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인을 신고할 수 없습니다.");
        }
    }

    /** 신고자·대상 둘 다 그 그룹의 구성원이었어야 한다 — 남을 대신해 신고하거나 무관한 그룹을 대는 것을 막는다 */
    private void requireSameGroup(Long groupId, Long reporterId, Long reportedUserId) {
        if (!matchHistoryPort.isMember(groupId, reporterId)
                || !matchHistoryPort.isMember(groupId, reportedUserId)) {
            throw new BusinessException(ErrorCode.GROUP_NOT_MEMBER);
        }
    }

    private User findUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, "신고 대상을 찾을 수 없습니다."));
    }
}

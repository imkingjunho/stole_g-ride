package com.gachiga.user.dto;

import com.gachiga.user.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 요청. {@code docs/api-spec.yaml} 의 {@code ReportRequest} 와 1:1 이다.
 *
 * <p>{@code reason} 이 허용된 값(NO_SHOW·RUDE·UNSAFE·PAYMENT·OTHER)이 아니면 JSON 역직렬화
 * 단계에서 걸러져 {@code INVALID_INPUT} 이 난다.
 */
public record ReportRequest(
        @NotNull(message = "신고할 그룹을 지정해 주세요") Long groupId,
        @NotNull(message = "신고 대상을 지정해 주세요") Long reportedUserId,
        @NotNull(message = "신고 사유를 선택해 주세요") ReportReason reason,
        @Size(max = 500, message = "상세 내용은 500자 이내로 입력해 주세요") String detail) {}

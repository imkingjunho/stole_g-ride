package com.gachiga.user;

/** 신고 사유 (FR-04, PRD §8 {@code ReportRequest}). */
public enum ReportReason {
    /** 노쇼 (E-05) */
    NO_SHOW,
    /** 무례한 언행 */
    RUDE,
    /** 위험 운전 등 안전 문제 */
    UNSAFE,
    /** 분담액 미정산 */
    PAYMENT,
    /** 그 밖의 사유. {@code detail} 에 구체적으로 적는다 */
    OTHER
}

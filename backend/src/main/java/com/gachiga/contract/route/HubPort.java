package com.gachiga.contract.route;

import java.util.List;
import java.util.Optional;

/**
 * 거점 조회. 구현은 {@code route/} (송준호), 사용은 서준(출발 좌표)과 이승민(요청 검증)이다.
 *
 * <p>Phase 0 에는 {@code route/StaticHubAdapter} 가 하드코딩한 6개를 돌려준다.
 */
public interface HubPort {

    /**
     * 거점 하나를 찾는다.
     *
     * @param hubId 찾을 거점 id
     * @return 거점. 없으면 {@link Optional#empty()}. <b>null 을 돌려주지 않는다</b>
     */
    Optional<HubInfo> findById(Long hubId);

    /**
     * 서비스 중인 거점 전체.
     *
     * @return 거점 목록. 비어 있을 수 있으나 null 아님
     */
    List<HubInfo> findAll();
}

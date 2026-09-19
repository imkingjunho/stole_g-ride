package com.gachiga.route;

import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * ⚠️ <b>Phase 0 스텁</b> — 거점 6개를 코드에 박아 둔다 (PRD §1.4·§14.7).
 *
 * <p><b>좌표는 임시값이다.</b> 공개 지도 데이터(OpenStreetMap·위키백과)에서 모은 값이라 실제 택시
 * 승하차 지점과 수십~수백 m 어긋날 수 있다. 특히 <b>예대 삼거리</b>는 공식 지명이 아니어서 주변
 * 도로 구조로 추정한 값이다(오차 200~300m 예상).
 *
 * <p>{@code type} 값은 PRD §7.1 {@code hubs.type} ENUM 의 네 가지만 쓴다.
 *
 * <p><b>교체 담당: 송준호 · Phase 1.</b> 현장 실측 좌표로 {@code hubs} 테이블을 시드하고
 * DB 를 읽는 구현으로 바꾼 뒤 이 클래스를 삭제한다 (PRD §7.1 초기 데이터).
 */
@Component
public class StaticHubAdapter implements HubPort {

    private static final List<HubInfo> HUBS =
            List.of(
                    // 전남대 — 정문은 승하차가 실제로 이뤄지는 앞 도로 교차점 기준
                    new HubInfo(1L, "전남대 정문", 35.1724d, 126.9048d, "CAMPUS"),
                    new HubInfo(2L, "전남대 후문", 35.1763d, 126.9123d, "CAMPUS"),
                    // 통칭 지명이라 위치가 가장 불확실하다. 실측 교체 1순위
                    new HubInfo(3L, "전남대 예대 삼거리", 35.1784d, 126.9042d, "CAMPUS"),
                    new HubInfo(4L, "경신여고", 35.1752d, 126.8923d, "SCHOOL"),
                    new HubInfo(5L, "유스퀘어", 35.1604d, 126.8794d, "TERMINAL"),
                    new HubInfo(6L, "광주송정역", 35.1378d, 126.7902d, "STATION"));

    @Override
    public Optional<HubInfo> findById(Long hubId) {
        return HUBS.stream().filter(hub -> hub.id().equals(hubId)).findFirst();
    }

    @Override
    public List<HubInfo> findAll() {
        return HUBS;
    }
}

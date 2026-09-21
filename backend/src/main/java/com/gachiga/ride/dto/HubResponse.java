package com.gachiga.ride.dto;

import com.gachiga.contract.route.HubInfo;

/**
 * 응답에 실리는 거점 정보. {@code docs/api-spec.yaml} 의 {@code Hub} 와 1:1 이다.
 *
 * <p>계약의 {@link HubInfo} 를 그대로 내보내지 않고 옮겨 담는 이유는, 계약이 바뀌어도
 * API 응답 모양이 따라 바뀌지 않게 하기 위해서다.
 */
public record HubResponse(Long id, String name, double lat, double lng, String type) {

    public static HubResponse from(HubInfo hub) {
        return new HubResponse(hub.id(), hub.name(), hub.lat(), hub.lng(), hub.type());
    }
}

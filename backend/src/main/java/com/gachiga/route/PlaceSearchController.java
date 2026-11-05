package com.gachiga.route;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.common.response.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

/** 카카오 로컬 검색 프록시 API. */
@RestController
@RequestMapping("/api/places")
@Profile("!test")
public class PlaceSearchController {

    private final KakaoPlaceSearchClient client;

    public PlaceSearchController(KakaoPlaceSearchClient client) {
        this.client = client;
    }

    @GetMapping("/search")
    public ApiResponse<List<PlaceSearchResult>> search(@RequestParam String q) {
        if (q.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "검색어를 입력해 주세요.");
        }
        try {
            return ApiResponse.ok(client.search(q.trim()));
        } catch (RestClientException | JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.ROUTE_API_FAILED, "장소 검색에 실패했습니다.", exception);
        }
    }
}

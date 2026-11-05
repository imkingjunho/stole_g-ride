package com.gachiga.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 카카오 로컬 키워드 검색 API 클라이언트. */
@Component
public class KakaoPlaceSearchClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String restApiKey;

    public KakaoPlaceSearchClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${KAKAO_REST_API_KEY:}") String restApiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.baseUrl("https://dapi.kakao.com").requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.restApiKey = restApiKey;
    }

    public List<PlaceSearchResult> search(String query) throws JsonProcessingException {
        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/keyword.json")
                        .queryParam("query", query)
                        .queryParam("x", 126.9079)
                        .queryParam("y", 35.1782)
                        .queryParam("radius", 20000)
                        .queryParam("size", 10)
                        .build())
                .header("Authorization", "KakaoAK " + restApiKey)
                .retrieve()
                .body(String.class);
        List<PlaceSearchResult> results = new ArrayList<>();
        for (JsonNode document : objectMapper.readTree(response).path("documents")) {
            results.add(new PlaceSearchResult(
                    document.path("place_name").asText(),
                    document.path("road_address_name").asText(document.path("address_name").asText()),
                    document.path("y").asDouble(),
                    document.path("x").asDouble()));
        }
        return results;
    }
}

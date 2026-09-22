package com.gachiga.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.RouteProvider;
import com.gachiga.contract.route.RouteResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오모빌리티 다중 경유지 길찾기 구현체.
 *
 * <p>카카오 호출이나 응답 매핑에 실패하면 기존 {@link EstimatedRouteProvider}로 위임한다.
 * 외부 API 장애가 매칭 자체를 중단시키지 않도록 {@link RouteProvider} 계약의 추정치 규칙을
 * 지킨다.
 */
@Primary
@Component
public class KakaoRouteProvider implements RouteProvider {

    private static final Logger log = LoggerFactory.getLogger(KakaoRouteProvider.class);
    private static final String DIRECTIONS_PATH = "/v1/waypoints/directions";
    private static final Duration API_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EstimatedRouteProvider fallback;
    private final String restApiKey;

    @Autowired
    public KakaoRouteProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            EstimatedRouteProvider fallback,
            @Value("${KAKAO_REST_API_KEY:}") String restApiKey) {
        this(buildRestClient(restClientBuilder), objectMapper, fallback, restApiKey);
    }

    KakaoRouteProvider(
            RestClient restClient,
            ObjectMapper objectMapper,
            EstimatedRouteProvider fallback,
            String restApiKey) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
        this.restApiKey = restApiKey;
    }

    @Override
    public RouteResult findRoute(
            Coordinate origin, List<Coordinate> waypoints, Coordinate destination) {
        List<Coordinate> normalizedWaypoints = waypoints == null ? List.of() : waypoints;
        if (restApiKey == null || restApiKey.isBlank()) {
            log.warn("카카오 REST API 키가 없어 추정 경로를 사용합니다.");
            return fallback.findRoute(origin, normalizedWaypoints, destination);
        }

        Map<String, Object> request = createRequest(origin, normalizedWaypoints, destination);
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String response = restClient
                        .post()
                        .uri(DIRECTIONS_PATH)
                        .header("Authorization", "KakaoAK " + restApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);
                return mapResponse(response, normalizedWaypoints.size());
            } catch (RestClientException | JsonProcessingException | IllegalStateException exception) {
                if (attempt == 0) {
                    log.warn("카카오 길찾기 호출 실패, 1회 재시도합니다.", exception);
                } else {
                    log.warn("카카오 길찾기 호출 실패, 추정 경로로 대체합니다.", exception);
                }
            }
        }
        return fallback.findRoute(origin, normalizedWaypoints, destination);
    }

    private RouteResult mapResponse(String response, int waypointCount) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(response);
        JsonNode route = root.path("routes").path(0);
        if (route.isMissingNode() || route.path("result_code").asInt(-1) != 0) {
            throw new IllegalStateException("카카오 길찾기 응답이 실패 상태입니다.");
        }

        JsonNode summary = route.path("summary");
        List<RouteResult.Section> sections = new ArrayList<>();
        for (JsonNode section : route.path("sections")) {
            sections.add(new RouteResult.Section(
                    section.path("distance").asInt(),
                    section.path("duration").asInt(),
                    mapPath(section.path("roads"))));
        }
        if (sections.size() != waypointCount + 1) {
            throw new IllegalStateException("카카오 응답의 구간 수가 경유지 수와 일치하지 않습니다.");
        }

        return new RouteResult(
                summary.path("fare").path("taxi").asInt(),
                summary.path("distance").asInt(),
                summary.path("duration").asInt(),
                sections,
                false,
                response);
    }

    private List<Coordinate> mapPath(JsonNode roads) {
        List<Coordinate> path = new ArrayList<>();
        for (JsonNode road : roads) {
            JsonNode vertexes = road.path("vertexes");
            for (int index = 0; index + 1 < vertexes.size(); index += 2) {
                path.add(new Coordinate(vertexes.get(index + 1).asDouble(), vertexes.get(index).asDouble()));
            }
        }
        return path;
    }

    private Map<String, Object> createRequest(
            Coordinate origin, List<Coordinate> waypoints, Coordinate destination) {
        Map<String, Object> request = new HashMap<>();
        request.put("origin", coordinateBody(origin, "origin"));
        request.put("destination", coordinateBody(destination, "destination"));
        request.put("waypoints", waypoints.stream()
                .map(coordinate -> coordinateBody(coordinate, "waypoint"))
                .toList());
        request.put("priority", "RECOMMEND");
        request.put("summary", false);
        return request;
    }

    private Map<String, Object> coordinateBody(Coordinate coordinate, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("x", coordinate.lng());
        body.put("y", coordinate.lat());
        body.put("name", name);
        return body;
    }

    private static RestClient buildRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(API_TIMEOUT);
        factory.setReadTimeout(API_TIMEOUT);
        return builder
                .baseUrl("https://apis-navi.kakaomobility.com")
                .requestFactory(factory)
                .build();
    }
}

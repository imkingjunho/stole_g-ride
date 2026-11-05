package com.gachiga.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.RouteProvider;
import com.gachiga.contract.route.RouteResult;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

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
    private final StringRedisTemplate redisTemplate;
    private final Duration cacheTtl;
    private final RouteCallQuota routeCallQuota;

    @Autowired
    public KakaoRouteProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            EstimatedRouteProvider fallback,
            @Value("${KAKAO_REST_API_KEY:}") String restApiKey,
            StringRedisTemplate redisTemplate,
            @Value("${gachiga.route.cache-ttl-seconds:600}") long cacheTtlSeconds,
            RouteCallQuota routeCallQuota) {
        this(buildRestClient(restClientBuilder), objectMapper, fallback, restApiKey,
                redisTemplate, Duration.ofSeconds(cacheTtlSeconds), routeCallQuota);
    }

    KakaoRouteProvider(
            RestClient restClient,
            ObjectMapper objectMapper,
            EstimatedRouteProvider fallback,
            String restApiKey) {
        this(restClient, objectMapper, fallback, restApiKey, null, Duration.ofMinutes(10), null);
    }

    KakaoRouteProvider(
            RestClient restClient,
            ObjectMapper objectMapper,
            EstimatedRouteProvider fallback,
            String restApiKey,
            StringRedisTemplate redisTemplate,
            Duration cacheTtl) {
        this(restClient, objectMapper, fallback, restApiKey, redisTemplate, cacheTtl, null);
    }

    KakaoRouteProvider(
            RestClient restClient,
            ObjectMapper objectMapper,
            EstimatedRouteProvider fallback,
            String restApiKey,
            StringRedisTemplate redisTemplate,
            Duration cacheTtl,
            RouteCallQuota routeCallQuota) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
        this.restApiKey = restApiKey;
        this.redisTemplate = redisTemplate;
        this.cacheTtl = cacheTtl;
        this.routeCallQuota = routeCallQuota;
    }

    @Override
    public RouteResult findRoute(
            Coordinate origin, List<Coordinate> waypoints, Coordinate destination) {
        List<Coordinate> normalizedWaypoints = waypoints == null ? List.of() : waypoints;
        String cacheKey = cacheKey(origin, normalizedWaypoints, destination);
        CacheLookup cacheLookup = readCache(cacheKey);
        if (cacheLookup.status() == CacheStatus.HIT) {
            log.info("route_cache event=hit");
            return cacheLookup.result();
        }
        if (cacheLookup.status() == CacheStatus.MISS) {
            log.info("route_cache event=miss");
        }
        if (restApiKey == null || restApiKey.isBlank()) {
            log.warn("카카오 REST API 키가 없어 추정 경로를 사용합니다.");
            return fallback.findRoute(origin, normalizedWaypoints, destination);
        }

        Map<String, Object> request = createRequest(origin, normalizedWaypoints, destination);
        for (int attempt = 0; attempt < 2; attempt++) {
            if (!isKakaoCallAllowed()) {
                return fallback.findRoute(origin, normalizedWaypoints, destination);
            }
            try {
                String response = restClient
                        .post()
                        .uri(DIRECTIONS_PATH)
                        .header("Authorization", "KakaoAK " + restApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);
                RouteResult result = mapResponse(response, normalizedWaypoints.size());
                writeCache(cacheKey, result);
                return result;
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

    private boolean isKakaoCallAllowed() {
        if (routeCallQuota == null) {
            return true;
        }
        try {
            RouteCallQuota.Decision decision = routeCallQuota.acquire();
            if (!decision.allowed()) {
                log.warn(
                        "카카오 경로 API 호출 한도 초과({}), 추정 경로로 대체합니다. tick={}, daily={}",
                        decision.limit(),
                        decision.tickCount(),
                        decision.dailyCount());
            }
            return decision.allowed();
        } catch (DataAccessException | IllegalStateException exception) {
            log.warn("Redis 호출량 제한 확인 실패, 카카오 호출을 생략하고 추정 경로를 사용합니다.", exception);
            return false;
        }
    }

    private CacheLookup readCache(String cacheKey) {
        if (redisTemplate == null) {
            return new CacheLookup(null, CacheStatus.UNAVAILABLE);
        }
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                return new CacheLookup(null, CacheStatus.MISS);
            }
            RouteResult result = objectMapper.readValue(cached, RouteResult.class);
            return result == null
                    ? new CacheLookup(null, CacheStatus.MISS)
                    : new CacheLookup(result, CacheStatus.HIT);
        } catch (JsonProcessingException exception) {
            log.warn("경로 캐시 역직렬화 실패: key={}", cacheKey, exception);
            return new CacheLookup(null, CacheStatus.MISS);
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis 연결 실패로 경로 API를 사용합니다.", exception);
            return new CacheLookup(null, CacheStatus.UNAVAILABLE);
        }
    }

    private enum CacheStatus {
        HIT,
        MISS,
        UNAVAILABLE
    }

    private record CacheLookup(RouteResult result, CacheStatus status) {}

    private void writeCache(String cacheKey, RouteResult result) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), cacheTtl);
        } catch (JsonProcessingException exception) {
            log.warn("경로 캐시 직렬화 실패: key={}", cacheKey, exception);
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis 연결 실패로 경로 결과를 캐시하지 못했습니다.", exception);
        }
    }

    private String cacheKey(Coordinate origin, List<Coordinate> waypoints, Coordinate destination) {
        StringBuilder normalized = new StringBuilder(formatCoordinate(origin));
        for (Coordinate waypoint : waypoints) {
            normalized.append('|').append(formatCoordinate(waypoint));
        }
        normalized.append('|').append(formatCoordinate(destination));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("route:");
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 환경입니다.", exception);
        }
    }

    private String formatCoordinate(Coordinate coordinate) {
        return "%.4f,%.4f".formatted(coordinate.lat(), coordinate.lng());
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

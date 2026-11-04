package com.gachiga.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.RouteResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoRouteProviderTest {

    @Test
    void mapsKakaoResponseToRouteResult() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis-navi.kakaomobility.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoRouteProvider provider =
                new KakaoRouteProvider(
                        builder.build(), new ObjectMapper(), new EstimatedRouteProvider(), "test-key");

        server.expect(requestTo("https://apis-navi.kakaomobility.com/v1/waypoints/directions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))
                .andRespond(withSuccess(
                        """
                        {
                          "routes": [{
                            "result_code": 0,
                            "summary": {
                              "fare": {"taxi": 8500},
                              "distance": 10000,
                              "duration": 1200
                            },
                            "sections": [{
                              "distance": 4000,
                              "duration": 500,
                              "roads": [{"vertexes": [126.9, 35.17, 126.8, 35.14]}]
                            }, {
                              "distance": 6000,
                              "duration": 700,
                              "roads": [{"vertexes": [126.8, 35.14, 126.79, 35.13]}]
                            }]
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        RouteResult result = provider.findRoute(
                new Coordinate(35.17, 126.9),
                List.of(new Coordinate(35.14, 126.8)),
                new Coordinate(35.13, 126.79));
        assertThat(result.totalFare()).isEqualTo(8500);
        assertThat(result.totalDistance()).isEqualTo(10000);
        assertThat(result.totalDuration()).isEqualTo(1200);
        assertThat(result.sections()).hasSize(2);
        assertThat(result.sections().get(0).path())
                .containsExactly(new Coordinate(35.17, 126.9), new Coordinate(35.14, 126.8));
        assertThat(result.estimated()).isFalse();
        assertThat(result.rawJson()).contains("\"result_code\": 0");
        server.verify();
    }

    @Test
    void usesEstimatedRouteWhenApiKeyIsMissing() {
        KakaoRouteProvider provider =
                new KakaoRouteProvider(
                        RestClient.builder().build(),
                        new ObjectMapper(),
                        new EstimatedRouteProvider(),
                        "");

        RouteResult result = provider.findRoute(
                new Coordinate(35.17, 126.9), List.of(), new Coordinate(35.14, 126.8));

        assertThat(result.estimated()).isTrue();
        assertThat(result.sections()).hasSize(1);
    }

    @Test
    void usesEstimatedRouteAfterApiFailureRetry() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis-navi.kakaomobility.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoRouteProvider provider = new KakaoRouteProvider(
                builder.build(), new ObjectMapper(), new EstimatedRouteProvider(), "test-key");
        server.expect(requestTo("https://apis-navi.kakaomobility.com/v1/waypoints/directions"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withServerError());
        server.expect(requestTo("https://apis-navi.kakaomobility.com/v1/waypoints/directions"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withServerError());

        RouteResult result = provider.findRoute(
                new Coordinate(35.17, 126.9), List.of(), new Coordinate(35.14, 126.8));

        assertThat(result.estimated()).isTrue();
        server.verify();
    }

    @Test
    void usesEstimatedRouteWhenTickQuotaIsReached() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis-navi.kakaomobility.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteCallQuota quota = mock(RouteCallQuota.class);
        when(quota.acquire())
                .thenReturn(new RouteCallQuota.Decision(
                        false, 10, 20, false, false, RouteCallQuota.Limit.TICK));
        KakaoRouteProvider provider = new KakaoRouteProvider(
                builder.build(),
                new ObjectMapper(),
                new EstimatedRouteProvider(),
                "test-key",
                null,
                Duration.ofMinutes(10),
                quota);

        RouteResult result = provider.findRoute(
                new Coordinate(35.17, 126.9), List.of(), new Coordinate(35.14, 126.8));

        assertThat(result.estimated()).isTrue();
        verify(quota).acquire();
        server.verify();
    }

    @Test
    void countsRetryAsAnotherKakaoCall() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis-navi.kakaomobility.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteCallQuota quota = mock(RouteCallQuota.class);
        when(quota.acquire())
                .thenReturn(
                        new RouteCallQuota.Decision(
                                true, 1, 1, false, false, RouteCallQuota.Limit.NONE),
                        new RouteCallQuota.Decision(
                                false, 10, 10, false, false, RouteCallQuota.Limit.TICK));
        server.expect(requestTo("https://apis-navi.kakaomobility.com/v1/waypoints/directions"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withServerError());
        KakaoRouteProvider provider = new KakaoRouteProvider(
                builder.build(),
                new ObjectMapper(),
                new EstimatedRouteProvider(),
                "test-key",
                null,
                Duration.ofMinutes(10),
                quota);

        RouteResult result = provider.findRoute(
                new Coordinate(35.17, 126.9), List.of(), new Coordinate(35.14, 126.8));

        assertThat(result.estimated()).isTrue();
        verify(quota, org.mockito.Mockito.times(2)).acquire();
        server.verify();
    }

    @Test
    void usesEstimatedRouteWhenRedisQuotaStoreIsUnavailable() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis-navi.kakaomobility.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteCallQuota quota = mock(RouteCallQuota.class);
        when(quota.acquire()).thenThrow(new RedisConnectionFailureException("redis unavailable"));
        KakaoRouteProvider provider = new KakaoRouteProvider(
                builder.build(),
                new ObjectMapper(),
                new EstimatedRouteProvider(),
                "test-key",
                null,
                Duration.ofMinutes(10),
                quota);

        RouteResult result = provider.findRoute(
                new Coordinate(35.17, 126.9), List.of(), new Coordinate(35.14, 126.8));

        assertThat(result.estimated()).isTrue();
        verify(quota).acquire();
        server.verify();
    }

    @Test
    void usesEstimatedRouteWhenQuotaResponseIsInvalid() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis-navi.kakaomobility.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RouteCallQuota quota = mock(RouteCallQuota.class);
        when(quota.acquire()).thenThrow(new IllegalStateException("invalid quota response"));
        KakaoRouteProvider provider = new KakaoRouteProvider(
                builder.build(),
                new ObjectMapper(),
                new EstimatedRouteProvider(),
                "test-key",
                null,
                Duration.ofMinutes(10),
                quota);

        RouteResult result = provider.findRoute(
                new Coordinate(35.17, 126.9), List.of(), new Coordinate(35.14, 126.8));

        assertThat(result.estimated()).isTrue();
        verify(quota).acquire();
        server.verify();
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsCachedRouteWithoutCallingKakaoOrQuota() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        RouteResult cached = new RouteResult(
                8500,
                10000,
                1200,
                List.of(new RouteResult.Section(
                        10000,
                        1200,
                        List.of(new Coordinate(35.17, 126.9), new Coordinate(35.14, 126.8)))),
                false,
                "{\"cached\":true}");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(objectMapper.writeValueAsString(cached));
        RouteCallQuota quota = mock(RouteCallQuota.class);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis-navi.kakaomobility.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoRouteProvider provider = new KakaoRouteProvider(
                builder.build(),
                objectMapper,
                new EstimatedRouteProvider(),
                "test-key",
                redisTemplate,
                Duration.ofMinutes(10),
                quota);

        RouteResult result = provider.findRoute(
                new Coordinate(35.17, 126.9), List.of(), new Coordinate(35.14, 126.8));

        assertThat(result).isEqualTo(cached);
        org.mockito.Mockito.verifyNoInteractions(quota);
        server.verify();
    }

    @SuppressWarnings("unchecked")
    @Test
    void treatsNullCachePayloadAsMissAndRequestsKakaoRoute() {
        ObjectMapper objectMapper = new ObjectMapper();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(org.mockito.ArgumentMatchers.anyString())).thenReturn("null");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis-navi.kakaomobility.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://apis-navi.kakaomobility.com/v1/waypoints/directions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "routes": [{
                            "result_code": 0,
                            "summary": {
                              "fare": {"taxi": 8500},
                              "distance": 10000,
                              "duration": 1200
                            },
                            "sections": [{
                              "distance": 10000,
                              "duration": 1200,
                              "roads": [{"vertexes": [126.9, 35.17, 126.8, 35.14]}]
                            }]
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));
        KakaoRouteProvider provider = new KakaoRouteProvider(
                builder.build(),
                objectMapper,
                new EstimatedRouteProvider(),
                "test-key",
                redisTemplate,
                Duration.ofMinutes(10));

        RouteResult result = provider.findRoute(
                new Coordinate(35.17, 126.9), List.of(), new Coordinate(35.14, 126.8));

        assertThat(result).isNotNull();
        assertThat(result.estimated()).isFalse();
        server.verify();
    }
}

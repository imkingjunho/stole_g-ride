package com.gachiga.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.RouteResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
}

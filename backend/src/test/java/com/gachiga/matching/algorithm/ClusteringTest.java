package com.gachiga.matching.algorithm;

import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Clustering & CombinationGenerator - T1-4")
class ClusteringTest {

    @Test
    @DisplayName("클러스터링: 3km 이상 떨어진 요청 → 다른 클러스터")
    void test_clustering_by_distance() {
        LocalDateTime now = LocalDateTime.now();
        
        // 요청 1, 2: 강남역 근처 (거리 약 200m)
        WaitingRequest req1 = new WaitingRequest(
            1L, 1L, 100L, new Coordinate(37.4979, 127.0276), "강남역", 
            now, now.plusMinutes(30), now, false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );
        
        WaitingRequest req2 = new WaitingRequest(
            2L, 2L, 100L, new Coordinate(37.4980, 127.0280), "강남역 근처",
            now.plusMinutes(2), now.plusMinutes(32), now.plusMinutes(2), false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        // 요청 3: 서울역 (거리 약 9km)
        WaitingRequest req3 = new WaitingRequest(
            3L, 3L, 100L, new Coordinate(37.5565, 126.9723), "서울역",
            now.plusMinutes(3), now.plusMinutes(33), now.plusMinutes(3), false, BigDecimal.valueOf(0.30), 5000, 5000, 0
        );

        List<WaitingRequest> requests = List.of(req1, req2, req3);
        List<List<WaitingRequest>> clusters = Clustering.cluster(requests);

        // 결과: 2개 클러스터 (req1, req2가 한 클러스터, req3이 다른 클러스터)
        assertEquals(2, clusters.size());
        assertEquals(2, clusters.get(0).size());  // req1, req2
        assertEquals(1, clusters.get(1).size());  // req3
    }

    @Test
    @DisplayName("조합 생성: 4명 클러스터 → C(4,2) + C(4,3) + C(4,4)")
    void test_combination_generation() {
        LocalDateTime now = LocalDateTime.now();
        Coordinate dest = new Coordinate(37.4979, 127.0276);

        List<WaitingRequest> cluster = List.of(
            new WaitingRequest(1L, 1L, 100L, dest, "목적지1", now, now.plusMinutes(30), now, false, BigDecimal.valueOf(0.30), 3000, 3000, 0),
            new WaitingRequest(2L, 2L, 100L, dest, "목적지2", now, now.plusMinutes(30), now, false, BigDecimal.valueOf(0.30), 3000, 3000, 0),
            new WaitingRequest(3L, 3L, 100L, dest, "목적지3", now, now.plusMinutes(30), now, false, BigDecimal.valueOf(0.30), 3000, 3000, 0),
            new WaitingRequest(4L, 4L, 100L, dest, "목적지4", now, now.plusMinutes(30), now, false, BigDecimal.valueOf(0.30), 3000, 3000, 0)
        );

        List<List<WaitingRequest>> clusters = List.of(cluster);
        List<List<WaitingRequest>> combinations = CombinationGenerator.generateCombinations(clusters);

        // C(4,2) = 6, C(4,3) = 4, C(4,4) = 1 → 총 11개
        assertEquals(11, combinations.size());
        
        // 크기별 확인
        long size2 = combinations.stream().filter(c -> c.size() == 2).count();
        long size3 = combinations.stream().filter(c -> c.size() == 3).count();
        long size4 = combinations.stream().filter(c -> c.size() == 4).count();
        
        assertEquals(6, size2);
        assertEquals(4, size3);
        assertEquals(1, size4);
    }

    @Test
    @DisplayName("빈 리스트 입력 → 빈 결과")
    void test_empty_input() {
        List<List<WaitingRequest>> clusters = Clustering.cluster(List.of());
        assertTrue(clusters.isEmpty());

        List<List<WaitingRequest>> combinations = CombinationGenerator.generateCombinations(List.of());
        assertTrue(combinations.isEmpty());
    }

    @Test
    @DisplayName("클러스터 크기가 1 → 조합 없음")
    void test_cluster_size_one() {
        LocalDateTime now = LocalDateTime.now();
        Coordinate dest = new Coordinate(37.4979, 127.0276);

        WaitingRequest req = new WaitingRequest(
            1L, 1L, 100L, dest, "목적지", now, now.plusMinutes(30), now,
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        List<List<WaitingRequest>> clusters = List.of(List.of(req));
        List<List<WaitingRequest>> combinations = CombinationGenerator.generateCombinations(clusters);

        // 크기 1은 조합 대상 아님
        assertTrue(combinations.isEmpty());
    }

    @Test
    @DisplayName("버킷 크기 제한: 요청 25개 → 버킷 20 + 버킷 5")
    void test_bucket_size_limit() {
        LocalDateTime now = LocalDateTime.now();
        Coordinate dest = new Coordinate(37.4979, 127.0276);

        // 25개 요청 생성
        List<WaitingRequest> requests = new java.util.ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            requests.add(new WaitingRequest(
                (long)i, (long)i, 100L, dest, "목적지" + i,
                now, now.plusMinutes(30), now, false, BigDecimal.valueOf(0.30), 3000, 3000, 0
            ));
        }

        List<List<WaitingRequest>> clusters = Clustering.cluster(requests);

        // 최소 2개 클러스터 (20 + 5)
        assertTrue(clusters.size() >= 2);
        
        // 모든 클러스터가 20 이하
        for (List<WaitingRequest> cluster : clusters) {
            assertTrue(cluster.size() <= 20);
        }
    }
}
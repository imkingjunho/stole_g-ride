package com.gachiga.matching.algorithm;

import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.Coordinate;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Clustering {

    private static final int DISTANCE_THRESHOLD_METERS = 3000;
    private static final int MAX_BUCKET_SIZE = 20;
    private static final int EARTH_RADIUS_METERS = 6371000;
    /** 거점에서 본 목적지 방향이 이 각도 이내로 이어지면 같은 버킷 (연쇄 기준, 버킷 폭은 약 90°) */
    private static final double BEARING_THRESHOLD_DEGREES = 45.0;

    /**
     * 거점별로 나눈 뒤, 거점 좌표를 알면 목적지 거리 대신 <b>출발 방향(방위각)</b>으로 묶는다.
     * 같은 방향 장거리(유스퀘어 → 광주송정역 축 등)는 한 버킷에 들어가고, 반대 방향은 카카오 경로 조회 전에 갈린다.
     * 거점 좌표를 모르면 기존 3km 목적지 연쇄 방식으로 처리한다.
     */
    public static List<List<WaitingRequest>> cluster(
            List<WaitingRequest> requests, Function<Long, Coordinate> hubLocator) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<WaitingRequest>> byHub =
                requests.stream().collect(Collectors.groupingBy(WaitingRequest::hubId));
        List<List<WaitingRequest>> allClusters = new ArrayList<>();
        for (Map.Entry<Long, List<WaitingRequest>> entry : byHub.entrySet()) {
            Coordinate origin = hubLocator == null ? null : hubLocator.apply(entry.getKey());
            if (origin == null) {
                allClusters.addAll(clusterByDistance(entry.getValue()));
                continue;
            }
            List<WaitingRequest> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparingDouble(r -> bearing(origin, r.destination())));
            allClusters.addAll(
                    clusterBy(
                            sorted,
                            (a, b) ->
                                    angleDiff(
                                                    bearing(origin, a.destination()),
                                                    bearing(origin, b.destination()))
                                            <= BEARING_THRESHOLD_DEGREES));
        }
        return allClusters;
    }

    public static List<List<WaitingRequest>> cluster(List<WaitingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, List<WaitingRequest>> byHub = requests.stream()
            .collect(Collectors.groupingBy(WaitingRequest::hubId));

        List<List<WaitingRequest>> allClusters = new ArrayList<>();

        for (List<WaitingRequest> hubRequests : byHub.values()) {
            List<List<WaitingRequest>> hubClusters = clusterByDistance(hubRequests);
            allClusters.addAll(hubClusters);
        }

        return allClusters;
    }

    private static List<List<WaitingRequest>> clusterByDistance(List<WaitingRequest> requests) {
        return clusterBy(
                requests,
                (a, b) ->
                        haversineDistance(
                                        a.destination().lat(),
                                        a.destination().lng(),
                                        b.destination().lat(),
                                        b.destination().lng())
                                <= DISTANCE_THRESHOLD_METERS);
    }

    private static List<List<WaitingRequest>> clusterBy(
            List<WaitingRequest> requests, BiPredicate<WaitingRequest, WaitingRequest> near) {
        List<List<WaitingRequest>> clusters = new ArrayList<>();
        List<Boolean> visited = new ArrayList<>(Collections.nCopies(requests.size(), false));

        for (int i = 0; i < requests.size(); i++) {
            if (visited.get(i)) {
                continue;
            }

            List<WaitingRequest> cluster = new ArrayList<>();
            Queue<Integer> queue = new LinkedList<>();

            queue.add(i);
            visited.set(i, true);

            while (!queue.isEmpty()) {
                int curr = queue.poll();
                cluster.add(requests.get(curr));

                if (cluster.size() >= MAX_BUCKET_SIZE) {
                    break;
                }

                for (int j = 0; j < requests.size(); j++) {
                                        if (!visited.get(j) && cluster.size() + queue.size() < MAX_BUCKET_SIZE) {
                        if (near.test(requests.get(curr), requests.get(j))) {
                            visited.set(j, true);
                            queue.add(j);

                            if (cluster.size() >= MAX_BUCKET_SIZE) {
                                break;
                            }
                        }
                    }
                }

                if (cluster.size() >= MAX_BUCKET_SIZE) {
                    break;
                }
            }

            clusters.add(cluster);
        }

        return clusters;
    }

    private static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.asin(Math.sqrt(a));
        return EARTH_RADIUS_METERS * c;
    }

    /** 출발점에서 목적지까지의 방위각(0~360°, 북=0) */
    static double bearing(Coordinate from, Coordinate to) {
        double phi1 = Math.toRadians(from.lat());
        double phi2 = Math.toRadians(to.lat());
        double dLon = Math.toRadians(to.lng() - from.lng());
        double y = Math.sin(dLon) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    static double angleDiff(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }
}

package com.gachiga.matching.algorithm;

import com.gachiga.contract.ride.WaitingRequest;
import java.util.*;
import java.util.stream.Collectors;

public class Clustering {

    private static final int DISTANCE_THRESHOLD_METERS = 3000;
    private static final int MAX_BUCKET_SIZE = 20;
    private static final int EARTH_RADIUS_METERS = 6371000;

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
                    if (!visited.get(j)) {
                        double dist = haversineDistance(
                            requests.get(curr).destination().lat(),
                            requests.get(curr).destination().lng(),
                            requests.get(j).destination().lat(),
                            requests.get(j).destination().lng()
                        );

                        if (dist <= DISTANCE_THRESHOLD_METERS) {
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
}
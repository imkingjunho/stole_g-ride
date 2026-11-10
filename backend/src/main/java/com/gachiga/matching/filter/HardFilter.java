package com.gachiga.matching.filter;

import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.user.UserPort;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.route.Coordinate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class HardFilter {

    private static final int TIME_WINDOW_MINUTES = 5;
    private static final int MAX_DESTINATION_DISTANCE_METERS = 3000;

    public static boolean compatible(WaitingRequest a, WaitingRequest b, UserPort userPort) {
        if (!a.hubId().equals(b.hubId())) {
            return false;
        }

        if (!isWithinTimeWindow(a.createdAt(), b.createdAt())) {
            return false;
        }

        if (!isGenderCompatible(a, b, userPort)) {
            return false;
        }

        if (!isDestinationWithinRange(a.destination(), b.destination())) {
            return false;
        }

        return true;
    }

    private static boolean isWithinTimeWindow(LocalDateTime time1, LocalDateTime time2) {
        long minutesDiff = Math.abs(ChronoUnit.MINUTES.between(time1, time2));
        return minutesDiff <= TIME_WINDOW_MINUTES;
    }

    private static boolean isGenderCompatible(WaitingRequest a, WaitingRequest b, UserPort userPort) {
        if (!a.sameGenderOnly()) {
            return true;
        }

        var userB = userPort.findById(b.userId());
        return userB.map(summary -> summary.gender() == Gender.F).orElse(false);
    }

    private static boolean isDestinationWithinRange(Coordinate dest1, Coordinate dest2) {
        double distance = haversineDistance(
            dest1.lat(), dest1.lng(),
            dest2.lat(), dest2.lng()
        );
        return distance <= MAX_DESTINATION_DISTANCE_METERS;
    }

    private static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_METERS = 6371000;

        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.asin(Math.sqrt(a));
        return EARTH_RADIUS_METERS * c;
    }
}
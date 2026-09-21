package com.gachiga.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RouteCacheKeyTest {

    @Test
    void shouldNormalizeCoordinatesToFourDecimalPlaces() {
        String key1 = RouteCacheKey.of(35.1760123, 126.8977123, 35.1420123, 126.7913123);
        String key2 = RouteCacheKey.of(35.1760123, 126.8977123, 35.1420123, 126.7913123);

        assertEquals(key1, key2);
        assertEquals("route:v1:35.1760:126.8977:35.1420:126.7913", key1);
    }
}

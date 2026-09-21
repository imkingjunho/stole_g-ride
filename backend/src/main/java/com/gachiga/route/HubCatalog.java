package com.gachiga.route;

import java.util.List;

public final class HubCatalog {
    private static final List<Hub> HUBS = List.of(
            new Hub("전남대 정문", 35.1760, 126.8977),
            new Hub("전남대 후문", 35.1760, 126.8977),
            new Hub("예대 삼거리", 35.1740, 126.9002),
            new Hub("경신여고", 35.1695, 126.8901),
            new Hub("유스퀘어", 35.1512, 126.8827),
            new Hub("광주송정역", 35.1420, 126.7913)
    );

    private HubCatalog() {
    }

    public static List<Hub> all() {
        return HUBS;
    }

    public static Hub getByName(String name) {
        return HUBS.stream()
                .filter(hub -> hub.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}

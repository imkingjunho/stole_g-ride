package com.gachiga.route;

public record Hub(String name, double latitude, double longitude) {
    public Hub {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("허브 이름은 비어 있을 수 없습니다.");
        }
    }
}

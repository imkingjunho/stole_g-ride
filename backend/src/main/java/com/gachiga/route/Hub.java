package com.gachiga.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출발 거점 프리셋. */
@Entity
@Table(name = "hubs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HubType type;

    public Hub(String name, double lat, double lng, HubType type) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.type = type;
    }

    public enum HubType {
        CAMPUS,
        STATION,
        TERMINAL,
        SCHOOL
    }
}

package com.gachiga.ride.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "waiting_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitingRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long hubId;
    private BigDecimal destinationLat;
    private BigDecimal destinationLng;
    private String destinationName;
    
    private LocalDateTime departAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    
    private boolean sameGenderOnly;
    private BigDecimal maxDetourRatio;
    
    private int soloDistance;
    private int soloFare;
    
    @Enumerated(EnumType.STRING)
    private RequestStatus status;
    
    private int version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = RequestStatus.WAITING;
    }

    public enum RequestStatus {
        WAITING, MATCHED, CONFIRMED, COMPLETED
    }
}
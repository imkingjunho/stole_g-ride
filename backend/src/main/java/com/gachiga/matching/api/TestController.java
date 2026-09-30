package com.gachiga.matching.api;

import com.gachiga.ride.domain.WaitingRequest;
import com.gachiga.ride.port.WaitingRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final WaitingRequestRepository waitingRequestRepository;

    @PostMapping("/create-waiting-requests")
    public String createWaitingRequests() {
        log.info("Creating test waiting requests");
        
        for (int i = 1; i <= 3; i++) {
            WaitingRequest req = WaitingRequest.builder()
                .userId((long) i)
                .hubId(1L)
                .destinationLat(BigDecimal.valueOf(35.1 + i * 0.01))
                .destinationLng(BigDecimal.valueOf(126.9 + i * 0.01))
                .destinationName("Test Destination " + i)
                .departAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .sameGenderOnly(false)
                .maxDetourRatio(BigDecimal.valueOf(1.5))
                .soloDistance(5000 - i * 100)
                .soloFare(10000 - i * 500)
                .version(0)
                .build();
            
            waitingRequestRepository.save(req);
            log.info("Created waiting request for user {}", i);
        }
        
        return "OK: 3 waiting requests created";
    }
}
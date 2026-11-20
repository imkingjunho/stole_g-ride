package com.gachiga.matching;

import com.gachiga.matching.port.MatchGroupRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PairOfferExpiry {
    private final MatchGroupRepository groups;
    private final GroupService service;
    private final Clock clock;

    @Scheduled(fixedDelay = 1000)
    public void expireUnanswered() {
        for (Long id : groups.expiredOffers(LocalDateTime.now(clock))) service.expireOffer(id);
    }
}

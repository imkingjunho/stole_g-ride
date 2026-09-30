package com.gachiga.ride.adapter;

import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.ride.port.WaitingRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideRequestAdapter implements RideRequestPort {

    private final WaitingRequestRepository repository;

    @Override
    public List<WaitingRequest> findWaiting() {
        return repository.findByStatus(com.gachiga.ride.domain.WaitingRequest.RequestStatus.WAITING)
            .stream()
            .map(this::toContract)
            .collect(Collectors.toList());
    }

    @Override
    public boolean tryMarkMatched(Long requestId, int version) {  // ← int version 추가!
    var req = repository.findById(requestId);
    if (req.isPresent()) {
        com.gachiga.ride.domain.WaitingRequest entity = req.get();
        entity.setStatus(com.gachiga.ride.domain.WaitingRequest.RequestStatus.MATCHED);
        entity.setVersion(version);  // ← version 설정
        repository.save(entity);
        return true;
    }
    return false;
}

    @Override
    public void markWaiting(Long requestId) {
        var req = repository.findById(requestId);
        req.ifPresent(entity -> {
            entity.setStatus(com.gachiga.ride.domain.WaitingRequest.RequestStatus.WAITING);
            repository.save(entity);
        });
    }

    @Override
    public void markConfirmed(Long requestId) {
        var req = repository.findById(requestId);
        req.ifPresent(entity -> {
            entity.setStatus(com.gachiga.ride.domain.WaitingRequest.RequestStatus.CONFIRMED);
            repository.save(entity);
        });
    }

    @Override
    public void markCompleted(Long requestId) {
        var req = repository.findById(requestId);
        req.ifPresent(entity -> {
            entity.setStatus(com.gachiga.ride.domain.WaitingRequest.RequestStatus.COMPLETED);
            repository.save(entity);
        });
    }

    private WaitingRequest toContract(com.gachiga.ride.domain.WaitingRequest entity) {
        return new WaitingRequest(
            entity.getId(),
            entity.getUserId(),
            entity.getHubId(),
            new com.gachiga.contract.route.Coordinate(
                entity.getDestinationLat().doubleValue(),
                entity.getDestinationLng().doubleValue()
            ),
            entity.getDestinationName(),
            entity.getDepartAt(),
            entity.getExpiresAt(),
            entity.getCreatedAt(),
            entity.isSameGenderOnly(),
            entity.getMaxDetourRatio(),
            entity.getSoloDistance(),
            entity.getSoloFare(),
            entity.getVersion()
        );
    }
}
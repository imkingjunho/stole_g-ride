package com.gachiga.ride.port;

import com.gachiga.ride.domain.WaitingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WaitingRequestRepository extends JpaRepository<WaitingRequest, Long> {
    List<WaitingRequest> findByStatusAndHubId(WaitingRequest.RequestStatus status, Long hubId);
    List<WaitingRequest> findByStatus(WaitingRequest.RequestStatus status);
}
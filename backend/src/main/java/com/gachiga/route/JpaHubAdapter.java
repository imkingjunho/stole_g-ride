package com.gachiga.route;

import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** DB에 저장된 거점을 계약 타입으로 변환하는 어댑터. */
@Component
@Profile("!test")
public class JpaHubAdapter implements HubPort {

    private final HubRepository hubRepository;

    public JpaHubAdapter(HubRepository hubRepository) {
        this.hubRepository = hubRepository;
    }

    @Override
    public Optional<HubInfo> findById(Long hubId) {
        return hubRepository.findById(hubId).map(this::toInfo);
    }

    @Override
    public List<HubInfo> findAll() {
        return hubRepository.findAll().stream().map(this::toInfo).toList();
    }

    private HubInfo toInfo(Hub hub) {
        return new HubInfo(
                hub.getId(), hub.getName(), hub.getLat(), hub.getLng(), hub.getType().name());
    }
}

package com.gachiga.route;

import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 테스트 프로파일에서 애플리케이션 컨텍스트에 제공하는 거점 port. */
@Component
@Profile("test")
public class TestHubAdapter implements HubPort {

    @Override
    public Optional<HubInfo> findById(Long hubId) {
        return Optional.empty();
    }

    @Override
    public List<HubInfo> findAll() {
        return List.of();
    }
}

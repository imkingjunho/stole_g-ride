package com.gachiga.route;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 운영 DB가 비어 있을 때만 서비스 기본 거점 6개를 등록한다. */
@Component
@Profile("!test")
public class HubSeedRunner implements ApplicationRunner {

    private final HubRepository hubRepository;

    public HubSeedRunner(HubRepository hubRepository) {
        this.hubRepository = hubRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (hubRepository.count() > 0) {
            return;
        }
        hubRepository.saveAll(List.of(
                new Hub("전남대 후문", 35.17865, 126.90892, Hub.HubType.CAMPUS),
                new Hub("전남대 정문", 35.17822, 126.90793, Hub.HubType.CAMPUS),
                new Hub("예대 삼거리", 35.17936, 126.90382, Hub.HubType.CAMPUS),
                new Hub("경신여고", 35.17062, 126.88876, Hub.HubType.SCHOOL),
                new Hub("유스퀘어", 35.16104, 126.88031, Hub.HubType.TERMINAL),
                new Hub("광주송정역", 35.13768, 126.79148, Hub.HubType.STATION)));
    }
}

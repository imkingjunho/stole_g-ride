package com.gachiga.route;

import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 기존 거점 ID와 연결된 요청을 보존하면서 지정 승차 지점으로 갱신한다. */
@Component
@Profile("!test")
public class HubSeedRunner implements ApplicationRunner {

    private final HubRepository hubRepository;

    public HubSeedRunner(HubRepository hubRepository) {
        this.hubRepository = hubRepository;
    }

    private record Pickup(String formerName, String name, double lat, double lng, Hub.HubType type) {}

    private static final List<Pickup> PICKUPS = List.of(
            new Pickup("전남대 후문", "전남대 후문 스타벅스", 35.1772166772749, 126.912456703755, Hub.HubType.CAMPUS),
            new Pickup("전남대 정문", "전남대 정문 이디야 커피", 35.17230611135343, 126.90530361924392, Hub.HubType.CAMPUS),
            new Pickup("예대 삼거리", "전대 예대점 CU", 35.1807257300084, 126.90421466731, Hub.HubType.CAMPUS),
            new Pickup("경신여고", "경신여고 새약손 한방병원", 35.1730500119223, 126.887698176817, Hub.HubType.SCHOOL),
            new Pickup("유스퀘어", "유스퀘어 택시정류장", 35.1610463, 126.8785098, Hub.HubType.TERMINAL),
            new Pickup("광주송정역", "광주송정역 택시정류장", 35.1363353244415, 126.790300414821, Hub.HubType.STATION));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var existing = hubRepository.findAll();
        for (var pickup : PICKUPS) {
            var matches = existing.stream().filter(h -> pickup.name().equals(h.getName())
                    || pickup.formerName().equals(h.getName())
                    || ("예대 삼거리".equals(pickup.formerName()) && "전남대 예대 삼거리".equals(h.getName()))).toList();
            if (matches.isEmpty()) {
                hubRepository.save(new Hub(pickup.name(), pickup.lat(), pickup.lng(), pickup.type()));
            } else {
                matches.forEach(h -> h.updatePickup(pickup.name(), pickup.lat(), pickup.lng()));
            }
        }
    }
}

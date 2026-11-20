package com.gachiga.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HubSeedRunnerTest {
    @Test
    void updatesExistingLandmarksWithoutChangingIdsOrDuplicatingOnRestart() {
        var gate = new Hub("전남대 정문", 35.17, 126.91, Hub.HubType.CAMPUS);
        ReflectionTestUtils.setField(gate, "id", 42L);
        var crossing = new Hub("전남대 예대 삼거리", 35.17, 126.90, Hub.HubType.CAMPUS);
        List<Hub> rows = new ArrayList<>(List.of(gate, crossing));
        var repository = mock(HubRepository.class);
        when(repository.findAll()).thenAnswer(call -> List.copyOf(rows));
        when(repository.save(any(Hub.class))).thenAnswer(call -> {
            Hub added = call.getArgument(0);
            rows.add(added);
            return added;
        });
        var runner = new HubSeedRunner(repository);

        runner.run(null);
        runner.run(null);

        assertThat(rows).hasSize(6);
        assertThat(gate.getId()).isEqualTo(42L);
        assertThat(gate.getName()).isEqualTo("전남대 정문 이디야 커피");
        assertThat(gate.getLat()).isEqualTo(35.17230611135343);
        assertThat(gate.getLng()).isEqualTo(126.90530361924392);
        assertThat(crossing.getName()).isEqualTo("전대 예대점 CU");
    }
}

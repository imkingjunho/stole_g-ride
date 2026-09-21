package com.gachiga.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class HubCatalogTest {

    @Test
    void shouldReturnKnownHub() {
        Hub mainGate = HubCatalog.getByName("전남대 정문");
        assertNotNull(mainGate);
        assertEquals("전남대 정문", mainGate.name());
    }

    @Test
    void shouldHaveAtLeastSixHubs() {
        assertEquals(6, HubCatalog.all().size());
    }
}

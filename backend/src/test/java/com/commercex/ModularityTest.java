package com.commercex;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(CommerceXApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        modules.verify();
    }
}

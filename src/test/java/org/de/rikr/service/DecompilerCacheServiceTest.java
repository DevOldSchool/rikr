package org.de.rikr.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DecompilerCacheServiceTest {
    @Test
    void returnsStructuredFailureForMissingClass() {
        try (DecompilerCacheService service = new DecompilerCacheService(message -> {
        })) {
            DecompileResult result = service.decompileClassAsync(null).join();

            assertFalse(result.success());
            assertNotNull(result.message());
        }
    }
}

package org.de.rikr.theme.providers;

import org.de.rikr.theme.SystemAppearance;
import org.de.rikr.theme.Theme;
import org.de.rikr.theme.ThemeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SystemThemeProviderTest {
    private final SystemThemeProvider provider = new SystemThemeProvider();

    @AfterEach
    void restoreDetection() {
        SystemAppearance.setDetector(null);
    }

    @Test
    void followsTheSystemAppearance() {
        SystemAppearance.setDetector(() -> true);
        assertEquals("Dark", provider.resolved().displayName());
        assertSame(Theme.DARK_BACKGROUND_COLOR, provider.palette().BACKGROUND_COLOR);

        SystemAppearance.setDetector(() -> false);
        assertEquals("Light", provider.resolved().displayName());
        assertSame(Theme.LIGHT_BACKGROUND_COLOR, provider.palette().BACKGROUND_COLOR);
    }

    @Test
    void isOfferedInTheRegistryUnderAStableId() {
        assertNotNull(ThemeRegistry.getById(SystemThemeProvider.ID));
        assertEquals("Match system", ThemeRegistry.getById(SystemThemeProvider.ID).displayName());
    }
}

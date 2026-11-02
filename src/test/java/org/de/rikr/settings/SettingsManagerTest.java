package org.de.rikr.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsManagerTest {

    @Test
    void theDefaultEditorFontSizeFollowsTheInterfaceScale() {
        assertEquals(26f, SettingsManager.editorFontSize(Settings.DEFAULT_FONT_SIZE, 2f));
        assertEquals(13f, SettingsManager.editorFontSize(Settings.DEFAULT_FONT_SIZE, 1f));
    }

    @Test
    void aSizeChosenInSettingsIsUsedAsItIs() {
        assertEquals(16f, SettingsManager.editorFontSize(16, 2f));
    }
}

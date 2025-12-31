package org.de.rikr.theme.laf;

import javax.swing.*;
import java.awt.*;

/**
 * Applies custom Look & Feel defaults that should persist across theme changes.
 * <p>
 * FlatLaf resets many UIDefaults during {@code setup()} calls. Always call
 * {@link #apply()} immediately after selecting a theme (both at startup and
 * at runtime switches) so our overrides remain in effect.
 */
public final class LafDefaults {
    private LafDefaults() {
    }

    /**
     * Apply all UIManager customizations used by Rikr.
     */
    public static void apply() {
        // Hide the dotted grip in JSplitPane divider (keep our clean look)
        UIManager.put("SplitPaneDivider.gripDotCount", 0);

        // Disable focus border completely
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Component.focusColor", new Color(0, 0, 0, 0));

        // Text components
        UIManager.put("TextComponent.focusWidth", 0);
        UIManager.put("TextComponent.focusColor", new Color(0, 0, 0, 0));

        // Buttons
        UIManager.put("Button.focusedBorderColor", new Color(0, 0, 0, 0));

        // Tables, lists, trees
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        UIManager.put("Tree.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        UIManager.put("List.focusCellHighlightBorder", BorderFactory.createEmptyBorder());

        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Component.arc", 0);

        // Disable FlatLaf focused outline border
        UIManager.put("Component.focusedBorderColor", new Color(0, 0, 0, 0));
        UIManager.put("Component.focusedBorderWidth", 0);

        // Disable outline variants (very important)
        UIManager.put("Outline.focusedBorderColor", new Color(0, 0, 0, 0));
        UIManager.put("Outline.focusedBorderWidth", 0);

        // Defensive: also remove non-focused outline
        UIManager.put("Outline.borderColor", new Color(0, 0, 0, 0));
        UIManager.put("Outline.borderWidth", 0);

        // Remove ALL FlatLaf outlines (focused + unfocused)
        UIManager.put("Outline.borderWidth", 0);
        UIManager.put("Outline.borderColor", new Color(0, 0, 0, 0));

        UIManager.put("Component.borderWidth", 0);
        UIManager.put("Component.borderColor", new Color(0, 0, 0, 0));

        // Defensive: some components use these
        UIManager.put("TextComponent.borderWidth", 0);
        UIManager.put("TextComponent.borderColor", new Color(0, 0, 0, 0));

        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("TextField.border", BorderFactory.createEmptyBorder());
        UIManager.put("TextArea.border", BorderFactory.createEmptyBorder());
        UIManager.put("FormattedTextField.border", BorderFactory.createEmptyBorder());
    }
}

package org.de.rikr.theme.laf;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.icons.FlatCheckBoxIcon;
import com.formdev.flatlaf.ui.FlatButtonBorder;
import com.formdev.flatlaf.ui.FlatRoundBorder;
import com.formdev.flatlaf.ui.FlatTextBorder;

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
    // Control edges need 3:1 against the panel behind them (WCAG 2.2 SC 1.4.11).
    public static final Color LIGHT_CONTROL_BORDER = new Color(0x8C, 0x8F, 0x94);
    public static final Color DARK_CONTROL_BORDER = new Color(0x7A, 0x7E, 0x85);

    // The focus ring is the accent colour, as SearchFields draws it.
    public static final Color LIGHT_FOCUS = new Color(0x35, 0x74, 0xF0);
    public static final Color DARK_FOCUS = new Color(0x54, 0x8A, 0xF7);

    private LafDefaults() {
    }

    /**
     * Apply all UIManager customizations used by Rikr.
     */
    public static void apply() {
        boolean dark = FlatLaf.isLafDark();
        Color border = dark ? DARK_CONTROL_BORDER : LIGHT_CONTROL_BORDER;
        Color focus = dark ? DARK_FOCUS : LIGHT_FOCUS;

        // Hide the dotted grip in JSplitPane divider (keep our clean look)
        UIManager.put("SplitPaneDivider.gripDotCount", 0);
        UIManager.put("Component.arc", 0);

        // Controls keep a visible edge, so people can tell what they can click and whether a box is ticked.
        UIManager.put("Component.borderWidth", 1);
        UIManager.put("Component.borderColor", border);
        UIManager.put("Button.borderWidth", 1);
        UIManager.put("Button.borderColor", border);
        // FlatLaf reads a button's edge from these gradient ends first, and the IntelliJ themes set them.
        UIManager.put("Button.startBorderColor", border);
        UIManager.put("Button.endBorderColor", border);
        UIManager.put("CheckBox.icon.borderColor", border);

        // A thin ring outside the edge shows where keyboard focus is (WCAG 2.2 SC 2.4.7).
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 1);
        UIManager.put("Component.focusColor", focus);
        UIManager.put("Component.focusedBorderColor", focus);
        UIManager.put("Button.focusedBorderColor", focus);
        UIManager.put("CheckBox.icon.focusedBorderColor", focus);

        // Code views and lists sit edge to edge in their panels.
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("TextArea.border", BorderFactory.createEmptyBorder());

        // FlatLaf's borders read their colours when they are made, so make them after the colours are set.
        UIManager.put("Button.border", new FlatButtonBorder());
        UIManager.put("ToggleButton.border", new FlatButtonBorder());
        UIManager.put("ComboBox.border", new FlatRoundBorder());
        UIManager.put("Spinner.border", new FlatRoundBorder());
        UIManager.put("TextField.border", new FlatTextBorder());
        UIManager.put("FormattedTextField.border", new FlatTextBorder());
        UIManager.put("PasswordField.border", new FlatTextBorder());
        UIManager.put("CheckBox.icon", new FlatCheckBoxIcon());
    }
}

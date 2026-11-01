package org.de.rikr.settings;

import org.de.rikr.resources.Fonts;
import org.de.rikr.theme.Theme;
import org.de.rikr.theme.ThemePalette;
import org.de.rikr.theme.ThemeProvider;
import org.de.rikr.theme.ThemeRegistry;
import org.de.rikr.theme.laf.LafDefaults;
import org.de.rikr.theme.SystemAppearance;
import org.de.rikr.theme.providers.SystemThemeProvider;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class SettingsManager {
    private static Settings current = Settings.Store.load();

    public interface Listener {
        void onSettingsApplied(Settings settings);
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static Settings getCurrent() {
        return current;
    }

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    public static void applyAndSave(Settings settings, Component anyComponentInApp) {
        current = settings;
        Settings.Store.save(settings);

        applyThemeLaf(anyComponentInApp);
        notifyListeners();
    }

    public static void applyThemeStartup() {
        ThemeProvider provider = resolveProvider();
        if (provider == null) {
            provider = ThemeRegistry.getById("dark.flatij");
        }

        if (provider != null) {
            provider.setupLookAndFeel();
            applyPalette(provider.palette());
        }

        LafDefaults.apply();
    }

    /**
     * While the theme is "Match system", apply the other theme when the system appearance changes. Polled every
     * few seconds: Java gives no event for it, and a poll of the appearance is cheap.
     */
    public static void watchSystemAppearance() {
        Timer timer = new Timer(5000, event -> {
            if (!SystemThemeProvider.ID.equals(current.getThemeId())) {
                return;
            }
            boolean dark = SystemAppearance.isDark();
            if (dark != FlatLaf.isLafDark()) {
                applyThemeLaf(null);
                notifyListeners();
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    public static void applyThemeLaf(Component anyComponentInApp) {
        applyThemeStartup();

        Window targetWindow = null;
        if (anyComponentInApp instanceof Window w) {
            targetWindow = w;
        } else if (anyComponentInApp != null) {
            targetWindow = SwingUtilities.getWindowAncestor(anyComponentInApp);
        }

        if (targetWindow != null) {
            SwingUtilities.updateComponentTreeUI(targetWindow);
            targetWindow.invalidate();
            targetWindow.validate();
            targetWindow.repaint();
        } else {
            for (Window window : Window.getWindows()) {
                if (window != null) {
                    SwingUtilities.updateComponentTreeUI(window);
                    window.invalidate();
                    window.validate();
                    window.repaint();
                }
            }
        }
    }

    public static Font getEditorFont() {
        String family = current.getFontFamily();
        float size = editorFontSize(current.getFontSize(), UIScale.getUserScaleFactor());
        if (family == null || family.isEmpty()) {
            Font font = Fonts.getDefaultFont();
            return font != null ? font.deriveFont(size) : new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size));
        }

        return new Font(family, Font.PLAIN, 1).deriveFont(size);
    }

    /**
     * The editor font's size on screen. Left at the default, it follows the interface scale, so code is as large as
     * everything else when Rikr is scaled up; a size chosen in Settings is used as it is.
     */
    static float editorFontSize(int chosen, float interfaceScale) {
        return chosen == Settings.DEFAULT_FONT_SIZE ? chosen * interfaceScale : chosen;
    }

    private static void notifyListeners() {
        for (Listener listener : listeners) {
            try {
                listener.onSettingsApplied(current);
            } catch (Exception ignored) {
            }
        }
    }

    public static void refreshThemeFor(Component componentSubtree) {
        updateComponentTree(componentSubtree);
        notifyListeners();
    }

    public static void updateComponentTree(Component component) {
        if (component == null) {
            return;
        }

        SwingUtilities.updateComponentTreeUI(component);
        component.invalidate();
        component.validate();
        component.repaint();
    }

    public static void updateAllWindows() {
        for (Window window : Window.getWindows()) {
            if (window != null) {
                SwingUtilities.updateComponentTreeUI(window);
                window.invalidate();
                window.validate();
                window.repaint();
            }
        }
    }

    public static Color getScrollBarTrackColor() {
        Color color = UIManager.getColor("ScrollBar.track");

        return color != null ? color : Theme.CONTENT_BACKGROUND_COLOR;
    }

    private static ThemeProvider resolveProvider() {
        String id = (current != null && current.getThemeId() != null && !current.getThemeId().isEmpty()) ? current.getThemeId() : "dark.flatij";
        ThemeProvider themeProvider = ThemeRegistry.getById(id);
        if (themeProvider == null) {
            themeProvider = ThemeRegistry.getById("dark.flatij");
        }

        return themeProvider;
    }

    private static void applyPalette(ThemePalette palette) {
        if (palette == null) {
            return;
        }

        // Core backgrounds
        Theme.BACKGROUND_COLOR = palette.BACKGROUND_COLOR;
        Theme.CONTENT_BACKGROUND_COLOR = palette.CONTENT_BACKGROUND_COLOR;
        // UI accents & content colors
        Theme.NAVBAR_BUTTON_SELECTED_COLOR = palette.NAVBAR_BUTTON_SELECTED_COLOR;
        Theme.SEARCH_BUTTON_SELECTED_COLOR = palette.SEARCH_BUTTON_SELECTED_COLOR;
        Theme.CONTENT_SELECTED_HIGHLIGHT_COLOR = palette.CONTENT_SELECTED_HIGHLIGHT_COLOR;
        Theme.SEARCH_HIGHLIGHT_COLOR = palette.SEARCH_HIGHLIGHT_COLOR;
        Theme.CONTENT_TEXT_COLOR = palette.CONTENT_TEXT_COLOR;
        Theme.CONTENT_BORDER_COLOR = palette.CONTENT_BORDER_COLOR;
        Theme.CONTENT_HIGHLIGHT_TEXT_COLOR = palette.CONTENT_HIGHLIGHT_TEXT_COLOR;
        // Syntax highlight
        Theme.SYNTAX_HIGHLIGHT_DEFAULT_COLOR = palette.SYNTAX_HIGHLIGHT_DEFAULT_COLOR;
        Theme.SYNTAX_HIGHLIGHT_KEYWORD_COLOR = palette.SYNTAX_HIGHLIGHT_KEYWORD_COLOR;
        Theme.SYNTAX_HIGHLIGHT_COMMENT_COLOR = palette.SYNTAX_HIGHLIGHT_COMMENT_COLOR;
        Theme.SYNTAX_HIGHLIGHT_NUMBER_COLOR = palette.SYNTAX_HIGHLIGHT_NUMBER_COLOR;
        Theme.SYNTAX_HIGHLIGHT_STRING_COLOR = palette.SYNTAX_HIGHLIGHT_STRING_COLOR;
        Theme.SYNTAX_HIGHLIGHT_ANNOTATION_COLOR = palette.SYNTAX_HIGHLIGHT_ANNOTATION_COLOR;
    }
}

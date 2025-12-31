package org.de.rikr.theme;

import org.de.rikr.theme.providers.DarkThemeProvider;
import org.de.rikr.theme.providers.LightThemeProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Registry for available themes. New themes can register at startup (static block)
 * or via ServiceLoader in the future.
 */
public final class ThemeRegistry {
    private static final List<ThemeProvider> PROVIDERS = new ArrayList<>();

    static {
        // Register built-in themes
        register(new DarkThemeProvider());
        register(new LightThemeProvider());
    }

    private ThemeRegistry() {
    }

    public static void register(ThemeProvider provider) {
        if (provider == null) return;
        // Replace any provider with same id
        synchronized (PROVIDERS) {
            PROVIDERS.removeIf(p -> Objects.equals(p.id(), provider.id()));
            PROVIDERS.add(provider);
        }
    }

    public static ThemeProvider getById(String id) {
        if (id == null) return null;
        synchronized (PROVIDERS) {
            for (ThemeProvider p : PROVIDERS) {
                if (id.equals(p.id())) return p;
            }
        }
        return null;
    }

    public static ThemeProvider[] all() {
        synchronized (PROVIDERS) {
            // return array for JComboBox model
            return PROVIDERS.toArray(new ThemeProvider[0]);
        }
    }

    public static List<ThemeProvider> list() {
        synchronized (PROVIDERS) {
            return Collections.unmodifiableList(new ArrayList<>(PROVIDERS));
        }
    }
}

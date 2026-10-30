package org.de.rikr.resources;

import org.de.rikr.ui.ClassNodeImages;

import org.de.rikr.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Images {
    private static final Map<String, ImageIcon> IMAGES = new HashMap<>();

    public static void loadImages() {
        String[] imageNames = {
            "folder",
            "search",
            "mapping",
            "references",
            "bookmarks",
            "flow",
            "instruction"
        };

        for (String imageName : imageNames) {
            URL imageUrl = ClassNodeImages.class.getResource("/images/" + imageName + ".png");
            if (imageUrl == null) {
                throw new RuntimeException("Image file not found: " + imageName);
            }

            IMAGES.put(imageName, new ImageIcon(imageUrl));
        }
    }

    /**
     * The application icon at each size the window and task bar may ask for.
     */
    public static List<Image> appIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            icons.add(load("app-icon-" + size).getImage());
        }
        return icons;
    }

    /**
     * The application icon drawn on the macOS icon grid, with the margin the Dock expects around it.
     */
    public static Image macAppIcon() {
        return load("app-icon-mac").getImage();
    }

    private static ImageIcon load(String name) {
        URL url = Images.class.getResource("/images/" + name + ".png");
        if (url == null) {
            throw new IllegalStateException("Image file not found: " + name);
        }
        return new ImageIcon(url);
    }

    public static ImageIcon getImage(String imageName) {
        return IMAGES.get(imageName);
    }

    // Dark enough for 3:1 against the light theme's panels (WCAG 2.2 SC 1.4.11).
    private static final Color LIGHT_THEME_ICON = new Color(0x4a, 0x4f, 0x57);
    private static final Map<String, ImageIcon> DARK_ICONS = new HashMap<>();

    /**
     * The icon for the current theme. The interface icons are drawn light for the dark theme; on a light
     * background they are recoloured dark, keeping their shape and Retina variants.
     */
    public static ImageIcon getThemedImage(String imageName) {
        Color background = Theme.BACKGROUND_COLOR;
        boolean light = background.getRed() + background.getGreen() + background.getBlue() > 3 * 128;
        if (!light) {
            return getImage(imageName);
        }
        return DARK_ICONS.computeIfAbsent(imageName, name -> recolour(getImage(name), LIGHT_THEME_ICON));
    }

    private static ImageIcon recolour(ImageIcon icon, Color color) {
        Image image = icon.getImage();
        if (image instanceof MultiResolutionImage variants) {
            List<Image> recoloured = new ArrayList<>();
            for (Image variant : variants.getResolutionVariants()) {
                recoloured.add(recolour(variant, color));
            }
            return new ImageIcon(new BaseMultiResolutionImage(recoloured.toArray(Image[]::new)));
        }
        return new ImageIcon(recolour(image, color));
    }

    private static BufferedImage recolour(Image image, Color color) {
        ImageIcon loaded = new ImageIcon(image);
        BufferedImage result = new BufferedImage(loaded.getIconWidth(), loaded.getIconHeight(),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.drawImage(loaded.getImage(), 0, 0, null);
        graphics.setComposite(AlphaComposite.SrcIn);
        graphics.setColor(color);
        graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
        graphics.dispose();
        return result;
    }
}

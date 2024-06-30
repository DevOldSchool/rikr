package org.de.rikr.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

public class Images {
    public static HashMap<String, ImageIcon> images = new HashMap<>();

    public static void loadImages() {
        String[] imageNames = {
                "class_binary",
                "class",
                "interface",
                "field_default",
                "field_public",
                "field_private",
                "field_protected",
                "method_default",
                "method_public",
                "method_private",
                "method_protected",
                "abstract_overlay",
                "final_overlay",
                "static_overlay",
        };

        for (String imageName : imageNames) {
            URL iconUrl = Images.class.getResource("/images/" + imageName + ".png");
            if (iconUrl == null) {
                throw new RuntimeException("Icon file not found: " + imageName);
            }

            images.put(imageName, new ImageIcon(iconUrl));
        }

        String[] types = {"field", "method"};
        String[] accesses = {"default", "public", "private", "protected"};
        String[] overlays = {"abstract", "static", "final"};

        for (String type : types) {
            for (String access : accesses) {
                ArrayList<String> overlayCombinations = getOverlayCombinations(overlays);
                for (String overlayCombination : overlayCombinations) {
                    String key = type + "_" + access + (overlayCombination.isEmpty() ? "" : "_" + overlayCombination);

                    // Combine base image with each overlay in the combination
                    ImageIcon combinedImage = getImage(type + "_" + access);
                    for (String overlay : overlayCombination.split("_")) {
                        if (!overlay.isEmpty()) {
                            ImageIcon overlayImage = getImage(overlay + "_overlay");
                            combinedImage = combineIcons(combinedImage, overlayImage);
                        }
                    }

                    images.put(key, combinedImage);
                }
            }
        }
    }

    private static ImageIcon combineIcons(ImageIcon icon1, ImageIcon icon2) {
        // Create a new BufferedImage that's big enough to hold both icons
        int maxWidth = Math.max(icon1.getIconWidth(), icon2.getIconWidth());
        int maxHeight = Math.max(icon1.getIconHeight(), icon2.getIconHeight());
        BufferedImage image = new BufferedImage(maxWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);

        // Draw the icons onto the new image
        Graphics g = image.getGraphics();
        g.drawImage(icon1.getImage(), 0, 0, null);
        g.drawImage(icon2.getImage(), 0, 0, null);
        g.dispose();

        // Return a new ImageIcon that holds the combined image
        return new ImageIcon(image);
    }

    private static ArrayList<String> getOverlayCombinations(String[] overlays) {
        ArrayList<String> combinations = new ArrayList<>();
        int n = overlays.length;

        for (int i = 0; i < (1 << n); i++) {
            StringBuilder combination = new StringBuilder();

            for (int j = 0; j < n; j++) {
                if ((i & (1 << j)) != 0) {
                    if (!combination.isEmpty()) {
                        combination.append("_");
                    }

                    combination.append(overlays[j]);
                }
            }
            
            combinations.add(combination.toString());
        }

        return combinations;
    }

    public static ImageIcon getImage(String imageName) {
        return images.get(imageName);
    }
}

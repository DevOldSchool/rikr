package org.de.rikr.resources;

import org.de.rikr.ui.ClassNodeImages;

import javax.swing.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Images {
    private static final Map<String, ImageIcon> IMAGES = new HashMap<>();

    public static void loadImages() {
        String[] imageNames = {
            "folder",
            "search",
            "match-case",
            "match-word",
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

    public static ImageIcon getImage(String imageName) {
        return IMAGES.get(imageName);
    }
}

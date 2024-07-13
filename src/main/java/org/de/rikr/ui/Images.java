package org.de.rikr.ui;

import javax.swing.*;
import java.net.URL;
import java.util.HashMap;

public class Images {
    public static HashMap<String, ImageIcon> images = new HashMap<>();

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

            images.put(imageName, new ImageIcon(imageUrl));
        }
    }

    public static ImageIcon getImage(String imageName) {
        return images.get(imageName);
    }
}

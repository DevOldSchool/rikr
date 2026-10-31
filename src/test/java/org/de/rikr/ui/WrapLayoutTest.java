package org.de.rikr.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.FlowLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WrapLayoutTest {
    @Test
    void preferredHeightCoversEveryRowTheChildrenWrapInto() {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        for (int i = 0; i < 4; i++) {
            JPanel chip = new JPanel();
            chip.setPreferredSize(new Dimension(100, 20));
            panel.add(chip);
        }
        // Room for two chips a row: four chips need two rows.
        panel.setSize(230, 10);
        Dimension preferred = panel.getLayout().preferredLayoutSize(panel);
        assertEquals(2 * 20 + 2 + 2 * 2, preferred.height);

        panel.setSize(500, 10);
        assertEquals(20 + 2 * 2, panel.getLayout().preferredLayoutSize(panel).height);
    }
}

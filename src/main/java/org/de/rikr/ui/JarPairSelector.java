package org.de.rikr.ui;

import javax.swing.*;
import java.util.List;

/**
 * Picks a source and a target JAR, keeping the two different whenever more than one JAR is loaded.
 */
class JarPairSelector {
    private final JComboBox<String> sourceBox = new JComboBox<>();
    private final JComboBox<String> targetBox = new JComboBox<>();
    private final Runnable onChange;
    private boolean updating;

    JarPairSelector(Runnable onChange) {
        this.onChange = onChange;
    }

    void init() {
        sourceBox.addActionListener(event -> selectionChanged());
        targetBox.addActionListener(event -> selectionChanged());
    }

    JComboBox<String> sourceBox() {
        return sourceBox;
    }

    JComboBox<String> targetBox() {
        return targetBox;
    }

    /**
     * Offer these JARs, keeping the current choices when they are still loaded.
     */
    void setJars(List<String> jarNames) {
        String previousSource = source();
        String previousTarget = target();
        updating = true;
        sourceBox.removeAllItems();
        targetBox.removeAllItems();
        jarNames.forEach(jarName -> {
            sourceBox.addItem(jarName);
            targetBox.addItem(jarName);
        });
        select(sourceBox, previousSource, 0);
        select(targetBox, previousTarget, jarNames.size() > 1 ? 1 : 0);
        ensureDifferentJars();
        updating = false;
    }

    String source() {
        return (String) sourceBox.getSelectedItem();
    }

    String target() {
        return (String) targetBox.getSelectedItem();
    }

    boolean hasPair() {
        String source = source();
        String target = target();
        return source != null && target != null && !source.equals(target);
    }

    private void selectionChanged() {
        if (updating) {
            return;
        }
        updating = true;
        ensureDifferentJars();
        updating = false;
        onChange.run();
    }

    private void ensureDifferentJars() {
        String source = source();
        if (source == null || !source.equals(target())) {
            return;
        }
        for (int index = 0; index < targetBox.getItemCount(); index++) {
            if (!source.equals(targetBox.getItemAt(index))) {
                targetBox.setSelectedIndex(index);
                return;
            }
        }
    }

    private static void select(JComboBox<String> comboBox, String preferred, int defaultIndex) {
        if (preferred != null) {
            comboBox.setSelectedItem(preferred);
        }
        if (comboBox.getSelectedIndex() < 0 && comboBox.getItemCount() > defaultIndex) {
            comboBox.setSelectedIndex(defaultIndex);
        }
    }
}

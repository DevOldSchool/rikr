package org.de.rikr;

import com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme;
import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.ui.ClassNodeImages;
import org.de.rikr.ui.ClassViewer;
import org.de.rikr.ui.Fonts;
import org.de.rikr.ui.Images;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.Map;

public class Rikr {
    private ClassViewer userInterface;
    private ClassProcessor processor;

    public Rikr() {
        userInterface = new ClassViewer(this);
    }

    public void start() {
        userInterface.init();
        processor = new ClassProcessor(userInterface.getLogPanel());
    }

    public ClassViewer getUserInterface() {
        return userInterface;
    }

    public ClassProcessor getProcessor() {
        return processor;
    }

    public void log(String message) {
        userInterface.getLogPanel().log(message);
    }

    public void loadJarFiles(File[] jarFiles) {
        processor.processJarFiles(jarFiles);
        userInterface.updateTree(processor.getJarClassesMap());
    }

    public void loadClassFiles(File[] classFiles) {
        processor.processClassFiles(classFiles);
        userInterface.updateTree(processor.getJarClassesMap());
    }

    public Map<String, List<ClassNode>> getJarClassesMap() {
        return processor.getJarClassesMap();
    }

    public void removeJar(String jarName) {
        processor.getJarClassesMap().remove(jarName);
    }

    public void removeClass(String jarName, ClassNode classNode) {
        List<ClassNode> classNodes = processor.getJarClassesMap().get(jarName);

        if (classNodes != null) {
            classNodes.remove(classNode);
        }
    }

    public List<ClassNode> getClasses(String jarName) {
        return processor.getJarClassesMap().get(jarName);
    }

    public static void main(String[] args) {
        if (SystemInfo.isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.appearance", "system");
            System.setProperty("apple.awt.application.name", "Rikr");
        }

        // Modify look and feel defaults
        UIManager.put("SplitPaneDivider.gripDotCount", 0);

        Images.loadImages();
        ClassNodeImages.loadImages();
        Fonts.loadFonts();

        SwingUtilities.invokeLater(() -> {
            FlatDarkFlatIJTheme.setup();
            Rikr rikr = new Rikr();
            rikr.start();
        });
    }
}

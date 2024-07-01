package org.de.rikr;

import com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme;
import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.ui.*;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class Rikr {
    private final ClassViewer userInterface;
    private ClassProcessor processor;

    public Rikr() {
        userInterface = new ClassViewer(this);
    }

    public void start() {
        userInterface.init();
        processor = new ClassProcessor(userInterface.getLogPanel());
    }

    public void loadJarFiles(File[] jarFiles) {
        processor.processJarFiles(jarFiles);
        userInterface.updateTree(processor.getJarClassesMap());
    }

    public void loadClassFiles(File[] classFiles) {
        processor.processClassFiles(classFiles);
        userInterface.updateTree(processor.getJarClassesMap());
    }

    public void displayBytecode(ClassNode classNode) {
        userInterface.displayBytecode(classNode);
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

    public void clearContent() {
        userInterface.clearContent();
    }

    public ContentPanel getContentPanel() {
        return userInterface.getContentPanel();
    }

    public JTextPane getContentPane() {
        return userInterface.getContentPane();
    }

    public void log(String message) {
        userInterface.getLogPanel().log(message);
    }

    public TreePanel getTreePanel() {
        return userInterface.getTreePanel();
    }

    public ClassViewer getUserInterface() {
        return userInterface;
    }

    public static void main(String[] args) {
        if (SystemInfo.isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.appearance", "system");
            System.setProperty("apple.awt.application.name", "Rikr");
        }

        Images.loadImages();
        Fonts.loadFonts();

        SwingUtilities.invokeLater(() -> {
            FlatDarkFlatIJTheme.setup();
            Rikr rikr = new Rikr();
            rikr.start();
        });
    }
}

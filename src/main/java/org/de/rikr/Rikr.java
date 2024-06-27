package org.de.rikr;

import com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme;
import org.de.rikr.ui.ClassViewer;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class Rikr {
    private final ClassViewer userInterface;
    private final ClassProcessor processor;

    public Rikr() {
        userInterface = new ClassViewer(this);
        processor = new ClassProcessor();
    }

    public void start() {
        userInterface.init();
    }

    public void loadJarFiles(File[] jarFiles) {
        processor.processJarFiles(jarFiles);
        userInterface.updateTree(processor.getJarClassesMap());
    }

    public void loadClassFiles(File[] classFiles) {
        processor.processClassFiles(classFiles);
        userInterface.updateTree(processor.getJarClassesMap());
    }

    public void displayClassDetails(ClassNode classNode) {
        userInterface.displayClassDetails(classNode);
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatDarkFlatIJTheme.setup();
            Rikr rikr = new Rikr();
            rikr.start();
        });
    }
}

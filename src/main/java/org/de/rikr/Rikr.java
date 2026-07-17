package org.de.rikr;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.resources.Fonts;
import org.de.rikr.resources.Images;
import org.de.rikr.service.DecompileResult;
import org.de.rikr.service.ClassMatch;
import org.de.rikr.service.RenameResult;
import org.de.rikr.service.RenameService;
import org.de.rikr.service.SearchQuery;
import org.de.rikr.service.SearchResult;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.ui.ClassNodeImages;
import org.de.rikr.ui.ClassViewer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Rikr {
    private final ClassViewer userInterface;
    private final RenameService renameService;
    private ClassProcessor processor;

    public Rikr() {
        renameService = new RenameService();
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
        userInterface.updateTree(processor.getJarClassesView());
    }

    public void loadClassFiles(File[] classFiles) {
        processor.processClassFiles(classFiles);
        userInterface.updateTree(processor.getJarClassesView());
    }

    public Map<String, List<ClassNode>> getJarClassesMap() {
        // Expose read-only view to prevent accidental external mutations
        return processor.getJarClassesView();
    }

    public void removeJar(String jarName) {
        processor.removeJar(jarName);
    }

    public void removeClass(String jarName, ClassNode classNode) {
        processor.removeClass(jarName, classNode);
    }

    public List<ClassNode> getClasses(String jarName) {
        return processor.getClasses(jarName);
    }

    public void setStripAnnotations(boolean stripAnnotations) {
        processor.setStripAnnotations(stripAnnotations);
    }

    public List<ClassNode> findClassesExtending(List<ClassNode> classes, ClassNode baseClassNode) {
        return processor.findClassesExtending(classes, baseClassNode);
    }

    public List<ClassNode> findClassesImplementing(List<ClassNode> classes, ClassNode baseClassNode) {
        return processor.findClassesImplementing(classes, baseClassNode);
    }

    public Map<String, List<ClassNode>> groupBySuperclass(List<ClassNode> classes) {
        return processor.groupBySuperclass(classes);
    }

    public Map<String, List<ClassNode>> groupByInterface(List<ClassNode> classes) {
        return processor.groupByInterface(classes);
    }

    public List<String> getNonStaticFieldDescriptions(ClassNode classNode) {
        return processor.getNonStaticFieldDescriptions(classNode);
    }

    public List<String> getNonStaticMethodDescriptions(ClassNode classNode) {
        return processor.getNonStaticMethodDescriptions(classNode);
    }

    public Map<String, List<ClassNode>> findMatchingClassNodes(String jarToExclude, ClassNode classNodeToMatch) {
        return processor.findMatchingClassNodes(jarToExclude, classNodeToMatch);
    }

    public Map<String, List<ClassMatch>> findSimilarClassNodes(String jarToExclude, ClassNode classNodeToMatch) {
        return processor.findSimilarClassNodes(jarToExclude, classNodeToMatch);
    }

    public String normalizeClassDescriptor(String descriptor) {
        return processor.normalizeClassDescriptor(descriptor);
    }

    public boolean areMethodsBehaviorallyEquivalent(
        List<ClassNode> classNodes1,
        MethodNode methodNode1,
        List<ClassNode> classNodes2,
        MethodNode methodNode2
    ) {
        return processor.areMethodsBehaviorallyEquivalent(classNodes1, methodNode1, classNodes2, methodNode2);
    }

    public void saveClassToFile(ClassNode classNode, File dest) throws IOException {
        processor.saveClassToFile(classNode, dest);
    }

    public void exportJarGroupToJar(String jarName, File destJar) throws IOException {
        processor.exportJarGroupToJar(jarName, destJar);
    }

    public boolean overwriteOriginalJar(String jarName) throws IOException {
        return processor.overwriteOriginalJar(jarName);
    }

    public File getOriginalJarFile(String jarName) {
        return processor.getOriginalJarFile(jarName);
    }

    public String getDecompiledSourceByClass(String internalName) {
        if (processor == null) {
            return null;
        }

        return processor.getDecompiledSourceByClass(internalName);
    }

    public CompletableFuture<DecompileResult> decompileClassAsync(ClassNode classNode) {
        if (processor == null) {
            return CompletableFuture.completedFuture(DecompileResult.failure("Class processor is not initialized."));
        }

        return processor.decompileClassAsync(classNode);
    }

    public List<SearchResult> search(SearchQuery query) {
        if (processor == null) {
            return List.of();
        }

        return processor.search(query);
    }

    public RenameResult renameClassReferences(String jarName, ClassNode classNode, String newName) {
        return renameService.renameClassReferences(getClasses(jarName), classNode.name, newName);
    }

    public RenameResult renameFieldReferences(String jarName, ClassNode owner, FieldNode fieldNode, String newName) {
        return renameService.renameFieldReferences(getClasses(jarName), owner, fieldNode.name, newName);
    }

    public RenameResult renameMethodReferences(String jarName, ClassNode owner, MethodNode methodNode, String newName) {
        return renameService.renameMethodReferences(getClasses(jarName), owner, methodNode.name, newName);
    }

    public void shutdown() {
        if (processor != null) {
            processor.close();
        }
    }

    public static void main(String[] args) {
        if (SystemInfo.isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.appearance", "system");
            System.setProperty("apple.awt.application.name", "Rikr");
        }

        Images.loadImages();
        ClassNodeImages.loadImages();
        Fonts.loadFonts();

        SwingUtilities.invokeLater(() -> {
            SettingsManager.applyThemeStartup();
            Rikr rikr = new Rikr();
            rikr.start();
        });
    }
}

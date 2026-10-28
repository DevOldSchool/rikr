package org.de.rikr;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.resources.Fonts;
import org.de.rikr.resources.Images;
import org.de.rikr.service.ClassMatch;
import org.de.rikr.model.ClassMappingDecision;
import org.de.rikr.model.ClassRef;
import org.de.rikr.service.BookmarkService;
import org.de.rikr.service.DecompileResult;
import org.de.rikr.service.RenameResult;
import org.de.rikr.service.RenameService;
import org.de.rikr.service.RenameOperation;
import org.de.rikr.service.RenamePreview;
import org.de.rikr.service.RenameUndoResult;
import org.de.rikr.service.RenameType;
import org.de.rikr.service.ProjectBookmark;
import org.de.rikr.service.ProjectLayout;
import org.de.rikr.service.ProjectSession;
import org.de.rikr.service.ProjectSessionService;
import org.de.rikr.service.ReferenceResult;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Rikr {
    private final ClassViewer userInterface;
    private RenameService renameService;
    private final BookmarkService bookmarkService;
    private final ProjectSessionService projectSessionService;
    private File currentProjectFile;
    private ClassProcessor processor;

    public Rikr() {
        renameService = new RenameService();
        bookmarkService = new BookmarkService();
        projectSessionService = new ProjectSessionService();
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
        renameService.removeJar(jarName);
        bookmarkService.removeJar(jarName);
        refreshProjectState();
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

    public List<ClassMatch> findMappingCandidates(
        String targetJar,
        ClassNode sourceClass,
        int minimumSimilarityPercent,
        int maximumResults
    ) {
        return processor.findMappingCandidates(targetJar, sourceClass, minimumSimilarityPercent, maximumResults);
    }

    public void acceptClassMapping(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassMatch candidate
    ) {
        processor.acceptClassMapping(sourceJar, targetJar, sourceClass, candidate);
    }

    public void rejectClassMapping(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassNode targetClass
    ) {
        processor.rejectClassMapping(sourceJar, targetJar, sourceClass, targetClass);
    }

    public void clearClassMappingDecisions(String sourceJar, String targetJar, ClassNode sourceClass) {
        processor.clearClassMappingDecisions(sourceJar, targetJar, sourceClass);
    }

    public Optional<ClassMappingDecision> getAcceptedClassMapping(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass
    ) {
        return processor.getAcceptedClassMapping(sourceJar, targetJar, sourceClass);
    }

    public boolean isClassMappingRejected(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassNode targetClass
    ) {
        return processor.isClassMappingRejected(sourceJar, targetJar, sourceClass, targetClass);
    }

    public List<ClassMappingDecision> getAcceptedClassMappings(String sourceJar, String targetJar) {
        return processor.getAcceptedClassMappings(sourceJar, targetJar);
    }

    public List<ReferenceResult> findFieldReads(String jarName, ClassNode owner, FieldNode field) {
        return processor.findFieldReads(jarName, owner, field);
    }

    public List<ReferenceResult> findFieldWrites(String jarName, ClassNode owner, FieldNode field) {
        return processor.findFieldWrites(jarName, owner, field);
    }

    public List<ReferenceResult> findMethodCallers(String jarName, ClassNode owner, MethodNode method) {
        return processor.findMethodCallers(jarName, owner, method);
    }

    public List<ReferenceResult> findMethodCallees(String jarName, ClassNode owner, MethodNode method) {
        return processor.findMethodCallees(jarName, owner, method);
    }

    public List<ReferenceResult> findTypeReferences(String jarName, ClassNode targetClass) {
        return processor.findTypeReferences(jarName, targetClass);
    }

    public List<ClassMappingDecision> autoAcceptUniqueClassMappings(
        String sourceJar,
        String targetJar,
        int minimumSimilarityPercent
    ) {
        return processor.autoAcceptUniqueClassMappings(sourceJar, targetJar, minimumSimilarityPercent);
    }

    public void exportAcceptedClassMappings(File destination, String sourceJar, String targetJar) throws IOException {
        processor.exportAcceptedClassMappings(destination, sourceJar, targetJar);
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

    public String getDecompiledSource(ClassNode classNode) {
        if (processor == null) {
            return null;
        }

        return processor.getDecompiledSource(classNode);
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
        String oldName = classNode.name;
        RenameResult result = renameService.applyClassRename(jarName, getClasses(jarName), classNode, newName);
        processor.renameClassInMappings(jarName, oldName, newName);
        bookmarkService.applyRename(new RenameOperation(
            RenameType.CLASS, jarName, oldName, null, oldName, newName
        ), false, result.declarationOwners());
        processor.refreshAfterRename(jarName);
        refreshProjectState();
        return result;
    }

    public RenameResult renameFieldReferences(String jarName, ClassNode owner, FieldNode fieldNode, String newName) {
        String oldName = fieldNode.name;
        RenameResult result = renameService.applyFieldRename(jarName, getClasses(jarName), owner, fieldNode, newName);
        bookmarkService.applyRename(new RenameOperation(
            RenameType.FIELD, jarName, owner.name, fieldNode.desc, oldName, newName
        ), false, result.declarationOwners());
        processor.refreshAfterRename(jarName);
        refreshProjectState();
        return result;
    }

    public RenameResult renameMethodReferences(String jarName, ClassNode owner, MethodNode methodNode, String newName) {
        String oldName = methodNode.name;
        RenameResult result = renameService.applyMethodRename(jarName, getClasses(jarName), owner, methodNode, newName);
        bookmarkService.applyRename(new RenameOperation(
            RenameType.METHOD, jarName, owner.name, methodNode.desc, oldName, newName
        ), false, result.declarationOwners());
        processor.refreshAfterRename(jarName);
        refreshProjectState();
        return result;
    }

    public RenamePreview previewClassRename(String jarName, ClassNode owner, String newName) {
        return renameService.previewClassRename(getClasses(jarName), owner, newName);
    }

    public RenamePreview previewFieldRename(String jarName, ClassNode owner, FieldNode field, String newName) {
        return renameService.previewFieldRename(getClasses(jarName), owner, field, newName);
    }

    public RenamePreview previewMethodRename(String jarName, ClassNode owner, MethodNode method, String newName) {
        return renameService.previewMethodRename(getClasses(jarName), owner, method, newName);
    }

    public void undoLastRename() {
        Optional<RenameUndoResult> undo = renameService.undoLast(this::getClasses);
        if (undo.isEmpty()) {
            log("No rename to undo.");
            return;
        }
        RenameUndoResult value = undo.get();
        bookmarkService.applyRename(value.operation(), true, value.result().declarationOwners());
        processor.refreshAfterRename(value.operation().jarName());
        if (value.operation().type() == RenameType.CLASS) {
            processor.renameClassInMappings(
                value.operation().jarName(), value.operation().newName(), value.operation().oldName()
            );
        }
        userInterface.updateTree(processor.getJarClassesView());
        refreshProjectState();
        userInterface.displayBytecode(value.affectedClass());
        log("Undid rename " + value.operation().newName() + " -> " + value.operation().oldName() + ".");
    }

    public Set<String> getDirtyJars() {
        return renameService.getDirtyJars();
    }

    public void markJarExported(String jarName) {
        renameService.markExported(jarName);
        refreshProjectState();
    }

    public void commitOverwrittenJar(String jarName) {
        renameService.commitOverwrittenJar(jarName);
        refreshProjectState();
    }

    public boolean toggleBookmark(ProjectBookmark bookmark) {
        boolean added = bookmarkService.toggle(bookmark);
        userInterface.refreshBookmarks();
        log((added ? "Bookmarked " : "Removed bookmark ") + bookmark);
        return added;
    }

    public void removeBookmark(ProjectBookmark bookmark) {
        bookmarkService.remove(bookmark);
        userInterface.refreshBookmarks();
    }

    public List<ProjectBookmark> getBookmarks() {
        return bookmarkService.getBookmarks();
    }

    public boolean openBookmark(ProjectBookmark bookmark) {
        return processor.findClass(new ClassRef(bookmark.jarName(), bookmark.className()))
            .map(owner -> userInterface.openBookmark(bookmark, owner))
            .orElse(false);
    }

    public void saveProject(File destination) throws IOException {
        Map<String, File> savedJars = processor.getOriginalJarFiles();
        List<String> jarPaths = savedJars.values().stream()
            .map(File::getAbsolutePath)
            .sorted()
            .toList();
        // Loose class files have no file to reopen, so state that refers to them cannot be restored.
        List<ClassMappingDecision> mappings = processor.getAllAcceptedClassMappings().stream()
            .filter(mapping -> savedJars.containsKey(mapping.sourceJar()) && savedJars.containsKey(mapping.targetJar()))
            .toList();
        List<RenameOperation> renames = renameService.getHistory().stream()
            .filter(operation -> savedJars.containsKey(operation.jarName()))
            .toList();
        List<ProjectBookmark> bookmarks = bookmarkService.getBookmarks().stream()
            .filter(bookmark -> savedJars.containsKey(bookmark.jarName()))
            .toList();
        int skipped = processor.getAllAcceptedClassMappings().size() - mappings.size()
            + renameService.getHistory().size() - renames.size()
            + bookmarkService.getBookmarks().size() - bookmarks.size();
        if (skipped > 0) {
            log("Project does not include " + skipped
                + " renames, mappings or bookmarks of loose class files; only JARs can be reopened.");
        }
        ProjectSession session = new ProjectSession(
            jarPaths,
            mappings,
            renames,
            bookmarks,
            userInterface.captureProjectLayout()
        );
        projectSessionService.save(destination, session);
        currentProjectFile = destination.getAbsoluteFile();
        userInterface.updateProjectTitle(currentProjectFile);
        log("Saved project to " + currentProjectFile.getAbsolutePath());
    }

    public void openProject(File source) throws IOException {
        ProjectSession session = projectSessionService.load(source);
        File[] jars = session.jarPaths().stream().map(File::new).toArray(File[]::new);
        Set<String> jarNames = new java.util.HashSet<>();
        for (File jar : jars) {
            if (!jar.isFile()) {
                throw new IOException("Project JAR does not exist: " + jar.getAbsolutePath());
            }
            if (!jarNames.add(jar.getName())) {
                throw new IOException("Project contains duplicate JAR names: " + jar.getName());
            }
        }

        ClassProcessor stagedProcessor = new ClassProcessor(userInterface.getLogPanel());
        RenameService stagedRenames = new RenameService();
        try {
            stagedProcessor.processJarFiles(jars);
            for (File jar : jars) {
                if (stagedProcessor.getOriginalJarFile(jar.getName()) == null) {
                    throw new IOException("Unable to load project JAR: " + jar.getAbsolutePath());
                }
            }
            stagedRenames.replay(session.renames(), stagedProcessor::getClasses);
            for (String jarName : stagedRenames.getDirtyJars()) {
                stagedProcessor.refreshAfterRename(jarName);
            }
            stagedProcessor.restoreClassMappings(session.mappings());
        } catch (IOException exception) {
            stagedProcessor.close();
            throw exception;
        } catch (RuntimeException exception) {
            stagedProcessor.close();
            throw new IOException("Unable to restore project: " + exception.getMessage(), exception);
        }

        ClassProcessor previous = processor;
        processor = stagedProcessor;
        renameService = stagedRenames;
        bookmarkService.replaceAll(session.bookmarks());
        currentProjectFile = source.getAbsoluteFile();
        if (previous != null) {
            previous.close();
        }
        userInterface.updateTree(processor.getJarClassesView());
        userInterface.refreshBookmarks();
        userInterface.applyProjectLayout(session.layout());
        userInterface.updateProjectTitle(currentProjectFile);
        refreshProjectState();
        log("Opened project " + currentProjectFile.getAbsolutePath());
    }

    public File getCurrentProjectFile() {
        return currentProjectFile;
    }

    private void refreshProjectState() {
        userInterface.updateDirtyJars(renameService.getDirtyJars());
        userInterface.refreshBookmarks();
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

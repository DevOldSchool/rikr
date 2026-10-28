package org.de.rikr;

import org.de.rikr.behavioral.ClassComparator;
import org.de.rikr.behavioral.MethodComparator;
import org.de.rikr.service.ClassLoadingService;
import org.de.rikr.service.ClassMatch;
import org.de.rikr.model.ClassMappingDecision;
import org.de.rikr.model.ClassRef;
import org.de.rikr.model.Workspace;
import org.de.rikr.service.ClassMappingService;
import org.de.rikr.service.DecompileResult;
import org.de.rikr.service.DecompilerCacheService;
import org.de.rikr.service.JarExportService;
import org.de.rikr.service.LoadedJar;
import org.de.rikr.service.ReferenceIndexService;
import org.de.rikr.service.ReferenceResult;
import org.de.rikr.service.SearchQuery;
import org.de.rikr.service.SearchResult;
import org.de.rikr.service.SearchService;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ClassProcessor implements AutoCloseable {
    private static final int MIN_CLASS_MATCH_PERCENT = 50;
    private static final int MAX_CLASS_MATCHES_PER_JAR = 10;

    private final Logger logger;
    private final Workspace workspace;
    private final ClassLoadingService classLoadingService;
    private final JarExportService jarExportService;
    private final DecompilerCacheService decompilerCacheService;
    private final SearchService searchService;
    private final ReferenceIndexService referenceIndexService;
    private final ClassComparator classComparator;
    private final ClassMappingService classMappingService;
    private final MethodComparator methodComparator;
    private boolean stripAnnotations;

    public ClassProcessor(Logger logger) {
        this.logger = logger;
        this.workspace = new Workspace();
        this.classLoadingService = new ClassLoadingService();
        this.jarExportService = new JarExportService(logger);
        this.decompilerCacheService = new DecompilerCacheService(logger);
        this.searchService = new SearchService();
        this.referenceIndexService = new ReferenceIndexService();
        this.classComparator = new ClassComparator();
        this.classMappingService = new ClassMappingService(classComparator);
        this.methodComparator = new MethodComparator();
        this.stripAnnotations = false;
    }

    public void processJarFiles(File[] jarFiles) {
        for (File file : jarFiles) {
            // JARs are identified by file name, so a second JAR with the same name would silently replace
            // the first one while its renames, bookmarks and mappings stayed behind.
            if (workspace.contains(file.getName())) {
                File loaded = workspace.sourceFile(file.getName());
                logger.log("Skipped " + file.getAbsolutePath() + ": a JAR named " + file.getName()
                    + " is already loaded" + (loaded == null ? "" : " from " + loaded.getAbsolutePath())
                    + ". Remove it first.");
                continue;
            }
            try {
                LoadedJar loadedJar = classLoadingService.readJar(file, stripAnnotations);
                String jarName = loadedJar.jarName();
                classMappingService.clearJar(jarName);
                workspace.addJar(jarName, loadedJar.classes(), loadedJar.originalFile());
                referenceIndexService.indexJar(jarName, loadedJar.classes());
                logger.log("Loaded " + loadedJar.classes().size() + " classes from " + jarName);
                decompilerCacheService.warmUpJar(jarName, file, () -> workspace.classesSnapshot(jarName));
            } catch (IOException e) {
                logger.log("Error processing jar file: " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public void processClassFiles(File[] classFiles) {
        for (File file : classFiles) {
            try {
                ClassNode classNode = classLoadingService.readClass(file, stripAnnotations);
                workspace.addLooseClass(classNode);
                refreshReferenceIndex(Workspace.LOOSE_CLASSES);
            } catch (IOException e) {
                logger.log("Error processing class file: " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public List<ClassNode> findClassesExtending(List<ClassNode> classes, ClassNode baseClassNode) {
        List<ClassNode> extendedClasses = new ArrayList<>();

        for (ClassNode classNode : classes) {
            if (classNode.superName.equals(baseClassNode.name)) {
                extendedClasses.add(classNode);
            }
        }

        return extendedClasses;
    }

    public List<ClassNode> findClassesImplementing(List<ClassNode> classes, ClassNode baseClassNode) {
        List<ClassNode> implementingClasses = new ArrayList<>();

        for (ClassNode classNode : classes) {
            if (classNode.interfaces.contains(baseClassNode.name)) {
                implementingClasses.add(classNode);
            }
        }

        return implementingClasses;
    }

    public Map<String, List<ClassNode>> groupBySuperclass(List<ClassNode> classes) {
        Map<String, List<ClassNode>> superclassGroups = new HashMap<>();

        for (ClassNode classNode : classes) {
            if (classNode.superName != null) {
                superclassGroups
                    .computeIfAbsent(classNode.superName, k -> new ArrayList<>())
                    .add(classNode);
            }
        }

        return superclassGroups;
    }

    public Map<String, List<ClassNode>> groupByInterface(List<ClassNode> classes) {
        Map<String, List<ClassNode>> interfaceGroups = new HashMap<>();

        for (ClassNode classNode : classes) {
            if (classNode.interfaces != null) {
                for (String interfaceName : classNode.interfaces) {
                    interfaceGroups
                        .computeIfAbsent(interfaceName, k -> new ArrayList<>())
                        .add(classNode);
                }
            }
        }

        return interfaceGroups;
    }

    public Map<String, List<ClassNode>> findMatchingClassNodes(String jarToExclude, ClassNode classNodeToMatch) {
        Map<String, List<ClassNode>> matching = new LinkedHashMap<>();

        for (Map.Entry<String, List<ClassNode>> entry : getSortedJarEntries()) {
            if (entry.getKey().equals(jarToExclude)) {
                continue;
            }

            List<ClassNode> classes = classComparator.getMatchingClassNodes(entry.getValue(), classNodeToMatch);
            if (!classes.isEmpty()) {
                matching.put(entry.getKey(), classes);
            }
        }

        return matching;
    }

    public Map<String, List<ClassMatch>> findSimilarClassNodes(String jarToExclude, ClassNode classNodeToMatch) {
        Map<String, List<ClassMatch>> matching = new LinkedHashMap<>();

        for (Map.Entry<String, List<ClassNode>> entry : getSortedJarEntries()) {
            if (entry.getKey().equals(jarToExclude)) {
                continue;
            }

            List<ClassMatch> matches = classComparator.getClassNodeMatches(entry.getValue(), classNodeToMatch).stream()
                .filter(match -> match.similarityPercent() >= MIN_CLASS_MATCH_PERCENT)
                .limit(MAX_CLASS_MATCHES_PER_JAR)
                .toList();
            if (!matches.isEmpty()) {
                matching.put(entry.getKey(), matches);
            }
        }

        return matching;
    }

    public Map<String, List<ClassNode>> getJarClassesView() {
        return workspace.classesByJar();
    }

    public boolean removeJar(String jarName) {
        decompilerCacheService.invalidateJar(jarName, workspace.classesSnapshot(jarName));
        classMappingService.clearJar(jarName);
        referenceIndexService.removeJar(jarName);
        return workspace.removeJar(jarName);
    }

    public boolean removeClass(String jarName, ClassNode classNode) {
        classMappingService.clearClass(jarName, classNode.name);
        boolean removed = workspace.removeClass(jarName, classNode);
        if (removed) {
            refreshReferenceIndex(jarName);
        }
        return removed;
    }

    public List<ClassNode> getClasses(String jarName) {
        return workspace.classes(jarName);
    }

    public Optional<ClassNode> findClass(ClassRef ref) {
        return workspace.findClass(ref);
    }

    public List<String> getNonStaticFieldDescriptions(ClassNode classNode) {
        return classComparator.getNonStaticFieldDescriptions(classNode);
    }

    public List<String> getNonStaticMethodDescriptions(ClassNode classNode) {
        return classComparator.getNonStaticMethodDescriptions(classNode);
    }

    public String normalizeClassDescriptor(String descriptor) {
        return classComparator.normalizeDescriptor(descriptor);
    }

    public List<ClassMatch> findMappingCandidates(
        String targetJar,
        ClassNode sourceClass,
        int minimumSimilarityPercent,
        int maximumResults
    ) {
        List<ClassNode> targetClasses = workspace.classesSnapshot(targetJar);
        if (targetClasses == null) {
            return List.of();
        }
        return classMappingService.findCandidates(
            targetClasses,
            sourceClass,
            minimumSimilarityPercent,
            maximumResults
        );
    }

    public void acceptClassMapping(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassMatch candidate
    ) {
        classMappingService.accept(sourceJar, targetJar, sourceClass, candidate);
    }

    public void rejectClassMapping(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassNode targetClass
    ) {
        classMappingService.reject(sourceJar, targetJar, sourceClass, targetClass);
    }

    public void clearClassMappingDecisions(String sourceJar, String targetJar, ClassNode sourceClass) {
        classMappingService.clearSourceDecisions(sourceJar, targetJar, sourceClass);
    }

    /**
     * Keep the mapping decisions of a class that was renamed.
     */
    public void renameClassInMappings(String jarName, String oldName, String newName) {
        classMappingService.renameClass(jarName, oldName, newName);
    }

    public Optional<ClassMappingDecision> getAcceptedClassMapping(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass
    ) {
        return classMappingService.getAcceptedMapping(sourceJar, targetJar, sourceClass);
    }

    public boolean isClassMappingRejected(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassNode targetClass
    ) {
        return classMappingService.isRejected(sourceJar, targetJar, sourceClass, targetClass);
    }

    public List<ClassMappingDecision> getAcceptedClassMappings(String sourceJar, String targetJar) {
        return classMappingService.getAcceptedMappings(sourceJar, targetJar);
    }

    public List<ClassMappingDecision> getAllAcceptedClassMappings() {
        return classMappingService.getAllAcceptedMappings();
    }

    public void restoreClassMappings(List<ClassMappingDecision> mappings) {
        classMappingService.clear();
        for (ClassMappingDecision mapping : mappings) {
            requireClass(mapping.sourceJar(), mapping.sourceClassName());
            requireClass(mapping.targetJar(), mapping.targetClassName());
            classMappingService.restore(mapping);
        }
    }

    public List<ReferenceResult> findFieldReads(String jarName, ClassNode owner, FieldNode field) {
        return referenceIndexService.findFieldReads(jarName, owner, field);
    }

    public List<ReferenceResult> findFieldWrites(
        String jarName,
        ClassNode owner,
        FieldNode field
    ) {
        return referenceIndexService.findFieldWrites(jarName, owner, field);
    }

    public List<ReferenceResult> findMethodCallers(String jarName, ClassNode owner, MethodNode method) {
        return referenceIndexService.findMethodCallers(jarName, owner, method);
    }

    public List<ReferenceResult> findMethodCallees(String jarName, ClassNode owner, MethodNode method) {
        return referenceIndexService.findMethodCallees(jarName, owner, method);
    }

    public List<ReferenceResult> findTypeReferences(String jarName, ClassNode targetClass) {
        return referenceIndexService.findTypeReferences(jarName, targetClass);
    }

    /**
     * Rebuild everything derived from a JAR's bytecode after its classes were renamed in place.
     */
    public void refreshAfterRename(String jarName) {
        workspace.classesChanged(jarName);
        decompilerCacheService.invalidateJar(jarName, workspace.classesSnapshot(jarName));
        refreshReferenceIndex(jarName);
    }

    public void refreshReferenceIndex(String jarName) {
        List<ClassNode> classes = workspace.classesSnapshot(jarName);
        if (classes == null) {
            referenceIndexService.removeJar(jarName);
        } else {
            referenceIndexService.indexJar(jarName, classes);
        }
    }

    public List<ClassMappingDecision> autoAcceptUniqueClassMappings(
        String sourceJar,
        String targetJar,
        int minimumSimilarityPercent
    ) {
        List<ClassNode> sourceClasses = workspace.classesSnapshot(sourceJar);
        List<ClassNode> targetClasses = workspace.classesSnapshot(targetJar);
        if (sourceClasses == null || targetClasses == null) {
            return List.of();
        }
        return classMappingService.autoAcceptUniqueMatches(
            sourceJar,
            targetJar,
            sourceClasses,
            targetClasses,
            minimumSimilarityPercent
        );
    }

    public void exportAcceptedClassMappings(File destination, String sourceJar, String targetJar) throws IOException {
        classMappingService.exportAcceptedMappings(destination, sourceJar, targetJar);
    }

    public boolean areMethodsBehaviorallyEquivalent(
        List<ClassNode> classNodes1,
        MethodNode methodNode1,
        List<ClassNode> classNodes2,
        MethodNode methodNode2
    ) {
        return methodComparator.areBehaviorallyEquivalent(classNodes1, methodNode1, classNodes2, methodNode2);
    }

    public boolean isStripAnnotations() {
        return stripAnnotations;
    }

    public void setStripAnnotations(boolean stripAnnotations) {
        this.stripAnnotations = stripAnnotations;
    }

    public byte[] toBytes(ClassNode classNode) {
        return jarExportService.toBytes(classNode);
    }

    public void saveClassToFile(ClassNode classNode, File dest) throws IOException {
        jarExportService.saveClassToFile(classNode, dest);
    }

    public void exportJarGroupToJar(String jarName, File destJar) throws IOException {
        List<ClassNode> list = workspace.classesSnapshot(jarName);
        if (list == null) {
            throw new IOException("Unknown jar group: " + jarName);
        }

        jarExportService.exportJar(destJar, list);
    }

    public boolean overwriteOriginalJar(String jarName) throws IOException {
        File original = workspace.sourceFile(jarName);
        List<ClassNode> list = workspace.classesSnapshot(jarName);

        return jarExportService.overwriteOriginalJar(original, list);
    }

    public File getOriginalJarFile(String jarName) {
        return workspace.sourceFile(jarName);
    }

    public Map<String, File> getOriginalJarFiles() {
        return workspace.sourceFiles();
    }

    public String getDecompiledSource(ClassNode classNode) {
        return decompilerCacheService.getDecompiledSource(classNode);
    }

    public CompletableFuture<DecompileResult> decompileClassAsync(ClassNode classNode) {
        return decompilerCacheService.decompileClassAsync(classNode);
    }

    public List<SearchResult> search(SearchQuery query) {
        return searchService.search(getJarClassesView(), query);
    }

    @Override
    public void close() {
        decompilerCacheService.close();
    }

    private List<Map.Entry<String, List<ClassNode>>> getSortedJarEntries() {
        return workspace.classesByJar().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .toList();
    }

    private ClassNode requireClass(String jarName, String className) {
        if (!workspace.contains(jarName)) {
            throw new IllegalArgumentException("Project references an unloaded JAR: " + jarName);
        }
        return workspace.findClass(new ClassRef(jarName, className))
            .orElseThrow(() -> new IllegalArgumentException(
                "Project references a missing class: " + jarName + " -> " + className
            ));
    }
}

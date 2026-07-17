package org.de.rikr;

import org.de.rikr.behavioral.ClassComparator;
import org.de.rikr.behavioral.MethodComparator;
import org.de.rikr.service.ClassLoadingService;
import org.de.rikr.service.DecompileResult;
import org.de.rikr.service.DecompilerCacheService;
import org.de.rikr.service.JarExportService;
import org.de.rikr.service.LoadedJar;
import org.de.rikr.service.ProjectRepository;
import org.de.rikr.service.SearchQuery;
import org.de.rikr.service.SearchResult;
import org.de.rikr.service.SearchService;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ClassProcessor implements AutoCloseable {
    private static final String LOOSE_CLASS_GROUP = "Classes";

    private final Logger logger;
    private final ProjectRepository projectRepository;
    private final ClassLoadingService classLoadingService;
    private final JarExportService jarExportService;
    private final DecompilerCacheService decompilerCacheService;
    private final SearchService searchService;
    private final ClassComparator classComparator;
    private final MethodComparator methodComparator;
    private boolean stripAnnotations;

    public ClassProcessor(Logger logger) {
        this.logger = logger;
        this.projectRepository = new ProjectRepository();
        this.classLoadingService = new ClassLoadingService();
        this.jarExportService = new JarExportService(logger);
        this.decompilerCacheService = new DecompilerCacheService(logger);
        this.searchService = new SearchService();
        this.classComparator = new ClassComparator();
        this.methodComparator = new MethodComparator();
        this.stripAnnotations = false;
    }

    public void processJarFiles(File[] jarFiles) {
        for (File file : jarFiles) {
            try {
                LoadedJar loadedJar = classLoadingService.readJar(file, stripAnnotations);
                projectRepository.putJarClasses(loadedJar.jarName(), loadedJar.classes(), loadedJar.originalFile());
                logger.log("Loaded " + loadedJar.classes().size() + " classes from " + loadedJar.jarName());
                decompilerCacheService.warmUpJar(file);
            } catch (IOException e) {
                logger.log("Error processing jar file: " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public void processClassFiles(File[] classFiles) {
        for (File file : classFiles) {
            try {
                ClassNode classNode = classLoadingService.readClass(file, stripAnnotations);
                projectRepository.addClass(LOOSE_CLASS_GROUP, classNode);
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
        Map<String, List<ClassNode>> matching = new HashMap<>();

        for (Map.Entry<String, List<ClassNode>> entry : projectRepository.getJarClassesView().entrySet()) {
            if (entry.getKey().equals(jarToExclude)) {
                continue;
            }

            List<ClassNode> classes = classComparator.getMatchingClassNodes(entry.getValue(), classNodeToMatch);
            matching.putIfAbsent(entry.getKey(), classes);
        }

        return matching;
    }

    public Map<String, List<ClassNode>> getJarClassesView() {
        return projectRepository.getJarClassesView();
    }

    public boolean removeJar(String jarName) {
        return projectRepository.removeJar(jarName);
    }

    public boolean removeClass(String jarName, ClassNode classNode) {
        return projectRepository.removeClass(jarName, classNode);
    }

    public List<ClassNode> getClasses(String jarName) {
        return projectRepository.getClasses(jarName);
    }

    public List<String> getNonStaticFieldDescriptions(ClassNode classNode) {
        return classComparator.getNonStaticFieldDescriptions(classNode);
    }

    public List<String> getNonStaticMethodDescriptions(ClassNode classNode) {
        return classComparator.getNonStaticMethodDescriptions(classNode);
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
        List<ClassNode> list = projectRepository.getClasses(jarName);
        if (list == null) {
            throw new IOException("Unknown jar group: " + jarName);
        }

        jarExportService.exportJar(destJar, list);
    }

    public boolean overwriteOriginalJar(String jarName) throws IOException {
        File original = projectRepository.getOriginalJarFile(jarName);
        List<ClassNode> list = projectRepository.getClasses(jarName);

        return jarExportService.overwriteOriginalJar(original, list);
    }

    public File getOriginalJarFile(String jarName) {
        return projectRepository.getOriginalJarFile(jarName);
    }

    public String getDecompiledSourceByClass(String internalName) {
        return decompilerCacheService.getDecompiledSourceByClass(internalName);
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
}

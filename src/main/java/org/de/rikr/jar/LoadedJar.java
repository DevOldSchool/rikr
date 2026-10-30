package org.de.rikr.jar;

import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.List;

public record LoadedJar(String jarName, List<ClassNode> classes, File originalFile) {
}

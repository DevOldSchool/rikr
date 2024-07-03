package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

public class JarMutableTreeNode extends DefaultMutableTreeNode {
    private final String jarName;
    private final List<ClassNode> classes;

    public JarMutableTreeNode(String jarName, List<ClassNode> classes, Object userObject) {
        super(userObject);
        this.jarName = jarName;
        this.classes = classes;
    }

    public String getJarName() {
        return jarName;
    }

    public List<ClassNode> getClasses() {
        return classes;
    }
}

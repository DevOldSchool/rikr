package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

public class ClassNodeMutableTreeNode extends IconMutableTreeNode {
    private final String jarName;
    private final ClassNode classNode;

    public ClassNodeMutableTreeNode(String jarName, ClassNode classNode, Object userObject) {
        super("class", userObject);
        this.jarName = jarName;
        this.classNode = classNode;
    }

    public String getJarName() {
        return jarName;
    }

    public ClassNode getClassNode() {
        return classNode;
    }
}

package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

public class InterfaceNodeMutableTreeNode extends IconMutableTreeNode {
    private final String jarName;
    private final ClassNode classNode;

    public InterfaceNodeMutableTreeNode(String jarName, ClassNode classNode, Object userObject) {
        super("interface", userObject);
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

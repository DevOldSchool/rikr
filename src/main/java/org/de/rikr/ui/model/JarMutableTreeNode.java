package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

import javax.swing.tree.DefaultMutableTreeNode;
import java.text.NumberFormat;
import java.util.List;

public class JarMutableTreeNode extends DefaultMutableTreeNode {
    private final String jarName;
    private final List<ClassNode> classes;
    private boolean dirty;

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

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * The JAR's name, how many classes it has, and a * when it has renames that are not saved.
     */
    @Override
    public String toString() {
        String count = NumberFormat.getIntegerInstance().format(classes.size());
        return jarName + (dirty ? " *" : "") + "   " + count + (classes.size() == 1 ? " class" : " classes");
    }
}

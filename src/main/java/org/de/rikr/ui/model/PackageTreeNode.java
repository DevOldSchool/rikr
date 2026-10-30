package org.de.rikr.ui.model;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * A package in the project tree, holding the classes in it. It is labelled with the dotted package name.
 */
public class PackageTreeNode extends DefaultMutableTreeNode {
    private final String packageName;

    /**
     * @param packageName the internal package name, such as {@code jaggl} or {@code jaclib/ping}
     */
    public PackageTreeNode(String packageName) {
        super(packageName.replace('/', '.'));
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }
}

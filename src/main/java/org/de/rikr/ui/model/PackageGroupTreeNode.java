package org.de.rikr.ui.model;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * The packages of a JAR folded into one node, so that the classes in no package come first. In an obfuscated JAR
 * those are the program's own classes; the packages are mostly libraries bundled with it.
 */
public class PackageGroupTreeNode extends DefaultMutableTreeNode {

    public PackageGroupTreeNode(int packages) {
        super(packages + (packages == 1 ? " package" : " packages"));
    }
}

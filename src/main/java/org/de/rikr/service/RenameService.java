package org.de.rikr.service;

import org.de.rikr.Renamer;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

public class RenameService {
    public RenameResult renameClassReferences(List<ClassNode> classes, String oldName, String newName) {
        return Renamer.updateClassReferences(classes, oldName, newName);
    }

    public RenameResult renameMethodReferences(
        List<ClassNode> classes,
        ClassNode owner,
        String oldName,
        String newName
    ) {
        return Renamer.updateMethodReferences(classes, owner, oldName, newName);
    }

    public RenameResult renameFieldReferences(
        List<ClassNode> classes,
        ClassNode owner,
        String oldName,
        String newName
    ) {
        return Renamer.updateFieldReferences(classes, owner, oldName, newName);
    }
}

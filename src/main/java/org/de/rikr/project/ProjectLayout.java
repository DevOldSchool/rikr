package org.de.rikr.project;

import java.util.Objects;

public record ProjectLayout(
    int windowX,
    int windowY,
    int windowWidth,
    int windowHeight,
    int mainDivider,
    int verticalDivider,
    int contentDivider,
    boolean logVisible,
    boolean simulatorVisible,
    boolean splitJavaVisible,
    String selectedWorkspace
) {
    public ProjectLayout {
        Objects.requireNonNull(selectedWorkspace, "selectedWorkspace");
    }
}

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
    /**
     * The GUI's starting layout, for projects saved without a window, for example by the MCP server. A zero window
     * size keeps the window where it is.
     */
    public static final ProjectLayout DEFAULT =
        new ProjectLayout(0, 0, 0, 0, 300, 600, 700, true, false, false, "PROJECT");

    public ProjectLayout {
        Objects.requireNonNull(selectedWorkspace, "selectedWorkspace");
    }
}

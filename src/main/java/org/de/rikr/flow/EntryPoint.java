package org.de.rikr.flow;

/**
 * A method that something outside the JAR calls to start it running: the JVM, a browser or loader, a thread, or the
 * windowing system.
 *
 * @param reason why the method is thought to be an entry point, for people reading the list
 */
public record EntryPoint(MethodRef method, Kind kind, String reason) {

    /**
     * Kinds in order of how likely they are to be where the program starts.
     */
    public enum Kind {
        /** The {@code main} of the manifest's Main-Class. */
        MAIN_CLASS,
        /** Any other {@code public static void main(String[])}. */
        MAIN,
        /** An applet's lifecycle, called by the browser or a loader: init, then start. */
        APPLET,
        /** A {@code run()} that a thread can start. */
        THREAD,
        /** An event listener method the windowing system calls. */
        LISTENER
    }
}

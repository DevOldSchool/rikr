package org.de.rikr.behavioral;

/**
 * An instruction the simulator can't run, such as the obsolete {@code JSR} and {@code RET}.
 */
public class SimulationException extends RuntimeException {
    public SimulationException(String message) {
        super(message);
    }
}

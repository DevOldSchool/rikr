package org.de.rikr.behavioral;

import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.Type;

/**
 * A value the simulator holds that isn't a plain Java value. Known ints, longs, floats, doubles and strings are
 * held as {@link Integer}, {@link Long}, {@link Float}, {@link Double} and {@link String}, and {@code null} is a
 * real null. Every boolean, byte, char and short is an {@link Integer}, as on the JVM's stack.
 */
public sealed interface SimulatedValue {
    /**
     * A value the simulator can't know, such as a parameter, a field it hasn't written or a call's result.
     * Anything computed from it is unknown too, so the stack keeps its shape and nothing looks known that isn't.
     *
     * @param type   the value's type
     * @param source where it came from, such as "parameter 1", or null when it was computed
     */
    record Unknown(Type type, String source) implements SimulatedValue {
        public Unknown(Type type) {
            this(type, null);
        }

        @Override
        public String toString() {
            return "unknown " + ClassNodeUtil.simpleName(type) + (source == null ? "" : " (" + source + ")");
        }
    }

    /**
     * An object: {@code this} (id 0) or one made by {@code NEW} while simulating. Its fields live in the frame.
     */
    record ObjectRef(int id, Type type) implements SimulatedValue {
        @Override
        public String toString() {
            return id == 0 ? "this" : "new " + ClassNodeUtil.simpleName(type) + " #" + id;
        }
    }

    /**
     * An array made while simulating. Its elements live in the frame.
     *
     * @param length the length, or null when it was unknown
     */
    record ArrayRef(int id, Type type, Integer length) implements SimulatedValue {
        @Override
        public String toString() {
            String element = ClassNodeUtil.simpleName(type.getElementType()) + "[]".repeat(type.getDimensions() - 1);
            return element + "[" + (length == null ? "?" : length) + "] #" + id;
        }
    }

    /**
     * A class constant pushed by {@code LDC}.
     */
    record ClassConstant(Type type) implements SimulatedValue {
        @Override
        public String toString() {
            return ClassNodeUtil.simpleName(type) + ".class";
        }
    }

    /**
     * How many stack words a value takes: two for longs and doubles, one for everything else.
     */
    static int size(Object value) {
        return switch (value) {
            case Long ignored -> 2;
            case Double ignored -> 2;
            case Unknown unknown -> unknown.type().getSize();
            case null, default -> 1;
        };
    }
}

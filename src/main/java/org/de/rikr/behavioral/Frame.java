package org.de.rikr.behavioral;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything one simulated method can see: its operand stack, its local variables, and what it has learned about
 * fields and array elements. Objects and arrays are ids, and their contents are entries in {@link #heap}, so a
 * copy of the maps is a complete snapshot.
 */
final class Frame {
    // Top of the stack last.
    final List<Object> stack;
    final Map<Integer, Object> locals;
    // Field and element values written while simulating, by key; see the key methods.
    final Map<String, Object> heap;
    int nextObjectId;
    // Objects with an id below this existed before the last call, which may have changed their contents.
    int callBarrier;

    Frame() {
        this(new ArrayList<>(), new TreeMap<>(), new HashMap<>(), 1, 0);
    }

    private Frame(List<Object> stack, Map<Integer, Object> locals, Map<String, Object> heap, int nextObjectId,
                  int callBarrier) {
        this.stack = stack;
        this.locals = locals;
        this.heap = heap;
        this.nextObjectId = nextObjectId;
        this.callBarrier = callBarrier;
    }

    Frame copy() {
        return new Frame(new ArrayList<>(stack), new TreeMap<>(locals), new HashMap<>(heap), nextObjectId,
            callBarrier);
    }

    static String fieldKey(int objectId, String name, String descriptor) {
        return "#" + objectId + "." + name + ":" + descriptor;
    }

    static String staticKey(String owner, String name, String descriptor) {
        return owner + "." + name + ":" + descriptor;
    }

    static String elementKey(int arrayId, int index) {
        return "#" + arrayId + "[" + index + "]";
    }

    // Set when an element was written at an unknown index, so no element of the array is known any more.
    static String anyElementKey(int arrayId) {
        return "#" + arrayId + "[?]";
    }
}

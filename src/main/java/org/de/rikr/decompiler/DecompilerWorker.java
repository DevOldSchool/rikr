package org.de.rikr.decompiler;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The decompiler's own JVM, started by {@link WorkerDecompiler}. It reads classes on stdin and writes their Java on
 * stdout, one at a time, until stdin closes.
 * <p>
 * A request starts with its kind. {@link #DECOMPILE} is followed by the class's internal name and its bytes, and is
 * answered with whether there is Java, then the Java, then the messages logged while decompiling. {@link #WARM_UP}
 * is followed by a number of classes, each its name and bytes, and has no answer: the worker decompiles them in the
 * background, so the JIT has compiled FernFlower before the first class someone opens, and stops when the next
 * class to decompile arrives. Strings and byte arrays are written as their length and then their bytes.
 */
public final class DecompilerWorker {
    static final int DECOMPILE = 0;
    static final int WARM_UP = 1;

    private DecompilerWorker() {
    }

    public static void main(String[] args) throws IOException {
        DataInputStream requests = new DataInputStream(new BufferedInputStream(System.in));
        DataOutputStream replies = new DataOutputStream(new BufferedOutputStream(
            new FileOutputStream(FileDescriptor.out)));
        // FernFlower and anything else that prints must not write into the replies.
        System.setOut(System.err);
        AtomicBoolean warmingUp = new AtomicBoolean();
        while (true) {
            String internalName;
            byte[] classBytes;
            try {
                int kind = requests.readByte();
                if (kind == WARM_UP) {
                    warmUp(readClasses(requests), warmingUp);
                    continue;
                }
                if (kind != DECOMPILE) {
                    throw new IOException("Unknown request: " + kind);
                }
                internalName = readString(requests);
                classBytes = readBytes(requests);
            } catch (EOFException closed) {
                return;
            }
            // The class someone asked for goes first; the warm-up gives up at FernFlower's next check.
            warmingUp.set(false);
            List<String> log = new ArrayList<>();
            String source = new JetBrainsDecompilerService(log::add).decompile(internalName, classBytes);
            replies.writeBoolean(source != null);
            if (source != null) {
                writeString(replies, source);
            }
            replies.writeInt(log.size());
            for (String message : log) {
                writeString(replies, message);
            }
            replies.flush();
        }
    }

    private static Map<String, byte[]> readClasses(DataInputStream in) throws IOException {
        int count = in.readInt();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            classes.put(readString(in), readBytes(in));
        }
        return classes;
    }

    private static void warmUp(Map<String, byte[]> classes, AtomicBoolean warmingUp) {
        warmingUp.set(true);
        Thread thread = new Thread(() -> {
            // What it logs is of no interest, and a class it gives up on fails, which is fine.
            JetBrainsDecompilerService service = new JetBrainsDecompilerService(message -> {
            });
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                if (!warmingUp.get()) {
                    return;
                }
                service.decompile(entry.getKey(), entry.getValue(), () -> !warmingUp.get());
            }
            warmingUp.set(false);
        }, "rikr-decompiler-warm-up");
        thread.setDaemon(true);
        thread.start();
    }

    static void writeString(DataOutputStream out, String value) throws IOException {
        writeBytes(out, value.getBytes(StandardCharsets.UTF_8));
    }

    static String readString(DataInputStream in) throws IOException {
        return new String(readBytes(in), StandardCharsets.UTF_8);
    }

    static void writeBytes(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    static byte[] readBytes(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("Negative length: " + length);
        }
        byte[] value = in.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Stream ended " + (length - value.length) + " bytes early.");
        }
        return value;
    }
}

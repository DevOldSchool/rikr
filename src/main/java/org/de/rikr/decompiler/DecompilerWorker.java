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
import java.util.List;

/**
 * The decompiler's own JVM, started by {@link WorkerDecompiler}. It reads classes on stdin and writes their Java on
 * stdout, one at a time, until stdin closes.
 * <p>
 * A request is the class's internal name and its bytes. A reply says whether there is Java, then the Java, then the
 * messages logged while decompiling. Strings and byte arrays are written as their length and then their bytes.
 */
public final class DecompilerWorker {
    private DecompilerWorker() {
    }

    public static void main(String[] args) throws IOException {
        DataInputStream requests = new DataInputStream(new BufferedInputStream(System.in));
        DataOutputStream replies = new DataOutputStream(new BufferedOutputStream(
            new FileOutputStream(FileDescriptor.out)));
        // FernFlower and anything else that prints must not write into the replies.
        System.setOut(System.err);
        while (true) {
            String internalName;
            byte[] classBytes;
            try {
                internalName = readString(requests);
                classBytes = readBytes(requests);
            } catch (EOFException closed) {
                return;
            }
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

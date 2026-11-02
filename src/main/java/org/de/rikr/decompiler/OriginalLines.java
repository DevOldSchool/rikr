package org.de.rikr.decompiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decompiled Java with FernFlower's original line numbers taken out. With {@code __dump_original_lines__} FernFlower
 * ends each Java line made from code that had line numbers with a comment listing them, such as
 * {@code return a;// 12 13}; those are the numbers the bytecode shows as {@code LINENUMBER}.
 *
 * @param source           the Java as it is shown, without the comments
 * @param originalsByLine  for each Java line, counted from 0, the class file's line numbers it came from
 */
public record OriginalLines(String source, Map<Integer, List<Integer>> originalsByLine) {
    // FernFlower writes the comment straight after the code, with no space, which source comments never have.
    private static final Pattern COMMENT = Pattern.compile("(?<=\\S)// (\\d+(?: \\d+)*)$");

    public OriginalLines {
        originalsByLine = Map.copyOf(originalsByLine);
    }

    public static OriginalLines parse(String decompiled) {
        if (decompiled == null) {
            return new OriginalLines(null, Map.of());
        }
        String[] lines = decompiled.split("\n", -1);
        Map<Integer, List<Integer>> originals = new HashMap<>();
        StringBuilder source = new StringBuilder(decompiled.length());
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            boolean carriageReturn = line.endsWith("\r");
            if (carriageReturn) {
                line = line.substring(0, line.length() - 1);
            }
            Matcher comment = COMMENT.matcher(line);
            if (comment.find()) {
                List<Integer> numbers = new ArrayList<>();
                for (String number : comment.group(1).split(" ")) {
                    numbers.add(Integer.parseInt(number));
                }
                originals.put(index, List.copyOf(numbers));
                line = line.substring(0, comment.start());
            }
            source.append(line);
            if (carriageReturn) {
                source.append('\r');
            }
            if (index < lines.length - 1) {
                source.append('\n');
            }
        }
        return new OriginalLines(source.toString(), originals);
    }
}

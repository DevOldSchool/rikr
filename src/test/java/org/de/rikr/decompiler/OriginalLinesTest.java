package org.de.rikr.decompiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OriginalLinesTest {
    @Test
    void takesOutLineCommentsAndKeepsTheirNumbers() {
        OriginalLines parsed = OriginalLines.parse("""
            class A {
               void a() {
                  b = 1;// 11
                  for(int i = 0; i < 2; ++i) {// 12 13
                  }
                  // a comment of the source // 14
                  c(); // 15
               }
            }
            """);

        assertEquals("""
            class A {
               void a() {
                  b = 1;
                  for(int i = 0; i < 2; ++i) {
                  }
                  // a comment of the source // 14
                  c(); // 15
               }
            }
            """, parsed.source());
        assertEquals(Map.of(2, List.of(11), 3, List.of(12, 13)), parsed.originalsByLine());
    }
}

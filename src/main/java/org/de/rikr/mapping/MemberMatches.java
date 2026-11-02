package org.de.rikr.mapping;

import java.util.List;

/**
 * Members of one JAR matched to members of another.
 *
 * @param sourceMethods how many methods the matched source classes declare, or for static methods the source JAR
 * @param sourceFields  how many fields the matched source classes declare, or for static fields the source JAR
 */
public record MemberMatches(List<MemberMatch> methods, List<MemberMatch> fields, int sourceMethods, int sourceFields) {
    public MemberMatches {
        methods = List.copyOf(methods);
        fields = List.copyOf(fields);
    }
}

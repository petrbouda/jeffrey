/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.profile.heapdump.parser;

/**
 * Converts an HPROF class name (JVMS internal notation as it appears in the
 * dump's string pool) into the user-facing fully-qualified name the rest of
 * the codebase expects. Applied once at index time in {@link HprofIndex} so
 * every downstream consumer (Class Histogram, Dominator Tree, Leak Suspects,
 * Path to GC Root, etc.) automatically sees dot-notation names.
 *
 * <p>Examples:
 * <pre>
 *   "java/util/HashMap"            -> "java.util.HashMap"
 *   "java/util/Map$Entry"          -> "java.util.Map$Entry"
 *   "[Ljava/lang/Object;"          -> "java.lang.Object[]"
 *   "[[Ljava/lang/String;"         -> "java.lang.String[][]"
 *   "[I"                           -> "int[]"
 *   "[[B"                          -> "byte[][]"
 * </pre>
 *
 * <p>The frontend (e.g. {@code ProfileHeapDumpHistogram.vue} helpers
 * {@code simpleClassName} / {@code packageName}) splits on the last
 * {@code '.'} to derive simple-name + package, so dot-notation FQNs render
 * correctly with no further work.
 */
public final class ClassNameFormatter {

    private ClassNameFormatter() {
    }

    /**
     * Converts a raw HPROF class name into the user-facing form.
     * {@code null} and empty inputs are returned unchanged.
     */
    public static String userFacing(String hprofName) {
        if (hprofName == null || hprofName.isEmpty()) {
            return hprofName;
        }
        int dims = 0;
        while (dims < hprofName.length() && hprofName.charAt(dims) == '[') {
            dims++;
        }
        String base;
        if (dims == 0) {
            base = hprofName.replace('/', '.');
        } else if (dims >= hprofName.length()) {
            // All brackets — malformed; return as-is.
            return hprofName;
        } else {
            char tag = hprofName.charAt(dims);
            if (tag == 'L' && hprofName.endsWith(";")) {
                base = hprofName.substring(dims + 1, hprofName.length() - 1).replace('/', '.');
            } else {
                base = primitiveName(tag);
                if (base == null) {
                    // Unrecognised tag — leave the original alone rather than mangle it.
                    return hprofName;
                }
            }
        }
        return base + "[]".repeat(dims);
    }

    private static String primitiveName(char tag) {
        return switch (tag) {
            case 'Z' -> "boolean";
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'S' -> "short";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'F' -> "float";
            case 'D' -> "double";
            default -> null;
        };
    }
}

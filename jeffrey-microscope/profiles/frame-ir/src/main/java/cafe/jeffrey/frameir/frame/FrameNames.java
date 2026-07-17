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

package cafe.jeffrey.frameir.frame;

/**
 * Builds display names for frames whose language/tier is unknown (every pprof frame is
 * {@code UNKNOWN}). A {@code '#'} marks the boundary the renderer splits on to pull out the
 * package/module; frames without a meaningful boundary stay plain dotted so they render as-is:
 * <ul>
 *   <li><b>Java class</b> (uppercase simple name) → {@code package.Class#method} — {@code '#'}
 *       separates class from method, package parsed from the dotted left part.</li>
 *   <li><b>C++ in a shared library</b> (the method carries {@code Class::method}) →
 *       {@code module#Class::method} — {@code '#'} separates the module/filename (rendered as the
 *       "package", e.g. {@code libjvm.so} / {@code libjvm.dylib} / {@code jvm.dll}) from the
 *       {@code Class::method}. Platform-agnostic: the module is whatever the profiler recorded.</li>
 *   <li><b>Native libraries / Go-style</b> ({@code libc.so.6} + {@code clone3},
 *       {@code main.setFunctions} + {@code func7505}) → plain dotted, rendered flat.</li>
 *   <li>A blank class (a bare native symbol) yields the method alone.</li>
 * </ul>
 */
public final class FrameNames {

    /** C++/native method qualifier ({@code Class::method}). */
    private static final String CPP_METHOD_SEPARATOR = "::";
    /** Boundary the renderer splits on to pull out the package/module (matches JFR Java frames). */
    private static final String NAME_DELIMITER = "#";
    private static final String DOTTED_DELIMITER = ".";
    private static final char PACKAGE_SEPARATOR = '.';
    private static final char SLASH_SEPARATOR = '/';

    private FrameNames() {
    }

    /**
     * Joins an {@code UNKNOWN}-typed frame's class and method into a display name.
     *
     * @param className  the class/module, may be blank for a bare native symbol
     * @param methodName the method (may itself contain {@code ::} for C++ frames)
     * @return the display name with the delimiter chosen per the class rules above
     */
    public static String joinUnknown(String className, String methodName) {
        if (className == null || className.isBlank()) {
            return methodName;
        }
        // '#' marks a boundary the renderer can use: for a Java class it separates class from method;
        // for a C++ frame it separates the shared-library module (shown as the package) from the
        // Class::method. Native libraries / Go-style names have neither, so keep them plain dotted.
        boolean marksBoundary = methodName.contains(CPP_METHOD_SEPARATOR) || isJavaClass(className);
        String delimiter = marksBoundary ? NAME_DELIMITER : DOTTED_DELIMITER;
        return className + delimiter + methodName;
    }

    /**
     * A Java fully-qualified class name has an uppercase <em>simple</em> name (lowercase package
     * segments, capitalised class). Native library names ({@code libc.so.6}, {@code libjvm.so}) and
     * Go-style names ({@code main.setFunctions}) do not.
     */
    private static boolean isJavaClass(String className) {
        int lastSeparator = Math.max(
                className.lastIndexOf(PACKAGE_SEPARATOR), className.lastIndexOf(SLASH_SEPARATOR));
        String simpleName = className.substring(lastSeparator + 1);
        return !simpleName.isEmpty() && Character.isUpperCase(simpleName.charAt(0));
    }
}

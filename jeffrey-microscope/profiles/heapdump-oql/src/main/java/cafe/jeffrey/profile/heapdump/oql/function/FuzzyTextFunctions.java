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
package cafe.jeffrey.profile.heapdump.oql.function;

/**
 * Java-side implementations of the fuzzy-text functions exposed by OQL.
 *
 * <p>The classic dynamic-programming Levenshtein with two rolling rows; the
 * Jaro–Winkler similarity follows the original 1989/1990 formulation. Both
 * run on decoded {@link String} content from a Plan C row, so per-call cost
 * is proportional to the candidate string length.
 */
public final class FuzzyTextFunctions {

    private static final double JARO_WINKLER_PREFIX_WEIGHT = 0.1;
    private static final int JARO_WINKLER_MAX_PREFIX = 4;

    private FuzzyTextFunctions() {
    }

    public static Integer levenshtein(Object a, Object b) {
        if (a == null || b == null) return null;
        String s1 = a.toString();
        String s2 = b.toString();
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0) return len2;
        if (len2 == 0) return len1;
        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[len2];
    }

    public static Double jaroWinklerSimilarity(Object a, Object b) {
        if (a == null || b == null) return null;
        String s1 = a.toString();
        String s2 = b.toString();
        double jaro = jaro(s1, s2);
        int prefix = commonPrefix(s1, s2);
        return jaro + prefix * JARO_WINKLER_PREFIX_WEIGHT * (1.0 - jaro);
    }

    private static double jaro(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 && len2 == 0) return 1.0;
        if (len1 == 0 || len2 == 0) return 0.0;
        int matchWindow = Math.max(0, Math.max(len1, len2) / 2 - 1);
        boolean[] s1Matched = new boolean[len1];
        boolean[] s2Matched = new boolean[len2];
        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchWindow);
            int end = Math.min(i + matchWindow + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matched[j]) continue;
                if (s1.charAt(i) != s2.charAt(j)) continue;
                s1Matched[i] = true;
                s2Matched[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) return 0.0;
        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matched[i]) continue;
            while (!s2Matched[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }
        double m = matches;
        return (m / len1 + m / len2 + (m - transpositions / 2.0) / m) / 3.0;
    }

    private static int commonPrefix(String s1, String s2) {
        int max = Math.min(JARO_WINKLER_MAX_PREFIX, Math.min(s1.length(), s2.length()));
        for (int i = 0; i < max; i++) {
            if (s1.charAt(i) != s2.charAt(i)) return i;
        }
        return max;
    }
}

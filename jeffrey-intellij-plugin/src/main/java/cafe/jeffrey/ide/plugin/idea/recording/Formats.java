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

package cafe.jeffrey.ide.plugin.idea.recording;

import java.util.Locale;

/**
 * Number formatting for the panel. Separate from the Swing code so the rounding — the part that is
 * easy to get subtly wrong and invisible in a screenshot — can be tested without an IDE fixture.
 *
 * <p>{@link Locale#ROOT} throughout, matching Microscope's own output: a developer comparing the
 * panel with the web UI side by side should not find one saying {@code 8.1 MB} and the other
 * {@code 8,1 MB} because of a machine's language setting.
 */
public final class Formats {

    private static final long KILO = 1024L;
    private static final String[] BYTE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    private static final long THOUSAND = 1_000L;
    private static final long MILLION = 1_000_000L;
    private static final long BILLION = 1_000_000_000L;

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long MINUTES_PER_HOUR = 60L;

    private Formats() {
    }

    public static String bytes(long value) {
        if (value < KILO) {
            return value + " " + BYTE_UNITS[0];
        }
        double scaled = value;
        int unit = 0;
        while (scaled >= KILO && unit < BYTE_UNITS.length - 1) {
            scaled /= KILO;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", scaled, BYTE_UNITS[unit]);
    }

    /** Sample counts, which run to millions and are read at a glance rather than added up. */
    public static String count(long value) {
        if (value < THOUSAND) {
            return Long.toString(value);
        }
        if (value < MILLION) {
            return String.format(Locale.ROOT, "%.1f K", value / (double) THOUSAND);
        }
        if (value < BILLION) {
            return String.format(Locale.ROOT, "%.2f M", value / (double) MILLION);
        }
        return String.format(Locale.ROOT, "%.2f B", value / (double) BILLION);
    }

    /**
     * The recording window. Sub-minute recordings are the common case and read best in seconds with
     * one decimal; anything longer drops to whole units, because nobody needs tenths of an hour.
     */
    public static String duration(long millis) {
        if (millis <= 0) {
            return "unknown";
        }
        long seconds = millis / MILLIS_PER_SECOND;
        if (seconds < SECONDS_PER_MINUTE) {
            return String.format(Locale.ROOT, "%.1f s", millis / (double) MILLIS_PER_SECOND);
        }
        long minutes = seconds / SECONDS_PER_MINUTE;
        if (minutes < MINUTES_PER_HOUR) {
            return minutes + " m " + (seconds % SECONDS_PER_MINUTE) + " s";
        }
        return (minutes / MINUTES_PER_HOUR) + " h " + (minutes % MINUTES_PER_HOUR) + " m";
    }

    /**
     * Sample loss, which is the figure that decides whether the rest can be trusted. A recording that
     * reports no sampler health at all says so rather than showing a reassuring {@code 0%}.
     */
    public static String lossRatio(double ratio) {
        if (ratio < 0) {
            return "not reported";
        }
        if (ratio == 0) {
            return "0%";
        }
        if (ratio < 0.001) {
            return "<0.1%";
        }
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100);
    }
}

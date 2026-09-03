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

package cafe.jeffrey.shared.common.config;

import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Value domain of a setting, used to reject malformed values on the write path.
 * <p>
 * Settings used to be bound once at startup through {@code @Value}, so a malformed value failed the
 * boot with a type-conversion error. Now that values are read lazily and can change at runtime, that
 * safety net is gone: a bad value would surface as a failure on every request that reads it. Each
 * constant therefore carries the predicate that guards its own domain, and
 * {@code SettingsManager} rejects a value before it ever reaches the database.
 */
public enum SettingType {

    /** Any value, including the empty string (used for unset secrets). */
    STRING(value -> true),

    /** A whole number greater than zero — token budgets, timeouts. */
    POSITIVE_INT(value -> parseInt(value) instanceof Integer parsed && parsed > 0),

    /**
     * A whole number of zero or more, where zero carries a meaning of its own rather than being an
     * absent value — a concurrency ceiling reads it as "no ceiling".
     */
    NON_NEGATIVE_INT(value -> parseInt(value) instanceof Integer parsed && parsed >= 0),

    /**
     * A percentage in the exclusive range {@code (0, 100)}. Matches the invariant enforced by
     * {@code AiExportConfig}, which is constructed per flamegraph request.
     */
    PERCENTAGE(value -> parseDouble(value) instanceof Double parsed && parsed > 0.0 && parsed < 100.0),

    /** A logging level name understood by the logging system, case-insensitive. */
    LOG_LEVEL(SettingType::isLogLevel),

    /** One of the supported AI providers, or {@code none} to disable AI entirely. */
    AI_PROVIDER(SettingType::isAiProvider),

    /** Flamegraph frame label layout. */
    FRAME_TEXT_MODE(SettingType::isFrameTextMode),

    /** A feature toggle: literally {@code true} or {@code false}, case-insensitive. */
    BOOLEAN(SettingType::isBoolean);

    private static final Set<String> LOG_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF");
    private static final Set<String> AI_PROVIDERS = Set.of("none", "claude", "chatgpt", "ollama", "claude-code");
    private static final Set<String> FRAME_TEXT_MODES = Set.of("single-line", "two-line");
    private static final Set<String> BOOLEANS = Set.of("true", "false");

    private final Predicate<String> validator;

    SettingType(Predicate<String> validator) {
        this.validator = validator;
    }

    /**
     * @param value the raw textual value, may be {@code null}
     * @return true when the value belongs to this type's domain
     */
    public boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        return validator.test(value);
    }

    private static boolean isLogLevel(String value) {
        return LOG_LEVELS.contains(upperCase(value));
    }

    private static boolean isAiProvider(String value) {
        return AI_PROVIDERS.contains(lowerCase(value));
    }

    private static boolean isFrameTextMode(String value) {
        return FRAME_TEXT_MODES.contains(lowerCase(value));
    }

    private static boolean isBoolean(String value) {
        return BOOLEANS.contains(lowerCase(value));
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String upperCase(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String lowerCase(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

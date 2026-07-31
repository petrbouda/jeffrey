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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsStoreTest {

    private static final String PROVIDER = "jeffrey.microscope.ai.provider";
    private static final String MAX_TOKENS = "jeffrey.microscope.ai.max-tokens";
    private static final String THRESHOLD = "jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct";

    private static final Map<String, String> DEFAULTS = Map.of(
            PROVIDER, "none",
            MAX_TOKENS, "128000",
            THRESHOLD, "0.05");

    private static SettingsStore store() {
        return new SettingsStore(DEFAULTS, Map.of());
    }

    @Nested
    class Construction {

        @Test
        void defaultsAreVisibleWithoutOverrides() {
            assertEquals("none", store().get(PROVIDER));
        }

        @Test
        void initialValuesOverrideDefaults() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of(PROVIDER, "claude"));
            assertEquals("claude", store.get(PROVIDER));
        }

        @Test
        void unknownStoredSettingIsIgnored() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of("jeffrey.removed.setting", "x"));
            assertFalse(store.contains("jeffrey.removed.setting"));
        }

        @Test
        void nullInitialValueBecomesEmptyString() {
            Map<String, String> initial = new HashMap<>();
            initial.put(PROVIDER, null);
            assertEquals("", new SettingsStore(DEFAULTS, initial).get(PROVIDER));
        }
    }

    @Nested
    class Put {

        @Test
        void reportsChangeWhenValueDiffers() {
            assertTrue(store().put(PROVIDER, "claude"));
        }

        @Test
        void reportsNoChangeWhenValueIsIdentical() {
            assertFalse(store().put(PROVIDER, "none"));
        }

        @Test
        void nullBecomesEmptyString() {
            SettingsStore store = store();
            store.put(PROVIDER, null);
            assertEquals("", store.get(PROVIDER));
        }

        @Test
        void unknownSettingIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> store().put("jeffrey.unknown", "x"));
        }

        @Test
        void valueIsVisibleImmediately() {
            SettingsStore store = store();
            store.put(PROVIDER, "ollama");
            assertEquals("ollama", store.get(PROVIDER));
        }
    }

    @Nested
    class TypedGetters {

        @Test
        void readsStoredInt() {
            assertEquals(128000, store().getInt(MAX_TOKENS, 42));
        }

        @Test
        void readsStoredDouble() {
            assertEquals(0.05, store().getDouble(THRESHOLD, 1.0));
        }

        @Test
        void readsUpdatedValue() {
            SettingsStore store = store();
            store.put(MAX_TOKENS, "4096");
            assertEquals(4096, store.getInt(MAX_TOKENS, 42));
        }

        @Test
        void malformedValueFallsBackToDeclaredDefault() {
            SettingsStore store = store();
            store.put(MAX_TOKENS, "not-a-number");
            assertEquals(128000, store.getInt(MAX_TOKENS, 42));
        }

        @Test
        void blankValueFallsBackToDeclaredDefault() {
            SettingsStore store = store();
            store.put(THRESHOLD, "   ");
            assertEquals(0.05, store.getDouble(THRESHOLD, 9.9));
        }

        @Test
        void malformedValueAndMalformedDefaultFallBackToCaller() {
            SettingsStore store = new SettingsStore(Map.of(MAX_TOKENS, "also-bad"), Map.of());
            store.put(MAX_TOKENS, "not-a-number");
            assertEquals(42, store.getInt(MAX_TOKENS, 42));
        }

        @Test
        void unknownNameFallsBackToCaller() {
            assertEquals(7, store().getInt("jeffrey.unknown", 7));
        }

        @Test
        void getStringFallsBackForUnknownName() {
            assertEquals("fallback", store().getString("jeffrey.unknown", "fallback"));
        }

        @Test
        void getStringKeepsEmptyValueBecauseUnsetSecretsAreMeaningful() {
            SettingsStore store = new SettingsStore(Map.of("api-key", ""), Map.of());
            assertEquals("", store.getString("api-key", "fallback"));
        }
    }

    @Nested
    class Names {

        @Test
        void exposesEveryDeclaredSetting() {
            assertEquals(Set.of(PROVIDER, MAX_TOKENS, THRESHOLD), store().names());
        }

        @Test
        void keySetDoesNotGrowAfterUpdates() {
            SettingsStore store = store();
            store.put(PROVIDER, "claude");
            assertEquals(3, store.names().size());
        }

        @Test
        void unknownNameIsNotContained() {
            assertFalse(store().contains("jeffrey.unknown"));
        }

        @Test
        void unknownNameReadsAsNull() {
            assertNull(store().get("jeffrey.unknown"));
        }
    }
}

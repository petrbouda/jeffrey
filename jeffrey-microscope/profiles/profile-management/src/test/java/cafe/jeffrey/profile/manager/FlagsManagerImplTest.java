/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.provider.profile.api.JvmFlagDetail;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlagsManagerImpl")
class FlagsManagerImplTest {

    @Mock
    private ProfileEventRepository eventRepository;

    @Mock
    private JvmFlagDescriptionProvider descriptionProvider;

    @Nested
    @DisplayName("Flag grouping by origin")
    class FlagGroupingByOrigin {

        @Test
        @DisplayName("should group flags by origin in predefined order")
        void shouldGroupFlagsByOriginInPredefinedOrder() {
            List<JvmFlagDetail> flags = List.of(
                    new JvmFlagDetail("MaxHeapSize", "4294967296", "uint64_t", "Command line", List.of(), true, null, List.of()),
                    new JvmFlagDetail("UseCompressedOops", "true", "bool", "Default", List.of(), false, null, List.of()),
                    new JvmFlagDetail("CICompilerCount", "4", "intx", "Ergonomic", List.of(), false, null, List.of()),
                    new JvmFlagDetail("InitialHeapSize", "268435456", "uint64_t", "Command line", List.of(), true, null, List.of())
            );

            when(eventRepository.getAllFlags()).thenReturn(flags);
            when(descriptionProvider.getDescription(anyString())).thenReturn(null);

            FlagsManagerImpl manager = new FlagsManagerImpl(eventRepository, descriptionProvider);
            FlagsData result = manager.getAllFlags();

            Map<String, List<JvmFlagDetail>> flagsByOrigin = result.flagsByOrigin();

            List<String> keyOrder = new ArrayList<>(flagsByOrigin.keySet());
            assertEquals(3, keyOrder.size());
            assertEquals("Command line", keyOrder.get(0));
            assertEquals("Ergonomic", keyOrder.get(1));
            assertEquals("Default", keyOrder.get(2));

            assertEquals(2, flagsByOrigin.get("Command line").size());
        }
    }

    @Nested
    @DisplayName("Description enrichment")
    class DescriptionEnrichment {

        @Test
        @DisplayName("should enrich flags with descriptions from provider")
        void shouldEnrichFlagsWithDescriptionsFromProvider() {
            List<JvmFlagDetail> flags = List.of(
                    new JvmFlagDetail("FlagA", "100", "intx", "Default", List.of(), false, null, List.of()),
                    new JvmFlagDetail("FlagB", "true", "bool", "Default", List.of(), false, null, List.of())
            );

            when(eventRepository.getAllFlags()).thenReturn(flags);
            when(descriptionProvider.getDescription("FlagA")).thenReturn("Description A");
            when(descriptionProvider.getDescription("FlagB")).thenReturn(null);

            FlagsManagerImpl manager = new FlagsManagerImpl(eventRepository, descriptionProvider);
            FlagsData result = manager.getAllFlags();

            List<JvmFlagDetail> defaultFlags = result.flagsByOrigin().get("Default");
            assertNotNull(defaultFlags);
            assertEquals(2, defaultFlags.size());

            JvmFlagDetail flagA = defaultFlags.stream()
                    .filter(f -> "FlagA".equals(f.name()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("Description A", flagA.description());

            JvmFlagDetail flagB = defaultFlags.stream()
                    .filter(f -> "FlagB".equals(f.name()))
                    .findFirst()
                    .orElseThrow();
            assertNull(flagB.description());
        }
    }

    @Nested
    @DisplayName("Changed flag counting")
    class ChangedFlagCounting {

        @Test
        @DisplayName("should count changed and total flags correctly")
        void shouldCountChangedAndTotalFlagsCorrectly() {
            List<JvmFlagDetail> flags = List.of(
                    new JvmFlagDetail("MaxHeapSize", "4294967296", "uint64_t", "Command line", List.of("2147483648"), true, null, List.of()),
                    new JvmFlagDetail("UseG1GC", "true", "bool", "Ergonomic", List.of(), false, null, List.of()),
                    new JvmFlagDetail("CICompilerCount", "8", "intx", "Management", List.of("4"), true, null, List.of())
            );

            when(eventRepository.getAllFlags()).thenReturn(flags);
            when(descriptionProvider.getDescription(anyString())).thenReturn(null);

            FlagsManagerImpl manager = new FlagsManagerImpl(eventRepository, descriptionProvider);
            FlagsData result = manager.getAllFlags();

            assertEquals(3, result.totalFlags());
            assertEquals(2, result.changedFlags());
        }
    }

    @Nested
    @DisplayName("Empty flags")
    class EmptyFlags {

        @Test
        @DisplayName("should handle empty flag list")
        void shouldHandleEmptyFlagList() {
            when(eventRepository.getAllFlags()).thenReturn(List.of());

            FlagsManagerImpl manager = new FlagsManagerImpl(eventRepository, descriptionProvider);
            FlagsData result = manager.getAllFlags();

            assertEquals(0, result.totalFlags());
            assertEquals(0, result.changedFlags());
            assertTrue(result.flagsByOrigin().isEmpty());
        }
    }
}

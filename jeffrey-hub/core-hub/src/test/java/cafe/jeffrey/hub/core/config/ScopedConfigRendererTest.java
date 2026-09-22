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


package cafe.jeffrey.hub.core.config;

import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.shared.common.config.ConfigType;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedConfigRendererTest {

    private static final Instant NOW = Instant.parse("2025-01-01T12:00:00Z");

    private static ScopedConfigEntry entry(String value) {
        return new ScopedConfigEntry(ScopedConfigKey.global(), ConfigType.ASPROF_SETTINGS, value, NOW);
    }

    @Test
    void aScopeWithNothingRendersNothing() {
        assertEquals("", ScopedConfigRenderer.render(List.of()));
    }

    @Test
    void rendersTheValueUnderTheTypesKey() {
        String rendered = ScopedConfigRenderer.render(List.of(entry("-agentpath:/opt/lib.so=start,cpu")));

        assertEquals("-agentpath:/opt/lib.so=start,cpu",
                ConfigFactory.parseString(rendered).getString("asprof-settings"));
    }

    /**
     * The digest of these bytes is what tells a session whether it is still current, so the same
     * values must always render to the same bytes or every tick would look like a change.
     */
    @Test
    void theSameValuesRenderToTheSameBytes() {
        assertEquals(
                ScopedConfigRenderer.render(List.of(entry("start,cpu"))),
                ScopedConfigRenderer.render(List.of(entry("start,cpu"))));
    }

    @Test
    void quotesAValueThatWouldOtherwiseBreakTheSyntax() {
        String awkward = "-agentpath:/opt/lib.so=start,file=\"quoted\",tag=a:b";

        String rendered = ScopedConfigRenderer.render(List.of(entry(awkward)));

        assertEquals(awkward, ConfigFactory.parseString(rendered).getString("asprof-settings"));
    }

    @Test
    void saysWhoWroteTheFile() {
        assertTrue(ScopedConfigRenderer.render(List.of(entry("start"))).startsWith("#"),
                "a file on a shared volume should say where it came from");
    }

    @Test
    void readsBackTheValuesItRendered() {
        String rendered = ScopedConfigRenderer.render(List.of(entry("start,cpu")));

        Map<ConfigType, String> parsed = ScopedConfigRenderer.parse(ConfigFactory.parseString(rendered));

        assertEquals(Map.of(ConfigType.ASPROF_SETTINGS, "start,cpu"), parsed);
    }

    @Test
    void readsNothingFromAFileWithNoKnownKeys() {
        Map<ConfigType, String> parsed =
                ScopedConfigRenderer.parse(ConfigFactory.parseString("something-else = \"value\""));

        assertTrue(parsed.isEmpty());
    }
}

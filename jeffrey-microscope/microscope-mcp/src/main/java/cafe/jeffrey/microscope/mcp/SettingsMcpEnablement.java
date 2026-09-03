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

package cafe.jeffrey.microscope.mcp;

import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

/**
 * The opt-in switch of the full Microscope: {@code jeffrey.microscope.mcp.enabled}, off by default,
 * flipped from the Settings page and read per request so the change needs no restart.
 */
public final class SettingsMcpEnablement implements McpEnablement {

    private static final boolean DISABLED_BY_DEFAULT = false;

    private final SettingsStore settingsStore;

    public SettingsMcpEnablement(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    @Override
    public boolean enabled() {
        return settingsStore.getBoolean(MicroscopeSettingKeys.MCP_ENABLED, DISABLED_BY_DEFAULT);
    }
}

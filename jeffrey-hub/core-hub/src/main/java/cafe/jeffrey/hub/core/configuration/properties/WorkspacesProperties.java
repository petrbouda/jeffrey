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
package cafe.jeffrey.hub.core.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code jeffrey.hub.workspaces.*}: how the hub treats a workspace directory it finds on the
 * volume with no workspace row behind it. Off by default — a directory is created by the
 * reconciler only when an operator has said that any announced workspace is welcome.
 */
@ConfigurationProperties("jeffrey.hub.workspaces")
public class WorkspacesProperties {

    private boolean autoCreate = false;

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }
}

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

/**
 * The scopes hub-published configuration can have, in merge order: a later scope overrides an
 * earlier one. The order is the directory tree on the shared volume — a file in a project's folder
 * beats the workspace's, which beats the one in the workspaces root — and it is the only place the
 * precedence is defined. Neither side resolves anything; both walk the tree in this order.
 */
public enum ConfigScope {

    /** {@code <workspaces>/.config/jeffrey.conf} — every JVM under the hub's workspaces root */
    GLOBAL,

    /** {@code <workspace>/.config/jeffrey.conf} — every project of one workspace */
    WORKSPACE,

    /** {@code <workspace>/<project>/.config/jeffrey.conf} — one project */
    PROJECT
}

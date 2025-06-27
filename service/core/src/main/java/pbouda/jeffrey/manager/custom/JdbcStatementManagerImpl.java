/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.manager.custom;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;

import java.util.Map;

public class JdbcStatementManagerImpl implements JdbcStatementManager {

    private static final Map<Type, String> POOL_EVENT_NAMES = Map.of(
            Type.POOLED_JDBC_CONNECTION_ACQUIRED, "Connection Acquired",
            Type.POOLED_JDBC_CONNECTION_BORROWED, "Connection Borrowed",
            Type.POOLED_JDBC_CONNECTION_CREATED, "Connection Created");

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;

    public JdbcStatementManagerImpl(ProfileInfo profileInfo, ProfileEventRepository eventRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
    }
}

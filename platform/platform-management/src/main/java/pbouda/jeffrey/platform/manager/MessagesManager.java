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

package pbouda.jeffrey.platform.manager;

import pbouda.jeffrey.platform.manager.jfr.model.ImportantMessage;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.time.TimeRange;

import java.util.List;
import java.util.function.Function;

/**
 * Manager for retrieving ImportantMessage events from JFR platformRepositories.
 * Parses messages from all session streaming-repo directories in a project.
 */
public interface MessagesManager {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, MessagesManager> {
    }

    /**
     * Retrieves ImportantMessage events within a time interval from all sessions.
     *
     * @param timeRange time range for filtering events
     * @return list of messages within the interval sorted by timestamp (newest first)
     */
    List<ImportantMessage> getMessages(TimeRange timeRange);

    /**
     * Retrieves only alert messages (isAlert=true) from all sessions.
     *
     * @param timeRange time range for filtering events
     * @return list of alert messages within the interval sorted by timestamp (newest first)
     */
    List<ImportantMessage> getAlerts(TimeRange timeRange);
}

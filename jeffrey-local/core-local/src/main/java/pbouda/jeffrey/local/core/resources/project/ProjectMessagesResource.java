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

package pbouda.jeffrey.local.core.resources.project;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import pbouda.jeffrey.local.core.manager.MessagesManager;
import pbouda.jeffrey.local.core.resources.response.ImportantMessageResponse;
import pbouda.jeffrey.shared.common.model.time.AbsoluteTimeRange;

import java.util.List;

/**
 * REST resource for retrieving ImportantMessage events from JFR platformRepositories.
 */
public class ProjectMessagesResource {

    private final MessagesManager messagesManager;

    public ProjectMessagesResource(MessagesManager messagesManager) {
        this.messagesManager = messagesManager;
    }

    /**
     * Retrieves ImportantMessage events within an optional time interval.
     *
     * @param startTimeMillis start of time interval in epoch milliseconds (optional)
     * @param endTimeMillis   end of time interval in epoch milliseconds (optional)
     * @return list of ImportantMessage events sorted by timestamp (newest first)
     */
    @GET
    public List<ImportantMessageResponse> getMessages(
            @QueryParam("start") Long startTimeMillis,
            @QueryParam("end") Long endTimeMillis) {

        return messagesManager.getMessages(AbsoluteTimeRange.ofEpochMillis(startTimeMillis, endTimeMillis)).stream()
                .map(ImportantMessageResponse::from)
                .toList();
    }

    /**
     * Retrieves only alert messages (isAlert=true) within an optional time interval.
     *
     * @param startTimeMillis start of time interval in epoch milliseconds (optional)
     * @param endTimeMillis   end of time interval in epoch milliseconds (optional)
     * @return list of alert ImportantMessage events sorted by timestamp (newest first)
     */
    @GET
    @Path("/alerts")
    public List<ImportantMessageResponse> getAlerts(
            @QueryParam("start") Long startTimeMillis,
            @QueryParam("end") Long endTimeMillis) {

        return messagesManager.getAlerts(AbsoluteTimeRange.ofEpochMillis(startTimeMillis, endTimeMillis)).stream()
                .map(ImportantMessageResponse::from)
                .toList();
    }
}

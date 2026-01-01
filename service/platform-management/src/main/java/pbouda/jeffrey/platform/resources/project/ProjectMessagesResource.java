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

package pbouda.jeffrey.platform.resources.project;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import pbouda.jeffrey.platform.manager.MessagesManager;
import pbouda.jeffrey.platform.resources.response.ImportantMessageResponse;
import pbouda.jeffrey.shared.model.time.AbsoluteTimeRange;

import java.time.Instant;
import java.util.List;

/**
 * REST resource for retrieving ImportantMessage events from JFR repositories.
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

        AbsoluteTimeRange timeRange = toTimeRange(startTimeMillis, endTimeMillis);
        return messagesManager.getMessages(timeRange).stream()
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

        AbsoluteTimeRange timeRange = toTimeRange(startTimeMillis, endTimeMillis);
        return messagesManager.getAlerts(timeRange).stream()
                .map(ImportantMessageResponse::from)
                .toList();
    }

    private static AbsoluteTimeRange toTimeRange(Long startTimeMillis, Long endTimeMillis) {
        Instant start = startTimeMillis != null ? Instant.ofEpochMilli(startTimeMillis) : Instant.MIN;
        Instant end = endTimeMillis != null ? Instant.ofEpochMilli(endTimeMillis) : Instant.MAX;
        return new AbsoluteTimeRange(start, end);
    }
}

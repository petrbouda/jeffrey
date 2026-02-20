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

package pbouda.jeffrey.platform.resources.pub;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import pbouda.jeffrey.platform.manager.MessagesManager;
import pbouda.jeffrey.platform.resources.response.ImportantMessageResponse;
import pbouda.jeffrey.shared.common.model.time.AbsoluteTimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Public REST endpoint for ImportantMessage events.
 * This enables remote workspaces to fetch messages and alerts.
 */
public class ProjectMessagesPublicResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectMessagesPublicResource.class);

    private final MessagesManager messagesManager;

    public ProjectMessagesPublicResource(MessagesManager messagesManager) {
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

        LOG.debug("Fetching project messages: start={} end={}", startTimeMillis, endTimeMillis);
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

        LOG.debug("Fetching project alerts: start={} end={}", startTimeMillis, endTimeMillis);
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

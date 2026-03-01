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

package pbouda.jeffrey.platform.streaming;

import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.provider.platform.repository.AlertRepository;
import pbouda.jeffrey.platform.jfr.MessageCategory;
import pbouda.jeffrey.shared.common.model.EventTypeName;
import pbouda.jeffrey.shared.common.model.ImportantMessage;
import pbouda.jeffrey.shared.common.model.Severity;

import static pbouda.jeffrey.platform.manager.jfr.JfrUtils.parseString;

/**
 * Handles {@code jeffrey.Alert} events from a JFR streaming repository.
 * Persists each event to the database via {@link AlertRepository}.
 * Uses ON CONFLICT DO NOTHING for replay-safe idempotent inserts.
 */
public class AlertStreamingHandler implements JfrStreamingHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AlertStreamingHandler.class);

    private final String sessionId;
    private final AlertRepository alertRepository;

    public AlertStreamingHandler(String sessionId, AlertRepository alertRepository) {
        this.sessionId = sessionId;
        this.alertRepository = alertRepository;
    }

    @Override
    public String eventType() {
        return EventTypeName.ALERT;
    }

    @Override
    public void onEvent(RecordedEvent event) {
        String category = parseString(event, "category");
        String resolvedSessionId = MessageCategory.SESSION.name().equals(category) ? sessionId : null;

        ImportantMessage message = new ImportantMessage(
                parseString(event, "type"),
                parseString(event, "title"),
                parseString(event, "message"),
                Severity.fromString(parseString(event, "severity", Severity.MEDIUM.name())),
                category,
                parseString(event, "source"),
                true,
                resolvedSessionId,
                event.getStartTime()
        );

        alertRepository.insert(message);

        LOG.debug("Persisted Alert: sessionId={} type={} title={}",
                sessionId, message.type(), message.title());
    }
}

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

package pbouda.jeffrey.platform.manager.jfr;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.platform.manager.jfr.model.ImportantMessage;
import pbouda.jeffrey.platform.manager.jfr.model.Severity;
import pbouda.jeffrey.repository.parser.EventProcessor;
import pbouda.jeffrey.repository.parser.ProcessableEvents;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.shared.model.repository.RecordingSession;

import java.util.ArrayList;
import java.util.List;

import static pbouda.jeffrey.platform.manager.jfr.JfrUtils.parseBoolean;
import static pbouda.jeffrey.platform.manager.jfr.JfrUtils.parseString;

/**
 * EventProcessor that collects ImportantMessage events from a JFR repository.
 * Each instance should be used for a single repository/session.
 */
public class ImportantMessageProcessor implements EventProcessor<List<ImportantMessage>> {

    private final RecordingSession session;
    private final List<ImportantMessage> messages = new ArrayList<>();

    /**
     * Creates a processor for ImportantMessage events.
     *
     * @param session   the session ID to associate with parsed messages
     */
    public ImportantMessageProcessor(RecordingSession session) {
        this.session = session;
    }

    @Override
    public ProcessableEvents processableEvents() {
        return ProcessableEvents.of(Type.IMPORTANT_MESSAGE);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        ImportantMessage message = new ImportantMessage(
                parseString(event, "type"),
                parseString(event, "title"),
                parseString(event, "message"),
                Severity.fromString(parseString(event, "severity", Severity.MEDIUM.name())),
                parseString(event, "category"),
                parseString(event, "source"),
                parseBoolean(event, "isAlert"),
                session.id(),
                event.getStartTime()
        );
        messages.add(message);
        return Result.CONTINUE;
    }

    @Override
    public List<ImportantMessage> get() {
        return messages;
    }
}

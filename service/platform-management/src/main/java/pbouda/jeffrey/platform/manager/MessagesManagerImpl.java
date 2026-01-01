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

import pbouda.jeffrey.platform.manager.jfr.ImportantMessageCollector;
import pbouda.jeffrey.platform.manager.jfr.ImportantMessageProcessor;
import pbouda.jeffrey.platform.manager.jfr.model.ImportantMessage;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.repository.parser.RepositoryIterators;
import pbouda.jeffrey.shared.model.repository.RecordingSession;
import pbouda.jeffrey.shared.model.time.AbsoluteTimeRange;
import pbouda.jeffrey.shared.model.time.RelativeTimeRange;
import pbouda.jeffrey.shared.model.time.TimeRange;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Implementation of MessagesManager that parses ImportantMessage events
 * from JFR streaming repositories across all sessions in a project.
 */
public class MessagesManagerImpl implements MessagesManager {

    private final Clock clock;
    private final RepositoryStorage repositoryStorage;

    public MessagesManagerImpl(Clock clock, RepositoryStorage repositoryStorage) {
        this.clock = clock;
        this.repositoryStorage = repositoryStorage;
    }

    @Override
    public List<ImportantMessage> getMessages(TimeRange timeRange) {
        List<RecordingSession> sessions = repositoryStorage.listSessions(false);
        if (sessions.isEmpty()) {
            return List.of();
        }

        // Convert TimeRange to AbsoluteTimeRange
        AbsoluteTimeRange absoluteTimeRange = switch (timeRange) {
            case RelativeTimeRange tr -> {
                Instant now = clock.instant();
                yield new AbsoluteTimeRange(now.minus(tr.duration()), now);
            }
            case AbsoluteTimeRange tr -> tr;
            default -> throw new IllegalStateException("Unexpected value: " + timeRange);
        };

        var iterator = RepositoryIterators.automatic(
                sessions, ImportantMessageProcessor::new, absoluteTimeRange, clock);

        return iterator.partialCollect(new ImportantMessageCollector());
    }

    @Override
    public List<ImportantMessage> getAlerts(TimeRange timeRange) {
        return getMessages(timeRange).stream()
                .filter(ImportantMessage::isAlert)
                .toList();
    }
}

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

import pbouda.jeffrey.provider.platform.repository.AlertRepository;
import pbouda.jeffrey.provider.platform.repository.MessageRepository;
import pbouda.jeffrey.shared.common.model.ImportantMessage;
import pbouda.jeffrey.shared.common.model.time.AbsoluteTimeRange;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.shared.common.model.time.TimeRange;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Implementation of MessagesManager that queries Message and Alert events from the database.
 */
public class MessagesManagerImpl implements MessagesManager {

    private final Clock clock;
    private final MessageRepository messageRepository;
    private final AlertRepository alertRepository;

    public MessagesManagerImpl(Clock clock, MessageRepository messageRepository, AlertRepository alertRepository) {
        this.clock = clock;
        this.messageRepository = messageRepository;
        this.alertRepository = alertRepository;
    }

    @Override
    public List<ImportantMessage> getMessages(TimeRange timeRange) {
        AbsoluteTimeRange range = resolveTimeRange(timeRange);
        return messageRepository.findAll(range.start(), range.end());
    }

    @Override
    public List<ImportantMessage> getAlerts(TimeRange timeRange) {
        AbsoluteTimeRange range = resolveTimeRange(timeRange);
        return alertRepository.findAll(range.start(), range.end());
    }

    private AbsoluteTimeRange resolveTimeRange(TimeRange timeRange) {
        return switch (timeRange) {
            case RelativeTimeRange tr -> {
                Instant now = clock.instant();
                yield new AbsoluteTimeRange(now.minus(tr.duration()), now);
            }
            case AbsoluteTimeRange tr -> tr;
            default -> throw new IllegalStateException("Unexpected value: " + timeRange);
        };
    }
}

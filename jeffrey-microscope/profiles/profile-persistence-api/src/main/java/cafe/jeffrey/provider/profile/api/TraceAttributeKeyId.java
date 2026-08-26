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

package cafe.jeffrey.provider.profile.api;

import java.util.Objects;

/**
 * What identifies one attribute key.
 * <p>
 * The owner is part of the identity, not decoration: {@code rows} declared by a JDBC query and
 * {@code rows} declared by some other event type are two keys that happen to share a name, and
 * merging them would average two unrelated measurements into one row.
 *
 * @param source where the key came from
 * @param owner  the event type that declares it, for {@link TraceAttributeSource#EVENT_FIELD};
 *               {@code null} for the other sources, whose keys are global
 * @param key    the key's name as the recording spells it
 */
public record TraceAttributeKeyId(TraceAttributeSource source, String owner, String key) {

    public TraceAttributeKeyId {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (source == TraceAttributeSource.EVENT_FIELD && owner == null) {
            throw new IllegalArgumentException("an event field must name the event type declaring it: " + key);
        }
        if (source.carrier() == TraceAttributeCarrier.NOTIFICATION && owner != null) {
            throw new IllegalArgumentException("a notification key is global and cannot name an owner: " + key);
        }
    }

    /** An attribute-map key, which no event type owns. */
    public static TraceAttributeKeyId attribute(String key) {
        return new TraceAttributeKeyId(TraceAttributeSource.ATTRIBUTE, null, key);
    }

    /** A key from a notification's attribute map, which no event type owns either. */
    public static TraceAttributeKeyId notificationAttribute(String key) {
        return new TraceAttributeKeyId(TraceAttributeSource.NOTIFICATION_ATTRIBUTE, null, key);
    }
}

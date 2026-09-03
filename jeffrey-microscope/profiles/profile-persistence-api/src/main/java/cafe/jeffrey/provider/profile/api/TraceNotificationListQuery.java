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

/**
 * Which notifications to aggregate. Every filter is optional; a null or blank one matches everything.
 *
 * @param severity        exact severity, {@code CRITICAL}, {@code HIGH}, {@code MEDIUM} or {@code LOW}
 * @param type            exact notification type
 * @param category        exact category
 * @param source          exact source
 * @param messageContains case-insensitive substring of the message
 * @param operation       keep only notifications raised inside traces of this operation
 * @param limit           maximum number of groups to return
 */
public record TraceNotificationListQuery(
        String severity,
        String type,
        String category,
        String source,
        String messageContains,
        TraceOperationId operation,
        int limit) {

    public TraceNotificationListQuery {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
    }

    /** Everything the profile's traces carry, the most severe kinds first. */
    public static TraceNotificationListQuery all(int limit) {
        return new TraceNotificationListQuery(null, null, null, null, null, null, limit);
    }

    /** Everything raised inside traces of one operation. */
    public static TraceNotificationListQuery ofOperation(TraceOperationId operation, int limit) {
        return new TraceNotificationListQuery(null, null, null, null, null, operation, limit);
    }

    public boolean hasSeverity() {
        return isGiven(severity);
    }

    public boolean hasType() {
        return isGiven(type);
    }

    public boolean hasCategory() {
        return isGiven(category);
    }

    public boolean hasSource() {
        return isGiven(source);
    }

    public boolean hasMessageFilter() {
        return isGiven(messageContains);
    }

    public boolean hasOperation() {
        return operation != null;
    }

    /** Whether any filter narrows the result; an unfiltered empty answer means there is nothing at all. */
    public boolean isFiltered() {
        return hasSeverity() || hasType() || hasCategory() || hasSource() || hasMessageFilter() || hasOperation();
    }

    private static boolean isGiven(String value) {
        return value != null && !value.isBlank();
    }
}

/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.flamegraph.code;

import cafe.jeffrey.provider.profile.api.CallSiteSelection;

import java.util.regex.Pattern;

/**
 * What a call-site read is asked: whose code is own, which event field the sites are split by, and
 * optionally one value of one field to keep.
 *
 * @param scope       the reader's own code; a call site is the deepest own frame on an event's stack
 * @param groupField  the event field whose value splits a call site into rows (a statement's
 *                    {@code name}, its {@code sql}, a request's {@code uri}); {@code null} for none
 * @param filterField a field to narrow by, or {@code null}
 * @param filterValue the value {@code filterField} must hold; given exactly when the field is
 * @param selection   bounds the ranked sites or statement groups before they reach Java
 */
public record CallSiteQuery(
        FrameScope scope, String groupField, String filterField, String filterValue, CallSiteSelection selection) {

    /** A JFR event field name: an identifier, nothing a JSON path could escape from. */
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public CallSiteQuery {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (selection == null) {
            throw new IllegalArgumentException("selection must not be null");
        }
        groupField = blankToNull(groupField);
        filterField = blankToNull(filterField);
        if ((filterField == null) != (filterValue == null)) {
            throw new IllegalArgumentException("A filter needs both its field and its value");
        }
        requireFieldName(groupField);
        requireFieldName(filterField);
    }

    private static void requireFieldName(String field) {
        if (field != null && !FIELD_NAME.matcher(field).matches()) {
            throw new IllegalArgumentException("Not an event field name: '" + field
                    + "'. jfr_describeEventType lists the fields of an event type.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

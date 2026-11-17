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

package cafe.jeffrey.provider.profile.api;

/**
 * What kind of thing carried an attribute — the two halves a trace is made of.
 * <p>
 * A key belongs to exactly one of these, decided by its {@link TraceAttributeSource}, and the carrier
 * is what tells every query which index table to read and what "the same one" means when a search is
 * scoped: the same span, or the same notification.
 * <p>
 * This is not a filter a caller chooses. It is derived from the key, so a condition can never be
 * pointed at the wrong table.
 */
public enum TraceAttributeCarrier {

    /** A span — an interval of work, with a name, an outcome and a place in the tree. */
    SPAN,

    /** A notification — an instant, which merely records the span that was open when it fired. */
    NOTIFICATION
}

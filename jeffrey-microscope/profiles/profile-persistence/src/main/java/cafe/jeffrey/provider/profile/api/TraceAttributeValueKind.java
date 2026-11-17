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
 * What a key's values turned out to be, inferred from the values themselves rather than declared.
 * <p>
 * It decides two things: which operators a caller is offered, and whether a key's values are
 * bucketed by order of magnitude when they are ranked. A key whose values are all {@code true} or
 * {@code false} is called out separately from a string because a two-valued key is a switch, and
 * offering it a substring match reads as a mistake.
 */
public enum TraceAttributeValueKind {

    STRING,
    NUMBER,
    BOOLEAN
}

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
package cafe.jeffrey.profile.heapdump.view;

/**
 * A single decoded instance field value.
 *
 * {@code value} is boxed:
 * <ul>
 *   <li>OBJECT → {@link Long} (referenced instance id; 0 if null reference)</li>
 *   <li>BOOLEAN → {@link Boolean}</li>
 *   <li>BYTE → {@link Byte}</li>
 *   <li>CHAR → {@link Character}</li>
 *   <li>SHORT → {@link Short}</li>
 *   <li>INT → {@link Integer}</li>
 *   <li>FLOAT → {@link Float}</li>
 *   <li>LONG → {@link Long}</li>
 *   <li>DOUBLE → {@link Double}</li>
 * </ul>
 */
public record InstanceFieldValue(String name, int basicType, Object value) {
}

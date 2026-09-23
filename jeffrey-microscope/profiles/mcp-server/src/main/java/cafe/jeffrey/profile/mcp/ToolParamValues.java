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
package cafe.jeffrey.profile.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The values a string parameter actually accepts, emitted as a JSON-Schema {@code enum}.
 * <p>
 * Several of Jeffrey's parameters are enumerations carried as strings — a direction is {@code SERVER} or
 * {@code CLIENT}, an I/O kind is {@code SOCKET} or {@code FILE} — and today the alternatives live only in
 * the prose of the description, where a client cannot act on them. Declaring them here moves the contract
 * into the schema, so a wrong value is refused before the call is made rather than after.
 * <p>
 * A parameter whose Java type is a real {@code enum} needs no annotation: its constants are read directly.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParamValues {

    String[] value();
}

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

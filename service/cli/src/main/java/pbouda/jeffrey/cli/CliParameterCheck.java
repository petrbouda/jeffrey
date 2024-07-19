/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.cli;

import pbouda.jeffrey.common.Type;

public abstract class CliParameterCheck {

    public static void weight(boolean weight, String eventType) {
        // Check whether WEIGHT-MODE is supported for the selected EVENT-TYPE
        if (weight && !Type.WEIGHT_SUPPORTED_TYPES.contains(Type.fromCode(eventType))) {
            System.out.println("Unsupported event type for weight-mode visualization. Supported types:");
            for (Type type : Type.WEIGHT_SUPPORTED_TYPES) {
                System.out.println(type.code());
            }

            System.exit(1);
        }
    }
}

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

/**
 * A {@code tools/call} that could not be dispatched to a tool at all: the name is not one this server
 * advertises, or the arguments do not fit the tool's schema.
 * <p>
 * The distinction this type carries is the one the MCP specification draws between a protocol error and
 * a tool error. A tool that ran and failed — no heap dump on this profile, a query that found nothing —
 * answers the model inside the result, with {@code isError} set, because the model is expected to read
 * it and try something else. A call that never reached a tool is not an answer to anything: the client
 * asked for something this server does not offer, and it is told so through the JSON-RPC error channel
 * as {@code -32602}, where a client can tell it apart from a failed analysis.
 * <p>
 * Extends {@link IllegalArgumentException} so that every existing caller — the ones that catch the
 * standard type to refuse a bad argument — keeps behaving as it did.
 */
public class ToolDispatchException extends IllegalArgumentException {

    public ToolDispatchException(String message) {
        super(message);
    }
}

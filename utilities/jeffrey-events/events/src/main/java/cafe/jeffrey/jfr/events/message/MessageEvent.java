/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.jfr.events.message;

import jdk.jfr.*;

@Name(MessageEvent.NAME)
@Label("Message Event")
@Description("A platform lifecycle message for monitoring")
@Category({"Application", "Message"})
@StackTrace(false)
public class MessageEvent extends Event {

    public static final String NAME = "jeffrey.Message";

    @Label("Type")
    @Description("Identifier for this type of message (e.g., HIGH_CPU_USAGE, CONNECTION_POOL_EXHAUSTED)")
    public String type;

    @Label("Title")
    @Description("Short summary of the message")
    public String title;

    @Label("Message")
    @Description("Detailed description of the message")
    public String message;

    @Label("Severity")
    @Description("The severity level of the message")
    public String severity;

    @Label("Category")
    @Description("The category of the message (e.g., PERFORMANCE, SECURITY, RESOURCE, AVAILABILITY)")
    public String category;

    @Label("Source")
    @Description("The component or service that raised the message")
    public String source;
}

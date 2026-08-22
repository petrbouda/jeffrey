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

@Name(AlertEvent.NAME)
@Label("Alert Event")
@Description("An alert for critical conditions requiring attention")
@Category({"Application", "Alert"})
@StackTrace(false)
public class AlertEvent extends Event {

    public static final String NAME = "jeffrey.Alert";

    @Label("Type")
    @Description("Identifier for this type of alert (e.g., JVM_CRASH_DETECTED, EVENT_PROCESSING_FAILED)")
    public String type;

    @Label("Title")
    @Description("Short summary of the alert")
    public String title;

    @Label("Message")
    @Description("Detailed description of the alert")
    public String message;

    @Label("Severity")
    @Description("The severity level of the alert")
    public String severity;

    @Label("Category")
    @Description("The category of the alert (e.g., PERFORMANCE, SECURITY, RESOURCE, AVAILABILITY)")
    public String category;

    @Label("Source")
    @Description("The component or service that raised the alert")
    public String source;
}

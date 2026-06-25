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

package cafe.jeffrey.jfr.events.appinfo;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;
import jdk.jfr.Timestamp;

/**
 * Application and session identity, emitted once at the start of every JFR chunk
 * so that each chunk self-describes which workspace, project, instance, and
 * session it belongs to — the application-identity analogue of
 * {@code jdk.JVMInformation}.
 *
 * <p>The sole emitter is the Jeffrey Agent, which is the only component that
 * holds the identity propagated from the Provisioner. The agent carries its own
 * self-contained copy of this class to stay zero-dependency; JFR deduplicates
 * event types by {@link Name}.</p>
 */
@Name(AppInformationEvent.NAME)
@Label("Application Information")
@Description("Jeffrey application and session identity, emitted once at the start of every chunk "
        + "so each chunk self-describes which workspace, project, instance, and session it belongs to")
@Category({"Application", "Information"})
@StackTrace(false)
@Period("beginChunk")
public class AppInformationEvent extends Event {

    public static final String NAME = "jeffrey.AppInformation";

    @Label("Workspace ID")
    @Description("Reference id of the Jeffrey workspace that owns the project (e.g. \"$default\")")
    public String workspaceId;

    @Label("Project ID")
    @Description("Stable unique identifier (UUID) of the project the recording belongs to")
    public String projectId;

    @Label("Project Name")
    @Description("Machine-friendly project name (letters, digits, dashes, underscores)")
    public String projectName;

    @Label("Project Label")
    @Description("Human-readable project label shown in the Jeffrey UI")
    public String projectLabel;

    @Label("Instance ID")
    @Description("Identifier of the running instance, typically the host or pod name; "
            + "a generated UUID is used only as a fallback")
    public String instanceId;

    @Label("Session ID")
    @Description("Unique identifier (UUID) of this profiling session, generated once per JVM start")
    public String sessionId;

    @Label("Session Order")
    @Description("Sequential number of this session within the instance "
            + "(1 for the first JVM run, 2 for the next, and so on)")
    public int sessionOrder;

    @Label("Attributes")
    @Description("Free-form custom metadata serialized as \"key=value;key=value\" "
            + "(e.g. cluster, namespace, environment)")
    public String attributes;

    @Label("Provisioned At")
    @Description("Wall-clock time at which the session was provisioned by the Provisioner, "
            + "in milliseconds since the epoch")
    @Timestamp(Timestamp.MILLISECONDS_SINCE_EPOCH)
    public long provisionedAt;

    @Label("JVM Started At")
    @Description("Wall-clock time at which the JVM process started, in milliseconds since the epoch")
    @Timestamp(Timestamp.MILLISECONDS_SINCE_EPOCH)
    public long jvmStartedAt;
}

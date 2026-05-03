package cafe.jeffrey.shared.common;

public abstract class CliConstants {

    public static final String PROFILER_PATH = "<<JEFFREY_PROFILER_PATH>>";
    public static final String CURRENT_SESSION = "<<JEFFREY_CURRENT_SESSION>>";

    public static final String DEFAULT_PROFILER_CONFIG =
            "-agentpath:" + PROFILER_PATH + "=start,alloc,lock,event=ctimer,jfrsync=default,loop=15m,chunksize=5m,file="
                    + CURRENT_SESSION + "/profile-%t.jfr";

    /**
     * Reference id of the workspace used when {@code project.workspace-ref-id} is not set
     * in the CLI's HOCON config. Both jeffrey-cli (when writing project metadata + the
     * filesystem layout) and jeffrey-server (as the fallback for
     * {@code jeffrey.server.default-workspace.reference-id}) resolve to this single
     * value so the two sides agree on the directory structure
     * <code>&lt;workspaces&gt;/$default/&lt;project&gt;/...</code>.
     */
    public static final String DEFAULT_WORKSPACE_REF_ID = "$default";
}

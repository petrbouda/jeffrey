package pbouda.jeffrey.shared.common;

public abstract class AgentConstants {

    public static final String PROFILER_PATH = "<<JEFFREY_PROFILER_PATH>>";
    public static final String CURRENT_SESSION = "<<JEFFREY_CURRENT_SESSION>>";

    public static final String DEFAULT_PROFILER_CONFIG =
            "-agentpath:" + PROFILER_PATH + "=start,alloc,lock,event=ctimer,jfrsync=default,loop=15m,chunksize=5m,file="
                    + CURRENT_SESSION + "/profile-%t.jfr";
}

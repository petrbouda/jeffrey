package pbouda.jeffrey;

import java.nio.file.Path;

public abstract class Naming {

    public static final String DATA_DIR_NAME = "data";
    public static final String JFR_PROFILE_NAME = "profile.jfr";
    public static final String FLAMEGRAPHS_DIR_NAME = "flamegraphs";
    public static final String FLAMEGRAPHS_PARTIAL_DIR_NAME = "partial";
    public static final String FLAMEGRAPHS_OVERALL_DIR_NAME = "overall";
    public static final String HEATMAPS_DIR_NAME = "heatmaps";

    public static String profileDirectoryName(Path profilePath) {
        return profilePath
                .getFileName().toString()
                .replace(".jfr", "")
                .replace(".", "_")
                .replace(" ", "_");
    }

}

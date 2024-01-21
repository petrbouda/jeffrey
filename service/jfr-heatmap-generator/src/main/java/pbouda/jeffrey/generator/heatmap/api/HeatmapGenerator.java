package pbouda.jeffrey.generator.heatmap.api;

import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Generate a data-file for a heatmap from a selected event from JFR file.
 */
public interface HeatmapGenerator {

    /**
     * Generate a data-file for the heatmap base on <i>JFR file</i> and selected <i>eventName</>. The result is streamed
     * into the provided {@link OutputStream}.
     *
     * @param jfrFile JFR file as a source of the specified event.
     * @param output a stream to write the generated result.
     * @param eventName a name of the select event that will be used to generate a data for heatmap.
     */
    void generate(Path jfrFile, OutputStream output, String eventName);

    /**
     * Generate a data-file for the heatmap base on <i>JFR file</i> and selected <i>eventName</>. The result is returned
     * in a byte-array representation.
     *
     * @param jfrFile JFR file as a source of the specified event.
     * @param eventName a name of the select event that will be used to generate a data for heatmap.
     * @return heatmap data represented in byte-array format.
     */
    byte[] generate(Path jfrFile, String eventName);

    /**
     * Generate a data-file for the heatmap base on <i>JFR file</i> and selected <i>eventName</>.  The result is returned
     * in a byte-array representation.
     *
     * @param jfrFile JFR file as a source of the specified event.
     * @param eventName a name of the select event that will be used to generate a data for heatmap.
     * @param fromBeginning limits the amount of data using the duration from the beginning.
     * @return limited range of heatmap data represented in byte-array format.
     */
    byte[] generate(Path jfrFile, String eventName, Duration fromBeginning);
}

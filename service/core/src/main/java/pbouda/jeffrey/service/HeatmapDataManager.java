package pbouda.jeffrey.service;

import org.openjdk.jmc.flightrecorder.jdk.JdkTypeIDs;
import pbouda.jeffrey.FileUtils;
import pbouda.jeffrey.WorkingDirectory;
import pbouda.jeffrey.flamegraph.EventType;
import pbouda.jeffrey.generator.heatmap.api.HeatmapGenerator;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HeatmapDataManager {

    private final HeatmapGenerator generator;

    public HeatmapDataManager(HeatmapGenerator generator) {
        this.generator = generator;
    }

    /**
     * Method checks whether the data file for the given {@code profile} and {@code eventType} has been already
     * generated locally.
     *
     * @param profile   filename of the JFR profile.
     * @param eventType JFR event type that is going to be checked.
     * @return {@link true} if the data file exists.
     */
    public boolean exists(String profile, EventType eventType) {
        return Files.exists(dataFilePath(profile, eventType));
    }

    /**
     * Generates a data file for the given heatmap identified by {@code profile} and {@code eventType} and stores
     * the data file locally inside the profile's generated directory ({@link WorkingDirectory#generatedDir(String)}).
     *
     * @param profile   filename of the JFR profile.
     * @param eventType JFR event type that is going to be checked.
     * @return a path to the newly created data file.
     */
    public Path createDataFile(String profile, EventType eventType) {
        Path targetFile = FileUtils.createDirectoriesForFile(dataFilePath(profile, eventType));
        Path profilePath = WorkingDirectory.profilePath(profile);
        try (var bos = new BufferedOutputStream(Files.newOutputStream(targetFile))) {
            generator.generate(profilePath, bos, eventType.eventTypeName());
            return targetFile;
        } catch (IOException e) {
            throw new RuntimeException(STR."Cannot write the generated Heatmap to the file: \{targetFile}", e);
        }
    }

    /**
     * Returns the data file path for the given {@code profile} and {@code eventType}. It checks the profile's directory
     * for generated content ({@link WorkingDirectory#generatedDir(String)})
     *
     * @param profile   filename of the JFR profile.
     * @param eventType JFR event type that is going to be checked.
     * @return path of the data for the given heatmap.
     */
    public Path dataFilePath(String profile, EventType eventType) {
        return WorkingDirectory.generatedDir(profile)
                .resolve(createFilename(eventType));
    }

    private static String createFilename(EventType eventType) {
        String eventTypeName = eventType.name().toLowerCase();
        return STR."heatmap-\{eventTypeName}.data";
    }
}

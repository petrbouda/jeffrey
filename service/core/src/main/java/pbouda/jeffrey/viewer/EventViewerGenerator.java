package pbouda.jeffrey.viewer;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;

public interface EventViewerGenerator {

    /**
     * Generates a JSON entity for <a href="https://primevue.org/treetable/#template">PrimeVue TreeTable</a> containing
     * all event types available for the current profile.
     *
     * @param path path to the JFR recording file
     * @return all event types for the current profile in the format of PrimeVue TreeTable
     */
    JsonNode allEventTypes(Path path);
}

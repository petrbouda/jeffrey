package pbouda.jeffrey.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Type;

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

    /**
     * Generates and provides all events of the given type.
     *
     * @param path path to the JFR recording file
     * @param eventType type of the events to be fetched from the recording
     * @return events in JSON format.
     */
    JsonNode events(Path path, Type eventType);

    /**
     * Generates the structure of the given event type to be able to generate a table in UI.
     *
     * @param path path to the JFR recording file
     * @param eventType type of the events to be fetched from the recording
     * @return event structure in JSON format.
     */
    JsonNode eventColumns(Path path, Type eventType);

}

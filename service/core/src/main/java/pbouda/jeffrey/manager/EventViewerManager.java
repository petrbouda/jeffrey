package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.function.Function;

public interface EventViewerManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, EventViewerManager> {
    }

    /**
     * Generates a JSON entity for <a href="https://primevue.org/treetable/#template">PrimeVue TreeTable</a> containing
     * all event types available for the current profile.
     *
     * @return all event types for the current profile in the format of PrimeVue TreeTable
     */
    JsonNode allEventTypes();

    /**
     * Generates and provides all events of the given type.
     *
     * @param eventType type of the events to be fetched from the recording
     * @return events in JSON format.
     */
    JsonNode events(Type eventType);

    /**
     * Generates the structure of the given event type to be able to generate a table in UI.
     *
     * @param eventType type of the events to be fetched from the recording
     * @return event structure in JSON format.
     */
    JsonNode eventColumns(Type eventType);
}

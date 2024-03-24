package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
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
}

package pbouda.jeffrey.exception;

import pbouda.jeffrey.common.Type;

public class NotFoundException extends RuntimeException {

    private final String profileId;
    private final String flamegraphId;
    private final Type eventType;

    public NotFoundException(String profileId, String flamegraphId) {
        this(profileId, flamegraphId, null);
    }

    public NotFoundException(String profileId, Type eventType) {
        this(profileId, null, eventType);
    }

    public NotFoundException(String profileId, String flamegraphId, Type eventType) {
        super("Cannot find the flamegraph: profile_id=" + profileId + " flamegraph_id=" + flamegraphId + ", " + " event_type=" + eventType);

        this.profileId = profileId;
        this.flamegraphId = flamegraphId;
        this.eventType = eventType;
    }
}

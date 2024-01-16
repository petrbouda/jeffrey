package pbouda.jeffrey.profile;

import pbouda.jeffrey.flamegraph.EventType;

import java.io.OutputStream;
import java.time.Instant;

public interface ProfileManager {

    OutputStream uploadFlamegraph(EventType eventType);

    OutputStream uploadPartialFlamegraph(String filename);

    OutputStream uploadHeatmap();

}

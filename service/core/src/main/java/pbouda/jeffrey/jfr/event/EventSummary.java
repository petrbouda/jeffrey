package pbouda.jeffrey.jfr.event;

import jdk.jfr.EventType;

public record EventSummary(EventType eventType, long samples, long weight) {
}

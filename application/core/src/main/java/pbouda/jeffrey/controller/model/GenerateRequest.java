package pbouda.jeffrey.controller.model;

import pbouda.jeffrey.flamegraph.EventType;

import java.util.List;

public record GenerateRequest(String profile, List<EventType> eventTypes) {
}

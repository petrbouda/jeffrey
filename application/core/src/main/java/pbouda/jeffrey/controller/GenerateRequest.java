package pbouda.jeffrey.controller;

import pbouda.jeffrey.flamegraph.EventType;

import java.util.List;

public record GenerateRequest(String profile, List<EventType> types) {
}

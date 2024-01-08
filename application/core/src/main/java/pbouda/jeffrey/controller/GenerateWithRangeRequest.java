package pbouda.jeffrey.controller;

import pbouda.jeffrey.flamegraph.EventType;

import java.util.List;

public record GenerateWithRangeRequest(String profile, String flamegraphName, EventType type, TimeRange timeRange) {
}

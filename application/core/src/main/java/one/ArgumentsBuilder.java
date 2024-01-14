package one;

import pbouda.jeffrey.flamegraph.EventType;

import java.nio.file.Path;
import java.util.regex.Pattern;

public class ArgumentsBuilder {

    private final Arguments args = new Arguments();

    public static ArgumentsBuilder create() {
        return new ArgumentsBuilder();
    }

    public ArgumentsBuilder withTitle(String title) {
        args.title = title;
        return this;
    }

    public ArgumentsBuilder withHighlight(String highlight) {
        args.highlight = highlight;
        return this;
    }

    public ArgumentsBuilder withState(String state) {
        args.state = state;
        return this;
    }

    public ArgumentsBuilder withInclude(Pattern include) {
        args.include = include;
        return this;
    }

    public ArgumentsBuilder withExclude(Pattern exclude) {
        args.exclude = exclude;
        return this;
    }

    public ArgumentsBuilder withMinWidth(double minWidth) {
        args.minwidth = minWidth;
        return this;
    }

    public ArgumentsBuilder withSkip(int skip) {
        args.skip = skip;
        return this;
    }

    public ArgumentsBuilder withReverse(boolean reverse) {
        args.reverse = reverse;
        return this;
    }

    public ArgumentsBuilder withThreads(boolean threads) {
        args.threads = threads;
        return this;
    }

    public ArgumentsBuilder withClassify(boolean classify) {
        args.classify = classify;
        return this;
    }

    public ArgumentsBuilder withTotal(boolean total) {
        args.total = total;
        return this;
    }

    public ArgumentsBuilder withLines(boolean lines) {
        args.lines = lines;
        return this;
    }

    public ArgumentsBuilder withSimple(boolean simple) {
        args.simple = simple;
        return this;
    }

    public ArgumentsBuilder withDot(boolean dot) {
        args.dot = dot;
        return this;
    }

    public ArgumentsBuilder withNorm(boolean norm) {
        args.norm = norm;
        return this;
    }

    public ArgumentsBuilder withCollapsed(boolean collapsed) {
        args.collapsed = collapsed;
        return this;
    }

    public ArgumentsBuilder withFrom(long from) {
        args.from = from;
        return this;
    }

    public ArgumentsBuilder withTo(long to) {
        args.to = to;
        return this;
    }

    public ArgumentsBuilder withInput(String input) {
        args.input = input;
        return this;
    }

    public ArgumentsBuilder withInput(Path input) {
        args.input = input.toString();
        return this;
    }

    public ArgumentsBuilder withOutput(String output) {
        args.output = output;
        return this;
    }

    public ArgumentsBuilder withOutput(Path output) {
        args.output = output.toString();
        return this;
    }

    public ArgumentsBuilder withEventType(EventType eventType) {
        switch (eventType) {
            case LIVE_OBJECTS -> args.live = true;
            case ALLOCATIONS -> args.alloc = true;
            case LOCKS -> args.lock = true;
            case EXECUTION_SAMPLES -> {
                // it's a default, no flag to activate
            }
        }
        return this;
    }

    public Arguments build() {
        return args;
    }
}

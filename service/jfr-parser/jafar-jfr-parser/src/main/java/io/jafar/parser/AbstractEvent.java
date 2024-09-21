

package io.jafar.parser;

import io.jafar.parser.internal_api.ParserContext;
import io.jafar.parser.internal_api.RecordingStream;

public abstract class AbstractEvent {
    private final ParserContext context;

    protected AbstractEvent(RecordingStream stream) {
        this.context = stream.getContext();
    }

    public final ParserContext getContext() {
        return context;
    }
}

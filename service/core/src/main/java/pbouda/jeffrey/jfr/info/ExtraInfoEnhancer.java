package pbouda.jeffrey.jfr.info;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;

public interface ExtraInfoEnhancer {

    boolean isApplicable(EventType eventType);

    void accept(ObjectNode json);

}

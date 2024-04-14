package pbouda.jeffrey.rules;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;

public interface RulesResultsProvider {

    JsonNode results(Path recording);

}

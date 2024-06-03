package pbouda.jeffrey.rules;

import java.nio.file.Path;
import java.util.List;

public interface RulesResultsProvider {

    List<AnalysisItem> results(Path recording);

}

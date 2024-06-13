package pbouda.jeffrey.rules;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class JdkRulesResultsProvider implements RulesResultsProvider {

    @Override
    public List<AnalysisItem> results(Path recording) {
        return RuleResultsGenerator.generate(recording).stream()
                .sorted(Comparator.comparing(a -> a.severity().order()))
                .toList();
    }
}

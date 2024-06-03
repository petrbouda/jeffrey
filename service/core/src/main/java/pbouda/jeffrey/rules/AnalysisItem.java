package pbouda.jeffrey.rules;

import org.openjdk.jmc.flightrecorder.rules.IRule;

public record AnalysisItem(
        String rule,
        Severity severity,
        String explanation,
        String summary,
        String solution,
        String score) {

    public enum Severity {
        OK(5), WARNING(1), NA(3), INFO(2), IGNORE(4);

        private final int order;

        Severity(int order) {
            this.order = order;
        }

        public int order() {
            return order;
        }
    }

    public AnalysisItem(
            IRule rule,
            org.openjdk.jmc.flightrecorder.rules.Severity severity,
            String explanation,
            String summary,
            String solution,
            String score) {

        this(rule.getName(), Severity.valueOf(severity.name()), explanation, summary, solution, score);
    }
}

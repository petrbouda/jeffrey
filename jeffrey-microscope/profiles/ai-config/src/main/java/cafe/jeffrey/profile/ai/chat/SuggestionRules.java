/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.profile.ai.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Keyword-based heuristic for generating follow-up suggestions after an AI analysis call.
 * Each domain assistant declares its rule table once; {@link #suggestFor(String, String)} evaluates
 * the rules against the user's question and the assistant's response. Rules are evaluated in
 * declaration order and the combined suggestions are capped at {@link #MAX_SUGGESTIONS}.
 *
 * @param questionRules       rules matched against the user's question
 * @param responseRules       rules matched against the assistant's response
 * @param fallbackSuggestions suggestions returned when no rule matched
 */
public record SuggestionRules(
        List<QuestionRule> questionRules,
        List<ResponseRule> responseRules,
        List<String> fallbackSuggestions
) {

    private static final int MAX_SUGGESTIONS = 3;

    public SuggestionRules {
        questionRules = List.copyOf(questionRules);
        responseRules = List.copyOf(responseRules);
        fallbackSuggestions = List.copyOf(fallbackSuggestions);
    }

    /**
     * Fires when the question contains any of the keywords (case-insensitive).
     *
     * @param keywords    the alternative keywords, any of which triggers the rule
     * @param suggestions the suggestions contributed when the rule fires
     */
    public record QuestionRule(Set<String> keywords, List<String> suggestions) {
        public QuestionRule {
            keywords = Set.copyOf(keywords);
            suggestions = List.copyOf(suggestions);
        }
    }

    /**
     * Fires when the response contains the keyword and the question does not contain the excluded
     * keyword (both case-insensitive) — i.e. the response drifted into a topic the user did not ask about.
     *
     * @param responseKeyword         the keyword looked up in the assistant's response
     * @param excludedQuestionKeyword the keyword that suppresses the rule when present in the question
     * @param suggestions             the suggestions contributed when the rule fires
     */
    public record ResponseRule(String responseKeyword, String excludedQuestionKeyword, List<String> suggestions) {
        public ResponseRule {
            suggestions = List.copyOf(suggestions);
        }
    }

    /**
     * Evaluate the rule table for a finished exchange.
     *
     * @param question the user's original question
     * @param response the assistant's response text
     * @return at most {@link #MAX_SUGGESTIONS} follow-up suggestions (fallbacks when no rule matched)
     */
    public List<String> suggestFor(String question, String response) {
        String lowerQuestion = question.toLowerCase();
        String lowerResponse = response.toLowerCase();

        List<String> suggestions = new ArrayList<>();
        for (QuestionRule rule : questionRules) {
            if (containsAny(lowerQuestion, rule.keywords())) {
                suggestions.addAll(rule.suggestions());
            }
        }
        for (ResponseRule rule : responseRules) {
            if (lowerResponse.contains(rule.responseKeyword())
                    && !lowerQuestion.contains(rule.excludedQuestionKeyword())) {
                suggestions.addAll(rule.suggestions());
            }
        }
        if (suggestions.isEmpty()) {
            suggestions.addAll(fallbackSuggestions);
        }
        return suggestions.stream().limit(MAX_SUGGESTIONS).toList();
    }

    private static boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

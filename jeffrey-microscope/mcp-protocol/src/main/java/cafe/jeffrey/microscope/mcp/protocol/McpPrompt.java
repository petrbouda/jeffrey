/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A workflow the server offers by name, which a client can insert into a conversation.
 * <p>
 * A server's workflows may also ship elsewhere — as a plugin's skills, say — and a client that has them
 * needs nothing here. Prompts exist for the clients that speak MCP and nothing else: without them such a
 * client gets a list of tools and no account of which to reach for first, which is the part that
 * actually makes the surface usable.
 *
 * @param name      the prompt name, as the client lists it
 * @param title     a human-readable name for a menu
 * @param description what the workflow is for
 * @param arguments what the caller may supply; all optional, since a workflow reads as guidance even
 *                  with nothing filled in
 * @param text      the body handed back as the prompt's message
 */
public record McpPrompt(
        String name,
        String title,
        String description,
        List<Argument> arguments,
        String text) {

    /** The token a skill body writes to mean "whatever the caller supplied, in one line". */
    private static final String ARGUMENTS_TOKEN_NAME = "ARGUMENTS";

    private static final String ARGUMENTS_JOIN_DELIMITER = " ";

    private static final String CONTEXT_HEADER = "\n\nCaller-provided workflow context (JSON):\n";

    /**
     * A token is {@code $} + the name, not followed by another identifier character — so
     * {@code $itemId} matches inside {@code Item: $itemId.} but not the start of
     * {@code $itemId2}, and substituting {@code itemId} never touches a differently-named token
     * that merely starts with the same letters.
     */
    private static Pattern tokenPattern(String tokenName) {
        return Pattern.compile("\\$" + Pattern.quote(tokenName) + "(?![A-Za-z0-9_])");
    }

    public McpPrompt {
        arguments = List.copyOf(arguments);
    }

    /**
     * Fills {@code $ARGUMENTS} and {@code $<argumentName>} placeholders in the body with what the
     * caller supplied. A supplied, declared argument whose own {@code $<argumentName>} token, or the
     * general {@code $ARGUMENTS} token, already occurred in the body is considered read by the skill and
     * is not repeated; every other supplied argument is listed afterward in a JSON block, so a value
     * never appears twice and a skill that never wrote a placeholder still receives what was supplied. A
     * body that consumes every supplied argument this way gets no block at all.
     */
    public String render(JsonNode supplied) {
        if (supplied != null && !supplied.isObject()) {
            throw new IllegalArgumentException("Prompt arguments must be an object");
        }
        Set<String> declared = arguments.stream().map(Argument::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (supplied != null) {
            for (String name : supplied.propertyNames()) {
                if (!declared.contains(name)) {
                    String declaredList = declared.isEmpty() ? "(none)" : String.join(", ", declared);
                    throw new IllegalArgumentException(
                            "Unknown prompt argument: '" + name + "'. Declared arguments: " + declaredList);
                }
            }
        }
        Pattern argumentsToken = tokenPattern(ARGUMENTS_TOKEN_NAME);
        boolean argumentsTokenConsumesEverything = argumentsToken.matcher(text).find();
        ObjectNode context = McpJson.createObject();
        List<String> suppliedValues = new ArrayList<>();
        String rendered = text;
        for (Argument argument : arguments) {
            JsonNode value = supplied == null ? null : supplied.get(argument.name());
            if (value == null) {
                if (argument.required()) {
                    throw new IllegalArgumentException("Missing required prompt argument: " + argument.name());
                }
                continue;
            }
            if (!value.isString()) {
                throw new IllegalArgumentException("Prompt argument '" + argument.name() + "' must be a string");
            }
            String stringValue = value.asString();
            suppliedValues.add(stringValue);
            Pattern ownToken = tokenPattern(argument.name());
            boolean consumedByOwnToken = ownToken.matcher(text).find();
            rendered = ownToken.matcher(rendered).replaceAll(Matcher.quoteReplacement(stringValue));
            if (!consumedByOwnToken && !argumentsTokenConsumesEverything) {
                context.set(argument.name(), value);
            }
        }
        String joinedArguments = String.join(ARGUMENTS_JOIN_DELIMITER, suppliedValues);
        rendered = argumentsToken.matcher(rendered).replaceAll(Matcher.quoteReplacement(joinedArguments));
        return context.isEmpty() ? rendered : rendered + CONTEXT_HEADER + context;
    }

    public record Argument(String name, String description, boolean required) {
    }
}

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

/**
 * Base for no-op assistant implementations used when AI is not configured.
 * Subclasses only implement their domain-specific {@code analyze} method and
 * return {@link #notConfiguredResponse()}.
 */
public abstract class NoOpAssistantService implements AiAssistantService {

    private static final String NOT_CONFIGURED_TEMPLATE = """
            AI-powered %s is not configured.

            To enable AI analysis, configure the following properties:
            - jeffrey.ai.provider=claude (or chatgpt)
            - jeffrey.ai.api-key=your-api-key

            See the documentation for more details on configuring AI providers.
            """;

    private final String notConfiguredMessage;

    /**
     * @param analysisKind human-readable kind of analysis (e.g. "JFR analysis", "heap dump analysis")
     */
    protected NoOpAssistantService(String analysisKind) {
        this.notConfiguredMessage = NOT_CONFIGURED_TEMPLATE.formatted(analysisKind);
    }

    @Override
    public final boolean isAvailable() {
        return false;
    }

    @Override
    public final String getModelName() {
        return null;
    }

    @Override
    public final String getProviderName() {
        return null;
    }

    /**
     * @return a text-only response explaining that the AI assistant is not configured
     */
    protected final AssistantResponse notConfiguredResponse() {
        return AssistantResponse.textOnly(notConfiguredMessage);
    }
}

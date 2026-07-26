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

package cafe.jeffrey.hub.core.workspace;

import java.util.function.Consumer;

/**
 * Lifecycle handlers for a live workspace event subscription.
 *
 * @param onNext     called for each produced page
 * @param onComplete called when the subscription ends normally
 * @param onError    called when the subscription fails
 * @param onClose    called when the subscription is closed, regardless of outcome
 */
public record WorkspaceEventCallbacks(
        Consumer<WorkspaceEventPage> onNext,
        Runnable onComplete,
        Consumer<Throwable> onError,
        Runnable onClose) {

    private static final Runnable NO_OP = () -> {
    };

    public WorkspaceEventCallbacks(
            Consumer<WorkspaceEventPage> onNext, Runnable onComplete, Consumer<Throwable> onError) {

        this(onNext, onComplete, onError, NO_OP);
    }

    /**
     * Returns a copy that runs the existing {@code onClose} first, then the given one.
     */
    public WorkspaceEventCallbacks withOnClose(Runnable additionalOnClose) {
        Runnable previous = this.onClose;
        return new WorkspaceEventCallbacks(onNext, onComplete, onError, () -> {
            previous.run();
            additionalOnClose.run();
        });
    }
}

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

package cafe.jeffrey.profile.manager.model.trace;

import java.util.List;

/**
 * The stack behind one throw, <strong>topmost frame first</strong>.
 * <p>
 * Every frame is returned; which of them are worth showing is the reader's question, not this
 * layer's. The UI folds runs of library frames away and lets them be opened again, and a fold that
 * lived here could not be undone without another round trip.
 *
 * @param stacktraceId the id the throw carried, echoed back so a response can be matched to the
 *                     request that asked for it
 * @param frames       the frames, throwing frame first and {@code Thread.run} last. Empty when the
 *                     recording captured no stack, which is ordinary rather than an error
 */
public record TraceStacktrace(String stacktraceId, List<TraceStackFrameRow> frames) {
}

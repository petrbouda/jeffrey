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

package cafe.jeffrey.provider.profile.api;

import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;

/**
 * Thread-safe deduplicator for frame/stacktrace/thread hashes on the ingest path.
 * Uses primitive long sets (no boxing) with striped locking to reduce contention
 * between parallel chunk writers.
 */
public class EventDeduplicator {

    private final StripedLongSet frameUsed = new StripedLongSet();
    private final StripedLongSet stacktraceUsed = new StripedLongSet();
    private final StripedLongSet threadUsed = new StripedLongSet();

    public boolean checkAndAddFrame(long value) {
        return frameUsed.add(value);
    }

    public boolean checkAndAddStacktrace(long value) {
        return stacktraceUsed.add(value);
    }

    public boolean checkAndAddThread(long value) {
        return threadUsed.add(value);
    }

    /**
     * Primitive long set partitioned into independently synchronized stripes.
     * A value always maps to the same stripe, so "was it added before" stays
     * consistent while writers touching different stripes do not block each other.
     */
    private static final class StripedLongSet {

        private static final int STRIPE_COUNT = 16;
        private static final int STRIPE_MASK = STRIPE_COUNT - 1;

        private final LongHashSet[] stripes;

        private StripedLongSet() {
            this.stripes = new LongHashSet[STRIPE_COUNT];
            for (int i = 0; i < STRIPE_COUNT; i++) {
                this.stripes[i] = new LongHashSet();
            }
        }

        /**
         * Checks if the value was already added. If not, it adds it and returns true.
         *
         * @param value deduplicated value
         * @return true if the value was not present and was added, false if it was already present.
         */
        private boolean add(long value) {
            LongHashSet stripe = stripes[stripeIndex(value)];
            synchronized (stripe) {
                return stripe.add(value);
            }
        }

        private static int stripeIndex(long value) {
            int hash = Long.hashCode(value);
            // Spread the upper bits down so values differing only in high bits do not share a stripe
            return (hash ^ (hash >>> 16)) & STRIPE_MASK;
        }
    }
}

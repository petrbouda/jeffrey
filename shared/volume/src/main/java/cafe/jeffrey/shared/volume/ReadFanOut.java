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

package cafe.jeffrey.shared.volume;

import java.util.List;
import java.util.function.Function;

/**
 * How a list of independent reads is run: one after another, or several at a time. On an Azure
 * file share each read is a round-trip, so a page that reads ten sessions waits for ten of them
 * in a row unless they go out together. The answers keep the order of what was asked.
 *
 * <p>A read run by a fan-out that itself fans out runs its inner reads one after another, so the
 * reads in flight never exceed the outer bound and a task never waits on a slot it holds.
 */
public interface ReadFanOut {

    /**
     * Each item read, in the items' order.
     *
     * @throws RuntimeException the first failure among the reads, as the read threw it
     */
    <T, R> List<R> map(List<T> items, Function<T, R> read);

    /** One read after another, on the calling thread: the hub's, on its own mount. */
    static ReadFanOut sequential() {
        return Sequential.INSTANCE;
    }

    /** The reads one after another. */
    final class Sequential implements ReadFanOut {

        private static final Sequential INSTANCE = new Sequential();

        private Sequential() {
        }

        @Override
        public <T, R> List<R> map(List<T> items, Function<T, R> read) {
            return items.stream().map(read).toList();
        }
    }
}

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

package cafe.jeffrey.shared.common;

import java.time.Duration;
import java.util.concurrent.*;

public abstract class CompletableFutures {

    public static <T> CompletableFuture<T> from(
            Future<T> future, ScheduledExecutorService scheduler, Duration pollInterval) {

        CompletableFuture<T> cf = new CompletableFuture<>();
        Runnable poller = new Runnable() {
            @Override
            public void run() {
                if (future.isDone()) {
                    try {
                        cf.complete(future.get());
                    } catch (ExecutionException e) {
                        cf.completeExceptionally(e.getCause());
                    } catch (InterruptedException e) {
                        cf.completeExceptionally(e);
                    }
                } else if (future.isCancelled()) {
                    cf.cancel(false);
                } else {
                    scheduler.schedule(this, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
                }
            }
        };
        scheduler.execute(poller);
        return cf;
    }
}

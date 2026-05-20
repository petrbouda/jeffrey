### Goal

Produce a blocking / contention report: where threads spent time waiting, what they were waiting on, and which call paths are the highest-impact candidates for contention reduction.

**Critical framing:** the weight unit is **nanoseconds blocked**, not samples or bytes. This profile shows *waiters*. It does **not** show *who held the lock* — surfacing the holder is a follow-up step (look at concurrent CPU samples or thread-state events from the same window).

### What to look for first

1. **Heaviest leaves by `total` (which is nanoseconds blocked, not samples)** — these are the dominant wait sources.
2. **The synthetic blocking-object frame at the top** — when present, it identifies the *class of the thing waited on*. For `JAVA_MONITOR_ENTER` / `JAVA_MONITOR_WAIT`, it's the contended monitor's class. For `THREAD_PARK`, it's the parker blocker (the argument to `LockSupport.park(blocker)`). For `THREAD_SLEEP`, there is no blocking object — that frame is absent and the leaf is `Thread.sleep` itself.
3. **Classify each finding by event subtype**:
   - **`JAVA_MONITOR_ENTER`** — contention on a synchronized block / method. The blocking-object class names what's contended.
   - **`JAVA_MONITOR_WAIT`** — a thread that called `Object.wait()` and is parked on a condition. Usually intentional; check whether the wait timeout is reasonable.
   - **`THREAD_PARK`** — `LockSupport.park` or `Unsafe.park` — used by `ReentrantLock`, `Semaphore`, `BlockingQueue.poll`, `CountDownLatch`, completable-future awaits. The blocker class hints at which.
   - **`THREAD_SLEEP`** — `Thread.sleep(N)`. Almost always intentional, but a hot `sleep` in production code is usually a smell (polling, retry-with-backoff, throttle workaround).
4. **The call path above the leaf** — tells you which application-level operation incurred the wait.

### How to ground claims

- Cite the **call path** for every finding, including the synthetic blocking-object class when present.
- Cite the **numbers shown in the bullet**: the total `nanoseconds Blocked` for the frame and its `% of total`.
- Specify the **event subtype** for each finding (monitor enter / monitor wait / park / sleep) — the fix kind differs.
- **Do not invent file:line numbers**. The export has none. Refer to frames by their method signatures as printed.

### Expected output shape

Opening table of top wait sources (event subtype + blocking-object class if any + leaf method + `% of total wait`).

Then numbered findings, **ordered by `% of total wait time`**, biggest first. Each section:

- **Sites** — call path, leaf and blocking-object class highlighted.
- **Event subtype** — monitor enter / monitor wait / park / sleep.
- **What it's waiting on** — one sentence based on the blocking-object class and call path.
- **The investigation / fix** — match the kind:
  - Monitor enter contention → reduce lock scope, switch to `ReentrantLock` or `StampedLock`, partition the data, use lock-free / concurrent collections.
  - Monitor wait → review timeout and predicate; check whether `notifyAll` vs `notify` is wrong.
  - Park (lock / queue / future) → look at the queue / lock holder under load; consider a different synchronisation primitive.
  - Sleep → identify why; usually replace with event-driven wait, exponential backoff with cap, or remove the poll altogether.
- **Design cost** — one line. Concurrency changes have higher design cost than typical fixes — be honest.
- **Estimated saving** — `% of total wait time`.

### Smaller items to skip

- Frames under **1 ms cumulative** wait — noise floor.
- Intentional and reasonable waits (a thread parked on a work queue for a workload-shaped reason). Note once, move on.
- Background-thread waits if the user's concern is request latency.

### Gate question

End with one closing question that lets the user redirect — for example: "The top contention is on a single monitor used by many request threads. The cleanest fix splits the data structure (real design hit, several files). Want me to start with that, or with the easier wins below it?"

---

Cite call paths by walking from the bullet back to its less-indented ancestors. Do not invent file:line numbers; the export has none.

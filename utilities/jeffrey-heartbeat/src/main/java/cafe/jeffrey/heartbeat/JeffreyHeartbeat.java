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

package cafe.jeffrey.heartbeat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reports to a Jeffrey Hub that this JVM is alive, and tells it when the JVM stopped.
 *
 * <p>This is the only writer of those files. Because it is an ordinary dependency, whether an
 * application reports at all is a build-time fact the Provisioner cannot detect — so the session
 * <em>declares</em> it, through {@code heartbeat.enabled}, and that declaration reaches both this
 * library (as {@link HeartbeatSettings#ENABLED_ENV}) and the hub, which uses it to decide whether
 * to hold the session to a heartbeat deadline at all.</p>
 *
 * <p>Typical use in a provisioned application is a single call at startup:</p>
 *
 * <pre>{@code
 * JeffreyHeartbeat.startFromEnvironment();
 * }</pre>
 *
 * <p>which reads what the Provisioner exported, begins beating, and writes the clean-exit marker
 * from a JVM shutdown hook. A container that manages its own lifecycle — Spring Boot, through
 * {@code jeffrey-heartbeat-spring-boot-starter} — calls {@link #start(HeartbeatSettings)} instead
 * and closes the instance itself, so the marker is written when the context shuts down rather than
 * when the JVM does.</p>
 *
 * <p><b>Nothing here fails an application.</b> A missing directory, an unreadable setting, a full
 * disk: each is logged once and leaves an inert instance behind. Liveness reporting is Jeffrey's
 * concern, and an application that cannot report it should still run.</p>
 */
public final class JeffreyHeartbeat implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyHeartbeat.class);

    private static final String THREAD_NAME = "jeffrey-heartbeat";
    private static final String SHUTDOWN_THREAD_NAME = "jeffrey-heartbeat-shutdown";

    /**
     * How long {@link #close()} waits for a beat that is already writing. Bounded because this
     * runs on the shutdown path: a slow volume must delay an application's exit by a moment, not
     * hold it open. Well under the beat interval, since a beat is one small file and a rename.
     */
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(2);

    private final HeartbeatWriter writer;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();

    private JeffreyHeartbeat(HeartbeatWriter writer, Clock clock, ScheduledExecutorService scheduler) {
        this.writer = writer;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    /**
     * Starts beating with settings read from the environment and registers a JVM shutdown hook to
     * write the clean-exit marker. The call for an application with no lifecycle of its own.
     *
     * @return the running instance, so a caller that does have a lifecycle can still close it early
     */
    public static JeffreyHeartbeat startFromEnvironment() {
        JeffreyHeartbeat heartbeat = start(HeartbeatSettings.fromEnvironment());
        Runtime.getRuntime().addShutdownHook(new Thread(heartbeat::close, SHUTDOWN_THREAD_NAME));
        return heartbeat;
    }

    /**
     * Starts beating with the given settings. The caller owns the returned instance and is
     * responsible for closing it, which is what writes the clean-exit marker.
     *
     * @return a running instance, or an inert one when the settings name nowhere to write, say not
     * to, or name a directory that cannot be created — never {@code null}
     */
    public static JeffreyHeartbeat start(HeartbeatSettings settings) {
        return start(settings, Clock.systemUTC());
    }

    /** As {@link #start(HeartbeatSettings)}, with the clock the timestamps come from. */
    public static JeffreyHeartbeat start(HeartbeatSettings settings, Clock clock) {
        if (!settings.writable()) {
            LOG.debug("Jeffrey heartbeat not started: enabled={} directory={}",
                    settings.enabled(), settings.directory());
            return inert();
        }

        Path directory = settings.directory();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            LOG.warn("Jeffrey heartbeat directory cannot be created, liveness will not be "
                    + "reported: directory={}", directory, e);
            return inert();
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            // Daemon: reporting liveness must never be the reason a JVM stays up
            thread.setDaemon(true);
            return thread;
        });

        JeffreyHeartbeat heartbeat =
                new JeffreyHeartbeat(new HeartbeatWriter(directory), clock, scheduler);
        long intervalMillis = settings.interval().toMillis();
        // Zero initial delay: the first beat is what tells the hub this session ever started, and
        // a hub that sees a declared producer write nothing eventually calls the session finished
        scheduler.scheduleAtFixedRate(heartbeat::beat, 0, intervalMillis, TimeUnit.MILLISECONDS);

        LOG.info("Jeffrey heartbeat started: directory={} interval={}",
                directory, settings.interval());
        return heartbeat;
    }

    /** Whether this instance is actually reporting — false once it is inert or closed. */
    public boolean running() {
        return scheduler != null && !closed.get();
    }

    /**
     * Stops beating and writes the clean-exit marker, which is what lets the hub finish the session
     * at once instead of waiting for the heartbeat to go stale. Closing twice is a no-op: the
     * marker is written by whichever call wins, and a second one would only move its timestamp.
     *
     * <p>A beat already in flight is <b>waited for</b> rather than interrupted. Both files are
     * written through a scratch file and a rename, and the two writers share the scratch names,
     * so tearing a beat down mid-write is how a rename fails, a temporary file is left behind, or
     * a beat lands after the marker it is supposed to precede.</p>
     */
    @Override
    public void close() {
        if (scheduler == null || !closed.compareAndSet(false, true)) {
            return;
        }
        awaitLastBeat();
        try {
            writer.finish(clock.millis());
        } catch (IOException | RuntimeException e) {
            // The session still finishes, from the last heartbeat, a threshold later
            LOG.warn("Jeffrey clean-exit marker could not be written", e);
        }
        writer.discardTemporaryFiles();
    }

    /**
     * Refuses new beats and gives the running one {@link #SHUTDOWN_GRACE} to finish, forcing it
     * only if it overruns — at which point a stuck volume is the problem and the marker matters
     * more than the beat.
     */
    private void awaitLastBeat() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.debug("Jeffrey heartbeat did not stop within its grace period: grace={}", SHUTDOWN_GRACE);
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    private void beat() {
        try {
            writer.beat(clock.millis());
        } catch (IOException | RuntimeException e) {
            // Logged at debug: a volume that is briefly unwritable would otherwise fill the
            // application's log at the heartbeat interval, and the next beat recovers on its own
            LOG.debug("Jeffrey heartbeat could not be written", e);
        }
    }

    private static JeffreyHeartbeat inert() {
        return new JeffreyHeartbeat(null, null, null);
    }
}

package cafe.jeffrey.microscope.core.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.io.IOException;

public class ApplicationStartedListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationStartedListener.class);

    /**
     * When {@code true}, the browser is opened automatically once the server is ready. Defaults to
     * {@code false}; only the packaged macOS .app (the DMG distribution) sets it {@code true} via a
     * jpackage {@code --java-options} entry. Plain {@code java -jar} / container / server runs leave it
     * {@code false} and are unaffected.
     */
    private static final String OPEN_BROWSER_PROPERTY = "jeffrey.microscope.open-browser";

    private static final String MAC_OPEN_COMMAND = "open";

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext ctx) {
            String url = "http://localhost:" + ctx.getWebServer().getPort();
            LOG.info("Jeffrey Microscope started: {}", url);

            Environment environment = ctx.getEnvironment();
            if (environment.getProperty(OPEN_BROWSER_PROPERTY, Boolean.class, false)) {
                openBrowser(url);
            }
        }
    }

    /**
     * Opens the given URL in the user's default browser via the macOS {@code open} command. Only the
     * packaged macOS .app sets {@link #OPEN_BROWSER_PROPERTY}, so double-clicking the app brings up the
     * UI without the user having to know the local server address.
     */
    private static void openBrowser(String url) {
        try {
            new ProcessBuilder(MAC_OPEN_COMMAND, url).start();
        } catch (IOException e) {
            LOG.warn("Failed to open browser automatically, open it manually: url={}", url, e);
        }
    }
}

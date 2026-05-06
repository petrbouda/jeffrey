package cafe.jeffrey.microscope.core.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;

public class ApplicationStartedListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationStartedListener.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext ctx) {
            LOG.info("Jeffrey Microscope started: http://localhost:{}", ctx.getWebServer().getPort());
        }
    }
}

package pbouda.jeffrey.appinitializer;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import pbouda.jeffrey.common.model.ProfilerInfo;
import pbouda.jeffrey.provider.api.repository.ProfilerRepository;

public class ProfilerInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final ProfilerRepository profilerRepository;

    public ProfilerInitializer(ProfilerRepository profilerRepository) {
        this.profilerRepository = profilerRepository;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();

        boolean projectSynchronizerCreate = environment.getProperty(
                "jeffrey.profiler.global-settings.create-if-not-exists", Boolean.class, false);
        String globalCommand = environment.getProperty(
                "jeffrey.profiler.global-settings.command", String.class, "");

        if (projectSynchronizerCreate && !globalCommand.isBlank()) {
            profilerRepository.upsertSettings(new ProfilerInfo(null, null, globalCommand));
        }
    }
}

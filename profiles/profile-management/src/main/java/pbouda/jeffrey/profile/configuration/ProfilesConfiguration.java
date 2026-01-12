package pbouda.jeffrey.profile.configuration;

import org.springframework.context.annotation.Import;
import pbouda.jeffrey.profile.ai.config.AiAssistantConfiguration;

@Import({
        AiAssistantConfiguration.class,
        ProfileFactoriesConfiguration.class,
        ProfileCustomFactoriesConfiguration.class
})
public class ProfilesConfiguration {
}

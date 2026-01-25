package pbouda.jeffrey.profile.configuration;

import org.springframework.context.annotation.Import;
import pbouda.jeffrey.profile.ai.config.AiAssistantConfiguration;
import pbouda.jeffrey.profile.ai.mcp.config.DuckDbMcpConfiguration;

@Import({
        AiAssistantConfiguration.class,
        DuckDbMcpConfiguration.class,
        ProfileFactoriesConfiguration.class,
        ProfileCustomFactoriesConfiguration.class
})
public class ProfilesConfiguration {
}

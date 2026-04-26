package cafe.jeffrey.profile.configuration;

import org.springframework.context.annotation.Import;
import cafe.jeffrey.profile.ai.config.AiAssistantConfiguration;
import cafe.jeffrey.profile.ai.config.AiChatModelConfiguration;
import cafe.jeffrey.profile.ai.heapmcp.config.HeapDumpMcpConfiguration;
import cafe.jeffrey.profile.ai.mcp.config.DuckDbMcpConfiguration;

@Import({
        AiChatModelConfiguration.class,
        AiAssistantConfiguration.class,
        DuckDbMcpConfiguration.class,
        HeapDumpMcpConfiguration.class,
        ProfileFactoriesConfiguration.class,
        ProfileCustomFactoriesConfiguration.class
})
public class ProfilesConfiguration {
}

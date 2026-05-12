package cafe.jeffrey.profile.configuration;

import org.springframework.context.annotation.Import;
import cafe.jeffrey.profile.ai.oql.config.AiAssistantConfiguration;
import cafe.jeffrey.profile.ai.config.AiChatModelConfiguration;
import cafe.jeffrey.profile.ai.duckdb.heapdump.config.HeapDumpMcpConfiguration;
import cafe.jeffrey.profile.ai.duckdb.jfr.config.DuckDbMcpConfiguration;
import cafe.jeffrey.profile.heapdump.oql.config.OqlEngineConfiguration;

@Import({
        AiChatModelConfiguration.class,
        AiAssistantConfiguration.class,
        DuckDbMcpConfiguration.class,
        HeapDumpMcpConfiguration.class,
        OqlEngineConfiguration.class,
        ProfileFactoriesConfiguration.class,
        ProfileCustomFactoriesConfiguration.class
})
public class ProfilesConfiguration {
}

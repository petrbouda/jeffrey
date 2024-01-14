package pbouda.jeffrey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.flamegraph.FlamegraphGenerator;
import pbouda.jeffrey.flamegraph.FlamegraphGeneratorImpl;
import pbouda.jeffrey.generator.heatmap.api.D3HeatmapGenerator;
import pbouda.jeffrey.repository.FlamegraphRepository;
import pbouda.jeffrey.repository.WorkingDirFlamegraphRepository;
import pbouda.jeffrey.service.HeatmapDataManager;

@Configuration
public class AppConfiguration {

    @Bean
    public FlamegraphRepository flamegraphRepository() {
        return new WorkingDirFlamegraphRepository();
    }

    @Bean
    public FlamegraphGenerator flamegraphGenerator() {
        return new FlamegraphGeneratorImpl();
    }

    @Bean
    public HeatmapDataManager heatmapDataManager() {
        return new HeatmapDataManager(new D3HeatmapGenerator());
    }
}

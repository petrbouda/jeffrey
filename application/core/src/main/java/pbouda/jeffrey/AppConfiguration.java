package pbouda.jeffrey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import pbouda.jeffrey.flamegraph.FlamegraphGenerator;
import pbouda.jeffrey.flamegraph.FlamegraphGeneratorImpl;
import pbouda.jeffrey.repository.FlamegraphRepository;
import pbouda.jeffrey.repository.WorkingDirFlamegraphRepository;
import pbouda.jeffrey.service.Context;

@Configuration
public class AppConfiguration implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedMethods("*");
    }

    @Bean
    public FlamegraphRepository flamegraphRepository() {
        return new WorkingDirFlamegraphRepository();
    }

    @Bean
    public FlamegraphGenerator flamegraphGenerator() {
        return new FlamegraphGeneratorImpl();
    }

    @Bean
    public Context context() {
        return new Context();
    }
}

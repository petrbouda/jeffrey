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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.streaming.LiveStreamingManager;
import cafe.jeffrey.hub.core.streaming.ReplayStreamingManager;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventReader;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Guards the Spring gRPC migration wiring. Spring gRPC's auto-configuration registers every
 * {@link BindableService} bean with the server and applies every {@link GlobalServerInterceptor}
 * bean to all services. This test verifies the contract this project is responsible for: that
 * {@link GrpcServerConfiguration} declares exactly the eight Jeffrey services as
 * {@code BindableService} beans, and that the JFR interceptor is declared as a global interceptor.
 * If a service {@code @Bean} is dropped or the interceptor stops being global, this fails.
 */
class GrpcServerConfigurationTest {

    private static final Set<Class<?>> EXPECTED_SERVICES = Set.of(
            WorkspaceGrpcService.class,
            ProjectGrpcService.class,
            InstanceGrpcService.class,
            ProfilerSettingsGrpcService.class,
            RepositoryGrpcService.class,
            RecordingDownloadGrpcService.class,
            WorkspaceEventsGrpcService.class,
            EventStreamingGrpcService.class);

    @Test
    void registersAllServicesAsBindableServiceBeans() {
        try (var ctx = new AnnotationConfigApplicationContext(MockDependencies.class, GrpcServerConfiguration.class)) {
            Map<String, BindableService> services = ctx.getBeansOfType(BindableService.class);

            assertEquals(EXPECTED_SERVICES.size(), services.size(),
                    "Expected exactly one BindableService bean per Jeffrey gRPC service");

            Set<Class<?>> registeredTypes = services.values().stream()
                    .map(Object::getClass)
                    .collect(Collectors.toSet());
            assertTrue(registeredTypes.containsAll(EXPECTED_SERVICES),
                    "Missing service beans: registered=" + registeredTypes);
        }
    }

    @Test
    void jfrInterceptorIsRegisteredAsGlobalInterceptor() throws NoSuchMethodException {
        try (var ctx = new AnnotationConfigApplicationContext(MockDependencies.class, GrpcServerConfiguration.class)) {
            ServerInterceptor interceptor = ctx.getBean(ServerInterceptor.class);
            assertInstanceOf(JfrGrpcServerInterceptor.class, interceptor);
        }

        Method beanMethod = GrpcServerConfiguration.class.getMethod("jfrGrpcServerInterceptor");
        assertTrue(beanMethod.isAnnotationPresent(GlobalServerInterceptor.class),
                "jfrGrpcServerInterceptor must be annotated @GlobalServerInterceptor so Spring gRPC applies it to all services");
    }

    @Configuration
    static class MockDependencies {

        @Bean
        public Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        public DefaultWorkspaceProperties defaultWorkspaceProperties() {
            return new DefaultWorkspaceProperties("$default", "$default");
        }

        @Bean
        public WorkspacesManager workspacesManager() {
            return mock(WorkspacesManager.class);
        }

        @Bean
        public WorkspaceEventReader workspaceEventReader() {
            return mock(WorkspaceEventReader.class);
        }

        @Bean
        public HubPlatformRepositories platformRepositories() {
            return mock(HubPlatformRepositories.class);
        }

        @Bean
        public ProjectManager.Factory projectManagerFactory() {
            return mock(ProjectManager.Factory.class);
        }

        @Bean
        public RepositoryManager.Factory repositoryManagerFactory() {
            return mock(RepositoryManager.Factory.class);
        }

        @Bean
        public HubJeffreyDirs jeffreyDirs() {
            return mock(HubJeffreyDirs.class);
        }

        @Bean
        public LiveStreamingManager liveStreamingManager() {
            return mock(LiveStreamingManager.class);
        }

        @Bean
        public ReplayStreamingManager replayStreamingManager() {
            return mock(ReplayStreamingManager.class);
        }

        @Bean
        public RepositoryStorage.Factory repositoryStorageFactory() {
            return mock(RepositoryStorage.Factory.class);
        }
    }
}

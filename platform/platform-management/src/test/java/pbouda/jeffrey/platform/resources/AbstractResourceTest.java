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

package pbouda.jeffrey.platform.resources;

import jakarta.ws.rs.core.Application;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.glassfish.jersey.test.grizzly.GrizzlyTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Base class for REST resource integration tests using Jersey Test Framework.
 * <p>
 * Provides common configuration including JSON serialization (Jackson) and
 * all exception mappers registered in the real application. Subclasses only
 * need to register their specific resources and mock dependencies.
 * <p>
 * Uses the non-servlet Grizzly HTTP container with an empty Spring context
 * to avoid loading the real application context. Resources are plain POJOs
 * with constructor DI, so no Spring wiring is needed for tests.
 */
public abstract class AbstractResourceTest extends JerseyTest {

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new GrizzlyTestContainerFactory();
    }

    @Override
    protected Application configure() {
        forceSet(TestProperties.CONTAINER_PORT, "0");

        ResourceConfig config = new ResourceConfig();
        // Provide an empty Spring context to prevent jersey-spring6 from loading applicationContext.xml
        config.property("contextConfig", new AnnotationConfigApplicationContext());
        config.register(JacksonFeature.class);
        config.register(ExceptionMappers.JeffreyExceptionMapper.class);
        config.register(ExceptionMappers.IllegalArgumentExceptionMapper.class);
        config.register(ExceptionMappers.WebApplicationExceptionMapper.class);
        config.register(ExceptionMappers.GenericExceptionMapper.class);
        configureResources(config);
        return config;
    }

    /**
     * Register resource classes and any additional providers needed for the test.
     *
     * @param config the Jersey resource configuration to register resources with
     */
    protected abstract void configureResources(ResourceConfig config);
}

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

package cafe.jeffrey.jfr.events.spring.boot;

import cafe.jeffrey.jfr.events.jdbc.datasource.TracingDataSource;
import cafe.jeffrey.jfr.events.servlet.HttpExchangeFilter;
import cafe.jeffrey.jfr.events.servlet.HttpExchangeSettings;
import cafe.jeffrey.jfr.events.servlet.HttpRequestNaming;
import cafe.jeffrey.jfr.events.spring.JeffreyTracingConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import cafe.jeffrey.jfr.events.mybatis.JeffreyMyBatisInterceptor;
import cafe.jeffrey.jfr.events.mybatis.MyBatisStatementSettings;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import javax.sql.DataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the contract of the three-module split: the plain configuration is inert, the starter wires
 * everything, and having both does not register anything twice.
 */
class JeffreyTracingAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JeffreyTracingAutoConfiguration.class));

    @Nested
    @DisplayName("With the starter")
    class WithStarter {

        @Test
        @DisplayName("the filter, its registration, the naming and the settings are all wired")
        void wiresEverything() {
            runner.run(context -> {
                assertThat(context).hasSingleBean(HttpExchangeFilter.class);
                assertThat(context).hasSingleBean(HttpRequestNaming.class);
                assertThat(context).hasSingleBean(HttpExchangeSettings.class);
                assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            });
        }

        @Test
        @DisplayName("the filter runs first in the chain and sees every request by default")
        void registersFirstForEveryRequest() {
            runner.run(context -> {
                FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
                assertThat(registration.getUrlPatterns()).containsExactly("/*");
                assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
            });
        }

        @Test
        @DisplayName("parameter capture is off unless asked for, because recordings get shared")
        void capturesNoParametersByDefault() {
            runner.run(context -> {
                HttpExchangeSettings settings = context.getBean(HttpExchangeSettings.class);
                assertThat(settings.captureQueryParams()).isFalse();
                assertThat(settings.capturePathParams()).isFalse();
            });
        }

        @Test
        @DisplayName("properties bind onto the registration and the settings")
        void propertiesBind() {
            runner.withPropertyValues(
                            "jeffrey.tracing.url-patterns=/api/*",
                            "jeffrey.tracing.order=10",
                            "jeffrey.tracing.capture-query-params=true",
                            "jeffrey.tracing.capture-path-params=true")
                    .run(context -> {
                        FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
                        assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
                        assertThat(registration.getOrder()).isEqualTo(10);

                        HttpExchangeSettings settings = context.getBean(HttpExchangeSettings.class);
                        assertThat(settings.captureQueryParams()).isTrue();
                        assertThat(settings.capturePathParams()).isTrue();
                    });
        }

        @Test
        @DisplayName("enabled=false turns it off without removing the dependency")
        void canBeDisabled() {
            runner.withPropertyValues("jeffrey.tracing.enabled=false")
                    .run(context -> assertThat(context).doesNotHaveBean(HttpExchangeFilter.class));
        }
    }

    @Nested
    @DisplayName("JDBC and connection pools")
    class DataSources {

        private final WebApplicationContextRunner jdbcRunner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JeffreyTracingAutoConfiguration.class, JeffreyJdbcTracingAutoConfiguration.class))
                .withUserConfiguration(PlainDataSource.class);

        @Test
        @DisplayName("an application's own DataSource is wrapped without it doing anything")
        void wrapsTheDataSource() {
            jdbcRunner.run(context ->
                    assertThat(context.getBean(DataSource.class)).isInstanceOf(TracingDataSource.class));
        }

        @Test
        @DisplayName("jdbc-enabled=false leaves the DataSource untouched")
        void jdbcCanBeDisabled() {
            jdbcRunner.withPropertyValues("jeffrey.tracing.jdbc-enabled=false")
                    .run(context ->
                            assertThat(context.getBean(DataSource.class)).isNotInstanceOf(TracingDataSource.class));
        }

        @Test
        @DisplayName("a DataSource is not wrapped twice when the context already has a traced one")
        void doesNotWrapTwice() {
            jdbcRunner.run(context -> {
                DataSource wrapped = context.getBean(DataSource.class);
                assertThat(((TracingDataSource) wrapped).delegate()).isNotInstanceOf(TracingDataSource.class);
            });
        }

        /** Nested inside this class on purpose: at test-class level the Boot slice would scan it. */
        @Configuration(proxyBeanMethods = false)
        static class PlainDataSource {

            @Bean
            DataSource dataSource() {
                return Mockito.mock(DataSource.class);
            }
        }
    }

    /**
     * MyBatis names statements better than a {@code DataSource} proxy can, so it takes over wherever
     * the application actually uses MyBatis — and that hand-over is what keeps a mapper call out of
     * the dashboard twice. What marks "actually uses" is a {@code SqlSessionFactory}, not the jar.
     */
    @Nested
    @DisplayName("MyBatis")
    class MyBatis {

        private final WebApplicationContextRunner mybatisRunner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JeffreyMyBatisTracingAutoConfiguration.class, JeffreyJdbcTracingAutoConfiguration.class))
                .withUserConfiguration(DataSources.PlainDataSource.class, MyBatisInUse.class);

        @Test
        @DisplayName("a SqlSessionFactory is enough — the interceptor bean registers without being asked")
        void registersTheInterceptorForAnApplicationUsingMyBatis() {
            mybatisRunner.run(context -> {
                assertThat(context).hasSingleBean(JeffreyMyBatisInterceptor.class);
                assertThat(context.getBean(JeffreyMyBatisInterceptor.class).settings())
                        .isEqualTo(MyBatisStatementSettings.defaults());
            });
        }

        @Test
        @DisplayName("parameter capture is on by default, because it is what makes a slow statement readable")
        void capturesParametersByDefault() {
            mybatisRunner.run(context ->
                    assertThat(context.getBean(MyBatisStatementSettings.class).captureParameters()).isTrue());
        }

        @Test
        @DisplayName("properties reach the interceptor the application actually gets")
        void propertiesBind() {
            mybatisRunner.withPropertyValues(
                            "jeffrey.tracing.mybatis-capture-parameters=false",
                            "jeffrey.tracing.mybatis-max-parameter-length=16")
                    .run(context -> assertThat(context.getBean(JeffreyMyBatisInterceptor.class).settings())
                            .isEqualTo(new MyBatisStatementSettings(false, 16)));
        }

        @Test
        @DisplayName("the DataSource is left alone while the interceptor is recording, so nothing is counted twice")
        void theDataSourceWrapperBacksOff() {
            mybatisRunner.run(context ->
                    assertThat(context.getBean(DataSource.class)).isNotInstanceOf(TracingDataSource.class));
        }

        @Test
        @DisplayName("having MyBatis on the classpath is not by itself a reason to register anything")
        void classpathWithoutASessionFactoryRegistersNothing() {
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            JeffreyMyBatisTracingAutoConfiguration.class, JeffreyJdbcTracingAutoConfiguration.class))
                    .withUserConfiguration(DataSources.PlainDataSource.class)
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(JeffreyMyBatisInterceptor.class);
                        assertThat(context.getBean(DataSource.class))
                                .as("a transitive MyBatis jar must not stop statements being recorded")
                                .isInstanceOf(TracingDataSource.class);
                    });
        }

        @Test
        @DisplayName("mybatis-enabled=false hands back to the DataSource wrapper, which sees JdbcTemplate too")
        void canBeDisabledInFavourOfTheWrapper() {
            mybatisRunner.withPropertyValues("jeffrey.tracing.mybatis-enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(JeffreyMyBatisInterceptor.class);
                        assertThat(context.getBean(DataSource.class)).isInstanceOf(TracingDataSource.class);
                    });
        }

        /** Nested for the same reason as {@link DataSources.PlainDataSource}. */
        @Configuration(proxyBeanMethods = false)
        static class MyBatisInUse {

            @Bean
            SqlSessionFactory sqlSessionFactory() {
                return Mockito.mock(SqlSessionFactory.class);
            }
        }
    }

    @Nested
    @DisplayName("Without the starter")
    class WithoutStarter {

        @Test
        @DisplayName("the plain configuration registers nothing on its own")
        void plainConfigurationIsInert() {
            new WebApplicationContextRunner()
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(HttpExchangeFilter.class);
                        assertThat(context).doesNotHaveBean(HttpRequestNaming.class);
                        assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                    });
        }

        @Test
        @DisplayName("jeffrey-tracing-spring ships no auto-configuration entry, which is what keeps it opt-in")
        void springModuleContributesNoAutoConfiguration() throws IOException {
            List<String> entries = autoConfigurationEntries();

            assertThat(entries)
                    .as("only the starter may contribute an auto-configuration for Jeffrey")
                    .noneMatch(entry -> entry.equals(JeffreyTracingConfiguration.class.getName()));
            assertThat(entries).contains(JeffreyTracingAutoConfiguration.class.getName());
        }

        private static List<String> autoConfigurationEntries() throws IOException {
            List<URL> resources = Collections.list(
                    JeffreyTracingAutoConfigurationTest.class.getClassLoader().getResources(IMPORTS_RESOURCE));

            return resources.stream()
                    .flatMap(JeffreyTracingAutoConfigurationTest::readLines)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        }
    }

    @Nested
    @DisplayName("With the starter and a manual @Import of the plain configuration")
    class WithBoth {

        @Test
        @DisplayName("nothing is registered twice, so the request is not measured twice")
        void doesNotDoubleRegister() {
            runner.withUserConfiguration(ManualImport.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(HttpExchangeFilter.class);
                        assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                        assertThat(context).hasSingleBean(HttpExchangeSettings.class);
                        assertThat(context).hasSingleBean(HttpRequestNaming.class);
                    });
        }

        @Configuration(proxyBeanMethods = false)
        @Import(JeffreyTracingConfiguration.class)
        static class ManualImport {
        }
    }

    private static Stream<String> readLines(URL resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList().stream();
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + resource, e);
        }
    }
}

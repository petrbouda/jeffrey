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

package pbouda.jeffrey.local.core.web;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring MVC controller test.
 *
 * <p>Bundles JUnit Jupiter + Mockito wiring so individual test classes stay
 * thin: just declare the {@code @Mock}s the controller needs and call
 * {@link MockMvcSupport#mockMvcFor(Object...)} (static import) inside each
 * test method.
 *
 * <p>This intentionally does <strong>not</strong> bring in
 * {@code @SpringBootTest} or any application-context bootstrapping —
 * controller tests run against {@code MockMvcBuilders.standaloneSetup} and
 * stay sub-100 ms per test. Spring Boot 4 has no {@code @WebMvcTest} slice
 * annotation any more, so this is the recommended fast path for thin
 * controller tests in this project.
 *
 * <pre>{@code
 * @ControllerTest
 * class WorkspacesControllerTest {
 *
 *     @Mock
 *     WorkspacesManager workspacesManager;
 *
 *     @Test
 *     void getsAllWorkspaces() throws Exception {
 *         mockMvcFor(new WorkspacesController(workspacesManager))
 *                 .perform(get("/api/internal/workspaces"))
 *                 .andExpect(status().isOk());
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockitoExtension.class)
public @interface ControllerTest {
}

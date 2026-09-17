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

package cafe.jeffrey.jib.maven;

import cafe.jeffrey.jib.payload.ArtifactCoordinates;
import cafe.jeffrey.jib.payload.PayloadResolutionException;
import cafe.jeffrey.jib.payload.PayloadResolver;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

/**
 * Resolves payload artifacts through the running Maven build's own Aether session, so payloads obey
 * the project's repositories, mirrors, proxies and local cache exactly like any other dependency.
 *
 * <p>The {@link RepositorySystem} is looked up from the session's Plexus container rather than
 * injected: JIB loads plugin extensions through {@link java.util.ServiceLoader}, which requires a
 * no-argument constructor and therefore rules out dependency injection.
 */
final class AetherPayloadResolver implements PayloadResolver {

    private static final String RESOLUTION_CONTEXT = "jeffrey-jib-payload";
    private static final String GET_CONTAINER_METHOD = "getContainer";

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    AetherPayloadResolver(
            RepositorySystem repositorySystem,
            RepositorySystemSession session,
            List<RemoteRepository> repositories) {

        this.repositorySystem = repositorySystem;
        this.session = session;
        this.repositories = repositories;
    }

    static AetherPayloadResolver from(
            MavenData mavenData,
            Class<? extends JibPluginExtension> extensionClass) throws JibPluginExtensionException {

        if (mavenData == null || mavenData.getMavenSession() == null || mavenData.getMavenProject() == null) {
            throw new JibPluginExtensionException(
                    extensionClass,
                    "jeffrey-jib: no Maven session available, so the Jeffrey payloads cannot be resolved.");
        }
        MavenSession mavenSession = mavenData.getMavenSession();
        return new AetherPayloadResolver(
                lookupRepositorySystem(mavenSession, extensionClass),
                mavenSession.getRepositorySession(),
                mavenData.getMavenProject().getRemoteProjectRepositories());
    }

    /**
     * {@code MavenSession.getContainer()} is deprecated in Maven 3.9 and gone from the Maven 4
     * session API, so it is reached reflectively: a Maven this extension cannot serve must say so
     * rather than die on a {@link NoSuchMethodError} from deep inside the build.
     */
    private static RepositorySystem lookupRepositorySystem(
            MavenSession mavenSession,
            Class<? extends JibPluginExtension> extensionClass) throws JibPluginExtensionException {

        try {
            Method getContainer = mavenSession.getClass().getMethod(GET_CONTAINER_METHOD);
            Object container = getContainer.invoke(mavenSession);
            Method lookup = container.getClass().getMethod("lookup", Class.class);
            return (RepositorySystem) lookup.invoke(container, RepositorySystem.class);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new JibPluginExtensionException(
                    extensionClass,
                    "jeffrey-jib: could not obtain Maven's artifact resolver from this Maven version ("
                            + mavenSession.getClass().getName() + "). Set provisionerPath and "
                            + "profilerPath to binaries the base image already provides to build "
                            + "without payload resolution.",
                    e);
        }
    }

    @Override
    public Path resolve(ArtifactCoordinates coordinates) throws PayloadResolutionException {
        Artifact artifact = new DefaultArtifact(
                coordinates.groupId(),
                coordinates.artifactId(),
                coordinates.classifier(),
                coordinates.extension(),
                coordinates.version());

        ArtifactRequest request = new ArtifactRequest(artifact, repositories, RESOLUTION_CONTEXT);
        try {
            return repositorySystem.resolveArtifact(session, request).getArtifact().getFile().toPath();
        } catch (ArtifactResolutionException e) {
            throw new PayloadResolutionException(
                    coordinates, "Maven could not resolve it from " + repositories, e);
        }
    }
}

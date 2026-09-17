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
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.RepositorySystem;
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
 * <p>Everything Maven-specific is reached on the first {@link #resolve} call, not when the
 * resolver is created. A build that bakes nothing — {@code enabled=false}, or both binaries
 * supplied by the base image — must never need Maven's artifact resolver, and on a Maven that
 * cannot provide one that is precisely the escape hatch the error message below recommends.
 *
 * <p>The {@link RepositorySystem} is looked up from the session's Plexus container rather than
 * injected: JIB loads plugin extensions through {@link java.util.ServiceLoader}, which requires a
 * no-argument constructor and therefore rules out dependency injection.
 */
final class AetherPayloadResolver implements PayloadResolver {

    private static final String RESOLUTION_CONTEXT = "jeffrey-jib-payload";
    private static final String GET_CONTAINER_METHOD = "getContainer";
    private static final String LOOKUP_METHOD = "lookup";

    private final MavenData mavenData;

    /** Looked up once, on the first payload; null until then. */
    private RepositorySystem repositorySystem;

    AetherPayloadResolver(MavenData mavenData) {
        this.mavenData = mavenData;
    }

    @Override
    public Path resolve(ArtifactCoordinates coordinates) throws PayloadResolutionException {
        MavenSession session = session(coordinates);
        List<RemoteRepository> repositories = mavenData.getMavenProject().getRemoteProjectRepositories();

        Artifact artifact = new DefaultArtifact(
                coordinates.groupId(),
                coordinates.artifactId(),
                coordinates.classifier(),
                coordinates.extension(),
                coordinates.version());
        ArtifactRequest request = new ArtifactRequest(artifact, repositories, RESOLUTION_CONTEXT);
        try {
            return repositorySystem(session, coordinates)
                    .resolveArtifact(session.getRepositorySession(), request)
                    .getArtifact()
                    .getFile()
                    .toPath();
        } catch (ArtifactResolutionException e) {
            throw new PayloadResolutionException(
                    coordinates, "Maven could not resolve it from " + repositories, e);
        }
    }

    private MavenSession session(ArtifactCoordinates coordinates) throws PayloadResolutionException {
        if (mavenData == null || mavenData.getMavenSession() == null || mavenData.getMavenProject() == null) {
            throw new PayloadResolutionException(
                    coordinates, "no Maven session is available to resolve it through");
        }
        return mavenData.getMavenSession();
    }

    private RepositorySystem repositorySystem(MavenSession session, ArtifactCoordinates coordinates)
            throws PayloadResolutionException {

        if (repositorySystem == null) {
            repositorySystem = lookupRepositorySystem(session, coordinates);
        }
        return repositorySystem;
    }

    /**
     * {@code MavenSession.getContainer()} is deprecated in Maven 3.9 and gone from the Maven 4
     * session API, so it is reached reflectively: a Maven this extension cannot serve must say so
     * rather than die on a {@link NoSuchMethodError} from deep inside the build.
     */
    private static RepositorySystem lookupRepositorySystem(
            MavenSession session, ArtifactCoordinates coordinates) throws PayloadResolutionException {

        try {
            Method getContainer = session.getClass().getMethod(GET_CONTAINER_METHOD);
            Object container = getContainer.invoke(session);
            Method lookup = container.getClass().getMethod(LOOKUP_METHOD, Class.class);
            return (RepositorySystem) lookup.invoke(container, RepositorySystem.class);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new PayloadResolutionException(
                    coordinates,
                    "this Maven (" + session.getClass().getName() + ") does not expose its artifact "
                            + "resolver to the extension. Mirror the jeffrey-jib-payload-* artifacts into a "
                            + "repository this build can reach, or set enabled=false to build an image "
                            + "without Jeffrey profiling",
                    e);
        }
    }
}

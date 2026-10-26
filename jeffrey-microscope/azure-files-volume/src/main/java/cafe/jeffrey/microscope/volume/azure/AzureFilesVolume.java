/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.volume.azure;

import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import com.azure.core.util.Context;
import com.azure.storage.file.share.FileSmbProperties;
import com.azure.storage.file.share.ShareClient;
import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareFileClient;
import com.azure.storage.file.share.models.ShareDirectoryProperties;
import com.azure.storage.file.share.models.ShareErrorCode;
import com.azure.storage.file.share.models.ShareFileItem;
import com.azure.storage.file.share.models.ShareFileItemProperties;
import com.azure.storage.file.share.models.ShareFileProperties;
import com.azure.storage.file.share.models.ShareFileRange;
import com.azure.storage.file.share.models.ShareStorageException;
import com.azure.storage.file.share.options.ShareFileRenameOptions;
import com.azure.storage.file.share.options.ShareListFilesAndDirectoriesOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The recording volume on an Azure file share, read over the Azure Files REST API — the same
 * directory tree the hub reads off its mount.
 *
 * <p>The two protocols list differently. An SMB share returns each entry with its timestamps from
 * one listing call; an NFS share returns names and sizes alone, so its entries carry no creation
 * time and a reader that needs one asks {@link #stat} for that entry. A look at one entry always
 * answers a time: the creation time where the share keeps it, its last modification otherwise.
 *
 * <p>Writes are the few the layout makes. A marker is written to a scratch file beside it and
 * renamed onto it, so a reader alongside never sees half of one; an empty flag file is created in
 * one call. The share has no recursive delete, so a directory is emptied entry by entry first.
 *
 * <p>"Not there" — a missing file, a missing parent, the other kind of entry under that name — is
 * an empty answer; any other refusal of the service (a credential it rejects, a share it cannot
 * reach) is an {@link UncheckedIOException}, never mistaken for an empty volume.
 */
public final class AzureFilesVolume implements WritableVolume {

    private static final Logger LOG = LoggerFactory.getLogger(AzureFilesVolume.class);

    private static final String SEPARATOR = "/";

    /** What the service answers for a name that holds nothing of the kind asked for. */
    private static final Set<ShareErrorCode> NOTHING_THERE = Set.of(
            ShareErrorCode.RESOURCE_NOT_FOUND,
            ShareErrorCode.PARENT_NOT_FOUND,
            ShareErrorCode.RESOURCE_TYPE_MISMATCH);

    private static final int NOT_FOUND = 404;
    private static final int RANGE_NOT_SATISFIABLE = 416;
    private static final int FORBIDDEN = 403;
    private static final String WRITE_RIGHTS_HINT =
            " (the credential lacks the create, write or delete right; declare the share read-only, or grant them)";

    private final ShareClient share;
    private final String rootPath;
    private final AzureFilesProtocol protocol;

    /**
     * @param share    the share, already signed in
     * @param rootPath the directory within the share that holds the workspaces, {@code /}-separated;
     *                 empty for the share's root
     * @param protocol the protocol the share was created for
     */
    AzureFilesVolume(ShareClient share, String rootPath, AzureFilesProtocol protocol) {
        this.share = share;
        this.rootPath = rootPath == null ? "" : rootPath;
        this.protocol = protocol;
    }

    @Override
    public List<VolumeEntry> list(VolumePath directory) {
        ShareListFilesAndDirectoriesOptions options = new ShareListFilesAndDirectoriesOptions();
        if (protocol == AzureFilesProtocol.SMB) {
            // Extended info keeps each file's size current while a writer still holds it open
            options.setIncludeTimestamps(true).setIncludeExtendedInfo(true);
        }
        List<VolumeEntry> entries = new ArrayList<>();
        try {
            for (ShareFileItem item : directoryClient(directory).listFilesAndDirectories(options, null, Context.NONE)) {
                entry(directory, item).ifPresent(entries::add);
            }
        } catch (ShareStorageException e) {
            if (isNothingThere(e)) {
                return List.of();
            }
            throw unreadable("list", directory, e);
        }
        return entries;
    }

    @Override
    public Optional<VolumeEntry> stat(VolumePath path) {
        if (path.segments().isEmpty()) {
            return directoryStat(path);
        }
        try {
            ShareFileProperties properties = fileClient(path).getProperties();
            Instant createdAt = creationTime(properties.getSmbProperties()).orElse(properties.getLastModified().toInstant());
            long size = properties.getContentLength() == null ? 0 : properties.getContentLength();
            return Optional.of(new VolumeEntry(path, VolumeEntry.Kind.FILE, size, Optional.of(createdAt)));
        } catch (ShareStorageException e) {
            if (isNothingThere(e)) {
                // Not a file by that name; a directory may still be
                return directoryStat(path);
            }
            throw unreadable("look at", path, e);
        }
    }

    @Override
    public Optional<byte[]> read(VolumePath file) {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        try {
            // One byte past the cap, so a file over it is told apart from one exactly at it
            fileClient(file).downloadWithResponse(
                    content, new ShareFileRange(0, (long) MAX_READ_BYTES), false, null, Context.NONE);
        } catch (ShareStorageException e) {
            if (isNothingThere(e)) {
                return Optional.empty();
            }
            if (e.getStatusCode() == RANGE_NOT_SATISFIABLE) {
                // A range on an empty file asks for bytes it does not have
                return Optional.of(new byte[0]);
            }
            throw unreadable("read", file, e);
        }
        return Optional.of(VolumeAccess.withinReadCap(file, content.toByteArray()));
    }

    /**
     * Streams the file in ranges as it is read, never whole into memory: a recording chunk runs to
     * hundreds of megabytes.
     */
    @Override
    public Optional<OpenedFile> open(VolumePath file) {
        ShareFileClient client = fileClient(file);
        try {
            ShareFileProperties properties = client.getProperties();
            long size = properties.getContentLength() == null ? 0 : properties.getContentLength();
            return Optional.of(new OpenedFile(file.name(), size, client.openInputStream()));
        } catch (ShareStorageException e) {
            if (isNothingThere(e)) {
                return Optional.empty();
            }
            throw unreadable("open", file, e);
        }
    }

    @Override
    public void createDirectories(VolumePath directory) {
        StringBuilder location = new StringBuilder();
        for (String element : location(directory).split(SEPARATOR)) {
            if (element.isEmpty()) {
                continue;
            }
            location.append(location.isEmpty() ? "" : SEPARATOR).append(element);
            try {
                share.getDirectoryClient(location.toString()).createIfNotExists();
            } catch (ShareStorageException e) {
                throw unwritable("create", directory, e);
            }
        }
    }

    @Override
    public void write(VolumePath file, byte[] content) {
        if (content.length == 0) {
            // Created at its final size in one call, so there is no half of it to see
            try {
                fileClient(file).create(0);
            } catch (ShareStorageException e) {
                throw unwritable("write", file, e);
            }
            return;
        }
        VolumePath scratch = file.scratchSibling();
        ShareFileClient scratchClient = fileClient(scratch);
        try {
            scratchClient.create(content.length);
            scratchClient.uploadRange(new ByteArrayInputStream(content), content.length);
            scratchClient.renameWithResponse(
                    new ShareFileRenameOptions(location(file)).setReplaceIfExists(true), null, Context.NONE);
        } catch (ShareStorageException e) {
            deleteQuietly(scratchClient, scratch);
            throw unwritable("write", file, e);
        }
    }

    @Override
    public void delete(VolumePath path) {
        Optional<VolumeEntry> entry = stat(path);
        if (entry.isEmpty()) {
            return;
        }
        try {
            if (entry.get().isDirectory()) {
                deleteTree(directoryClient(path));
            } else {
                fileClient(path).deleteIfExists();
            }
        } catch (ShareStorageException e) {
            throw unwritable("delete", path, e);
        }
    }

    /**
     * Everything under a directory, then the directory. Listed by the share's own names, not as
     * volume paths: a name the layout could never have written must not leave its directory
     * impossible to remove.
     */
    private static void deleteTree(ShareDirectoryClient directory) {
        for (ShareFileItem item : directory.listFilesAndDirectories()) {
            if (item.isDirectory()) {
                deleteTree(directory.getSubdirectoryClient(item.getName()));
            } else {
                directory.getFileClient(item.getName()).deleteIfExists();
            }
        }
        directory.deleteIfExists();
    }

    private void deleteQuietly(ShareFileClient client, VolumePath scratch) {
        try {
            client.deleteIfExists();
        } catch (ShareStorageException e) {
            LOG.warn("Cannot remove scratch file: share={} path={} status={}",
                    share.getShareName(), location(scratch), e.getStatusCode());
        }
    }

    private Optional<VolumeEntry> directoryStat(VolumePath path) {
        try {
            ShareDirectoryProperties properties = directoryClient(path).getProperties();
            Instant createdAt = creationTime(properties.getSmbProperties()).orElse(properties.getLastModified().toInstant());
            return Optional.of(new VolumeEntry(path, VolumeEntry.Kind.DIRECTORY, 0, Optional.of(createdAt)));
        } catch (ShareStorageException e) {
            if (isNothingThere(e)) {
                return Optional.empty();
            }
            throw unreadable("look at", path, e);
        }
    }

    /**
     * One listed entry, or empty for a name no volume path can carry — the layout could never have
     * written it, and one foreign file must not fail the whole listing.
     */
    private static Optional<VolumeEntry> entry(VolumePath directory, ShareFileItem item) {
        VolumePath path;
        try {
            path = directory.resolve(item.getName());
        } catch (IllegalArgumentException e) {
            LOG.debug("Leaving out an entry whose name is no plain path element: directory={} name={}",
                    directory, item.getName());
            return Optional.empty();
        }
        Optional<Instant> createdAt = Optional.ofNullable(item.getProperties())
                .map(ShareFileItemProperties::getCreatedOn)
                .map(OffsetDateTime::toInstant);
        long size = item.getFileSize() == null ? 0 : item.getFileSize();
        VolumeEntry.Kind kind = item.isDirectory() ? VolumeEntry.Kind.DIRECTORY : VolumeEntry.Kind.FILE;
        return Optional.of(new VolumeEntry(path, kind, size, createdAt));
    }

    /** The SMB creation time; an NFS share keeps none, so its entries fall back to their last change. */
    private static Optional<Instant> creationTime(FileSmbProperties smb) {
        return Optional.ofNullable(smb)
                .map(FileSmbProperties::getFileCreationTime)
                .map(OffsetDateTime::toInstant);
    }

    private ShareDirectoryClient directoryClient(VolumePath path) {
        String location = location(path);
        return location.isEmpty() ? share.getRootDirectoryClient() : share.getDirectoryClient(location);
    }

    private ShareFileClient fileClient(VolumePath path) {
        return share.getFileClient(location(path));
    }

    /** The path within the share: the root directory, then the volume path's elements. */
    private String location(VolumePath path) {
        String relative = path.toString();
        if (rootPath.isEmpty()) {
            return relative;
        }
        return relative.isEmpty() ? rootPath : rootPath + SEPARATOR + relative;
    }

    private static boolean isNothingThere(ShareStorageException e) {
        return e.getStatusCode() == NOT_FOUND || NOTHING_THERE.contains(e.getErrorCode());
    }

    private UncheckedIOException unwritable(String action, VolumePath path, ShareStorageException e) {
        String hint = e.getStatusCode() == FORBIDDEN ? WRITE_RIGHTS_HINT : "";
        return new UncheckedIOException(new IOException(
                "Cannot %s an Azure Files entry%s: share=%s path=%s status=%d error_code=%s"
                        .formatted(action, hint, share.getShareName(), location(path), e.getStatusCode(), e.getErrorCode()),
                e));
    }

    private UncheckedIOException unreadable(String action, VolumePath path, ShareStorageException e) {
        return new UncheckedIOException(new IOException(
                "Cannot %s an Azure Files entry: share=%s path=%s status=%d error_code=%s"
                        .formatted(action, share.getShareName(), location(path), e.getStatusCode(), e.getErrorCode()),
                e));
    }
}

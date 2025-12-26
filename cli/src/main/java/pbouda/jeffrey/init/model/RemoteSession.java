package pbouda.jeffrey.init.model;

public record RemoteSession(
        String sessionId,
        String projectId,
        String workspaceId,
        long createdAt,
        String finishedFile,
        String relativeSessionPath,
        String profilerSettings) {
}

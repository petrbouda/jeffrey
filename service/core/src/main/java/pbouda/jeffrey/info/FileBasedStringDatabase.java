package pbouda.jeffrey.info;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileBasedStringDatabase implements StringDatabase {

    private final Path backingFile;
    private FileChannel fileChannel;

    public FileBasedStringDatabase(Path backingFile) {
        this.backingFile = backingFile;
    }

    @Override
    public void initialize() {
        if (!Files.exists(backingFile)) {
            try {
                Files.createDirectories(backingFile.getParent());
                Files.createFile(backingFile);
            } catch (IOException e) {
                throw new RuntimeException("Cannot create a backing file: " + backingFile, e);
            }
        }

        try {
            fileChannel = FileChannel.open(backingFile);
            fileChannel.lock();
        } catch (IOException e) {
            throw new RuntimeException("Cannot open the file and acquire a lock: " + backingFile, e);
        }
    }

    @Override
    public String readContent() {
        try {
            var baos = new ByteArrayOutputStream();
            // We don't have to have the transfer operation in a loop because of the nature of the stream above
            fileChannel.transferTo(0, fileChannel.size(), Channels.newChannel(baos));
            return baos.toString();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read a content from the file: " + backingFile, e);
        }
    }

    @Override
    public void writeContent(String content) {
        byte[] contentInBytes = content.getBytes();
        var bais = new ByteArrayInputStream(contentInBytes);

        try {
            fileChannel.truncate(0);
            // We don't have to have the transfer operation in a loop because of the nature of the stream above
            fileChannel.transferFrom(Channels.newChannel(bais), 0, contentInBytes.length);
        } catch (IOException e) {
            throw new RuntimeException("Cannot write a content to the file: " + backingFile, e);
        }
    }

    @Override
    public void close() {
        if (fileChannel != null) {
            try {
                fileChannel.close();
            } catch (IOException ex) {
                throw new RuntimeException("Cannot close the file channel: path=" + backingFile, ex);
            }
        }
    }
}

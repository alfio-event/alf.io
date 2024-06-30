package alfio.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

@Component
public class FileBlobCacheManager {

    private static final Logger log = LoggerFactory.getLogger(FileBlobCacheManager.class);

    private Path getBlobDir(String section) {
        return Paths.get(System.getProperty("java.io.tmpdir"), "alfio-blob").resolve(section);
    }

    private void checkPath(Path resourcePath) {
        var parentPath = Paths.get(System.getProperty("java.io.tmpdir")).normalize();
        var childPath = resourcePath.normalize();
        Assert.isTrue(childPath.startsWith(parentPath), () -> "Resource path " + childPath + "must be inside the blob path " + parentPath);
    }

    public File getFile(String section, String id, Supplier<File> supplier) {
        var resourcePath = getBlobDir(section).resolve(id);
        checkPath(resourcePath);
        if (Files.exists(resourcePath)) {
            return resourcePath.toFile();
        }
        log.info("Cache not hit for file {}", id);
        var tmpFile = supplier.get();
        var dir = getBlobDir(section);
        try {
            Files.createDirectories(dir);
            Files.move(tmpFile.toPath(), resourcePath, StandardCopyOption.ATOMIC_MOVE);
            return resourcePath.toFile();
        } catch (IOException e) {
            throw new IllegalStateException("Was not able to cache file for section " + section + " id " + id);
        }
    }

    public void ensureFileExists(String section, String id, Supplier<byte[]> supplier) {
        var resourcePath = getBlobDir(section).resolve(id);
        checkPath(resourcePath);
        if (Files.exists(resourcePath)) {
            return;
        }
        try {
            // ensure directory
            var dir = getBlobDir(section);
            Files.createDirectories(dir);
            var tmpFile = Files.createTempFile(dir, "tmp", "fileblob");
            Files.write(tmpFile, supplier.get());
            Files.move(tmpFile, dir.resolve(id), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("was not able to ensure file presence with section {} and id ", section, id, e);
        }
    }
}

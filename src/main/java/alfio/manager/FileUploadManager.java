/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager;

import alfio.model.FileBlobMetadata;
import alfio.model.modification.UploadBase64FileModification;
import alfio.repository.FileUploadRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.imgscalr.Scalr;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

@Component
@Transactional
@Log4j2
@RequiredArgsConstructor
public class FileUploadManager {

    static final int IMAGE_THUMB_MAX_WIDTH_PX = 300;
    static final int IMAGE_THUMB_MAX_HEIGHT_PX = 200;
    /**
     * Maximum allowed file size is 200kb
     */
    private static final int MAXIMUM_ALLOWED_SIZE = 1024 * 200;
    private static final MimeType IMAGE_TYPE = MimeType.valueOf("image/*");
    private final FileUploadRepository repository;
    private final Cache<String, File> cache = Caffeine.newBuilder()
        .maximumSize(20)
        .expireAfterWrite(Duration.ofMinutes(20))
        .removalListener(removalListener())
        .build();

    private static RemovalListener<String, File> removalListener() {
        return (String key, File value, RemovalCause cause) -> {
            if (value != null) {
                try {
                    Files.delete(value.toPath());
                    log.trace("deleted {}", key);
                } catch(Exception ex) {
                    log.trace("Error while deleting file", ex);
                }
            }
        };
    }

    public Optional<FileBlobMetadata> findMetadata(String id) {
        return repository.findById(id);
    }

    public void outputFile(String id, OutputStream out) {
        var file = cache.get(id, identifier -> repository.file(id));
        if(file == null || !file.exists()) { //fallback, the file will not be cached though
            cache.invalidate(id);
            file = repository.file(id);
        }

        try (var fis = new FileInputStream(file)){
            fis.transferTo(out);
        } catch(EOFException ex){
            // this happens when the browser closes the stream on its end.
            log.trace("got EOFException", ex);
        } catch (IOException e) {
            throw new IllegalStateException("Error while copying data", e);
        }
    }

    public String insertFile(UploadBase64FileModification file) {
        final var mimeType = MimeTypeUtils.parseMimeType(file.getType());
        var upload = resizeIfNeeded(file, mimeType);
        Validate.exclusiveBetween(1, MAXIMUM_ALLOWED_SIZE, upload.getFile().length);
        String digest = DigestUtils.sha256Hex(upload.getFile());
        if (Integer.valueOf(0).equals(repository.isPresent(digest))) {
            repository.upload(upload, digest, getAttributes(upload));
        }
        return digest;
    }

    public void cleanupUnreferencedBlobFiles(Date date) {
        int deleted = repository.cleanupUnreferencedBlobFiles(date);
        log.debug("removed {} unused file_blob", deleted);
    }

    /**
     * @author <a href="https://github.com/emassip">Etienne M.</a>
     */
    private UploadBase64FileModification resizeIfNeeded(UploadBase64FileModification upload, MimeType mimeType) {
        if (!mimeType.isCompatibleWith(IMAGE_TYPE)) {
            // not an image, nothing to do here.
            return upload;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(upload.getFile()));
            // resize only if the image is bigger than target size on either side
            if (image.getWidth() > IMAGE_THUMB_MAX_WIDTH_PX || image.getHeight() > IMAGE_THUMB_MAX_HEIGHT_PX) {
                UploadBase64FileModification resized = new UploadBase64FileModification();
                BufferedImage thumbImg = Scalr.resize(image, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, IMAGE_THUMB_MAX_WIDTH_PX, IMAGE_THUMB_MAX_HEIGHT_PX, Scalr.OP_ANTIALIAS);
                try (final var baos = new ByteArrayOutputStream()) {
                    ImageIO.write(thumbImg, mimeType.getSubtype(), baos);
                    resized.setFile(baos.toByteArray());
                }
                resized.setAttributes(upload.getAttributes());
                resized.setName(upload.getName());
                resized.setType(upload.getType());
                return resized;
            }
            return upload;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, String> getAttributes(UploadBase64FileModification file) {
        if(!StringUtils.startsWith(file.getType(), "image/")) {
            return Collections.emptyMap();
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getFile()));
            Map<String, String> attributes = new HashMap<>();
            attributes.put(FileBlobMetadata.ATTR_IMG_WIDTH, String.valueOf(image.getWidth()));
            attributes.put(FileBlobMetadata.ATTR_IMG_HEIGHT, String.valueOf(image.getHeight()));
            return attributes;
        } catch (IOException e) {
            log.error("error while processing image: ", e);
            return Collections.emptyMap();
        }
    }
}

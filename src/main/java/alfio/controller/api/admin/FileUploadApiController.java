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
package alfio.controller.api.admin;

import alfio.manager.FileUploadManager;
import alfio.model.modification.UploadBase64FileModification;
import lombok.extern.log4j.Log4j2;

import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/admin/api")
@Log4j2
public class FileUploadApiController {

    private static final int IMAGE_THUMB_MAX_WIDTH_PX = 500;
    private static final int IMAGE_THUMB_MAX_HEIGHT_PX = 500;

    private final FileUploadManager fileUploadManager;

    @Autowired
    public FileUploadApiController(FileUploadManager fileUploadManager) {
        this.fileUploadManager = fileUploadManager;
    }

    @PostMapping("/file/upload")
    public ResponseEntity<String> uploadFile(@RequestParam(required = false, value = "resizeImage", defaultValue = "false") Boolean resizeImage,
                                             @RequestBody UploadBase64FileModification upload) {

        try {
            final var mimeType = MimeTypeUtils.parseMimeType(upload.getType());
            if (Boolean.TRUE.equals(resizeImage)) {
                upload = resize(upload, mimeType);
            }
            return ResponseEntity.ok(fileUploadManager.insertFile(upload));
        } catch (Exception e) {
            log.error("error while uploading image", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private UploadBase64FileModification resize(UploadBase64FileModification upload, MimeType mimeType) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(upload.getFile()));
        //resize only if the image is bigger than 500px on one of the side
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
    }
}

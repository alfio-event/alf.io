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

    private final FileUploadManager fileUploadManager;

    @Autowired
    public FileUploadApiController(FileUploadManager fileUploadManager) {
        this.fileUploadManager = fileUploadManager;
    }

    @PostMapping("/file/upload")
    public ResponseEntity<String> uploadFile(@RequestBody UploadBase64FileModification upload) {
        try {
            return ResponseEntity.ok(fileUploadManager.insertFile(upload));
        } catch (Exception e) {
            log.error("error while uploading image", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

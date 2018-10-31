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
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/admin/api")
public class FileUploadApiController {

    private final FileUploadManager fileUploadManager;

    @Autowired
    public FileUploadApiController(FileUploadManager fileUploadManager) {
        this.fileUploadManager = fileUploadManager;
    }

    @RequestMapping(value = "/file/upload", method = POST)
    public ResponseEntity<String> uploadFile(@RequestParam(required = false, value = "resizeImage", defaultValue = "false") boolean resizeImage,
                                             @RequestBody UploadBase64FileModification upload) {
        try {

            if (resizeImage) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(upload.getFile()));
                //resize only if the image is bigger than 500px on one of the side
                if(image.getWidth() > 500 || image.getHeight() > 500) {
                    UploadBase64FileModification resized = new UploadBase64FileModification();
                    BufferedImage thumbImg = Scalr.resize(image, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, 500, 500, Scalr.OP_ANTIALIAS);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();

                    ImageIO.write(thumbImg, "png", baos);

                    resized.setFile(baos.toByteArray());
                    resized.setAttributes(upload.getAttributes());
                    resized.setName(upload.getName());
                    resized.setType("image/png");
                    upload = resized;
                }
            }

            return ResponseEntity.ok(fileUploadManager.insertFile(upload));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}

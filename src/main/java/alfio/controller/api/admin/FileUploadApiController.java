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

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
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
    
    private static final MimeType MIME_TYPE_IMAGE_SVG = MimeTypeUtils.parseMimeType("image/svg+xml");

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
            if (MIME_TYPE_IMAGE_SVG.equalsTypeAndSubtype(mimeType)) {
                upload = rasterizeSVG(upload);
            } else if (Boolean.TRUE.equals(resizeImage)) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(upload.getFile()));
                //resize only if the image is bigger than 500px on one of the side
                if(image.getWidth() > 500 || image.getHeight() > 500) {
                    UploadBase64FileModification resized = new UploadBase64FileModification();
                    BufferedImage thumbImg = Scalr.resize(image, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, 500, 500, Scalr.OP_ANTIALIAS);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();

                    ImageIO.write(thumbImg, mimeType.getSubtype(), baos);

                    resized.setFile(baos.toByteArray());
                    resized.setAttributes(upload.getAttributes());
                    resized.setName(upload.getName());
                    resized.setType(upload.getType());
                    upload = resized;
                }
            }

            return ResponseEntity.ok(fileUploadManager.insertFile(upload));
        } catch (Exception e) {
            log.error("error while uploading image", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private UploadBase64FileModification rasterizeSVG(UploadBase64FileModification upload) throws TranscoderException, IOException {
        final UploadBase64FileModification rasterized = new UploadBase64FileModification();
        final PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(PNGTranscoder.KEY_MAX_HEIGHT, 500f);
        transcoder.addTranscodingHint(PNGTranscoder.KEY_MAX_WIDTH, 500f);
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) { 
            transcoder.transcode(new TranscoderInput(upload.getInputStream()), new TranscoderOutput(baos));
            rasterized.setFile(baos.toByteArray());
        }
        rasterized.setAttributes(upload.getAttributes());
        rasterized.setName(upload.getName() + ".png");
        rasterized.setType(MimeTypeUtils.IMAGE_PNG_VALUE);
        return rasterized;
    }
}

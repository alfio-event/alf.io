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

import com.kitfox.svg.SVGUniverse;
import com.kitfox.svg.app.beans.SVGIcon;

import javax.imageio.ImageIO;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

@RestController
@RequestMapping("/admin/api")
@Log4j2
public class FileUploadApiController {

    private static final MimeType MIME_TYPE_IMAGE_SVG = MimeTypeUtils.parseMimeType("image/svg+xml");

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
            if (MIME_TYPE_IMAGE_SVG.equalsTypeAndSubtype(mimeType)) {
                upload = rasterizeSVG(upload, resizeImage);
            } else if (Boolean.TRUE.equals(resizeImage)) {
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
        if(image.getWidth() > IMAGE_THUMB_MAX_WIDTH_PX || image.getHeight() > IMAGE_THUMB_MAX_HEIGHT_PX) {
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

    private UploadBase64FileModification rasterizeSVG(UploadBase64FileModification upload, Boolean resizeImage) throws IOException {

        final SVGUniverse uni = new SVGUniverse();
        final URI uri = uni.loadSVG(upload.getInputStream(), "_");
        // use apply 10% margin to prevent final image size to exceed max allowed size
        final int maxWidthWithMargin  = (int) (IMAGE_THUMB_MAX_WIDTH_PX  * 0.9);
        final int maxHeightWithMargin = (int) (IMAGE_THUMB_MAX_HEIGHT_PX * 0.9);

        final SVGIcon icon = new SVGIcon();
        icon.setSvgUniverse(uni);
        icon.setSvgURI(uri);
        icon.setAntiAlias(true);
        if(icon.getIconWidth() > maxHeightWithMargin || icon.getIconHeight() > maxHeightWithMargin) {
            icon.setAutosize(SVGIcon.AUTOSIZE_STRETCH);
            icon.setPreferredSize(new Dimension(Math.min(maxWidthWithMargin, icon.getIconWidth()), Math.min(maxHeightWithMargin, icon.getIconHeight())));
        }
        final BufferedImage bi = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        icon.paintIcon(null, bi.createGraphics(), 0, 0);

        final UploadBase64FileModification rasterized = new UploadBase64FileModification();
        try (final var baos = new ByteArrayOutputStream()) {
            ImageIO.write(bi, "PNG", baos);
            rasterized.setFile(baos.toByteArray());
        }
        rasterized.setAttributes(upload.getAttributes());
        rasterized.setName(upload.getName() + ".png");
        rasterized.setType(MimeTypeUtils.IMAGE_PNG_VALUE);

        uni.removeDocument(uri);

        return rasterized;
    }
}

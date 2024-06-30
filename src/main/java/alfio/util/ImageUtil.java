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
package alfio.util;

import alfio.manager.FileUploadManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.EnumMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.center;
import static org.apache.commons.lang3.StringUtils.truncate;

public final class ImageUtil {

    private static final Logger log = LoggerFactory.getLogger(ImageUtil.class);

    private static final String DEJA_VU_SANS = "/alfio/font/DejaVuSansMono.ttf";

    private static File loadDejaVuFont() {
        try {
            File cachedFile = File.createTempFile("font-cache", ".tmp");
            cachedFile.deleteOnExit();
            try (InputStream is = new ClassPathResource(DEJA_VU_SANS).getInputStream(); OutputStream tmpOs = new FileOutputStream(cachedFile)) {
                is.transferTo(tmpOs);
            }
            return cachedFile;
        } catch (IOException e) {
            log.warn("error while loading DejaVuSansMono.ttf font", e);
            return null;
        }
    }

    public static File getDejaVuSansMonoFont(FileUploadManager fileUploadManager) {
        return fileUploadManager.getFile("font", "DejaVuSansMono", () -> loadDejaVuFont());
    }


    private ImageUtil() {
    }

    public static byte[] createQRCode(String text) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BitMatrix matrix = drawQRCode(text);
            MatrixToImageWriter.writeToStream(matrix, "png", baos);
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BitMatrix drawQRCode(String text) throws WriterException {
        Map<EncodeHintType, Object> hintMap = new EnumMap<>(EncodeHintType.class);
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        return new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 200, 200, hintMap);
    }

    public static byte[] createQRCodeWithDescription(String text, String description, FileUploadManager fileUploadManager) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BitMatrix matrix = drawQRCode(text);
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(matrix);
            BufferedImage scaled = new BufferedImage(200, 230, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = (Graphics2D)scaled.getGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.drawImage(bufferedImage, 0,0, null);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 200, 200, 30);
            graphics.setColor(Color.BLACK);
            File fontFile = getDejaVuSansMonoFont(fileUploadManager);
            if (fontFile != null) {
                graphics.setFont(Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(14f));
                graphics.drawString(center(truncate(description, 23), 25), 0, 215);
            }
            ImageIO.write(scaled, "png", baos);
            return baos.toByteArray();
        } catch (WriterException | IOException | FontFormatException e) {
            throw new IllegalStateException(e);
        }
    }

}

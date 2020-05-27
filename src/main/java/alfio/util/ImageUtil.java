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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.nayuki.qrcodegen.QrCode;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.center;
import static org.apache.commons.lang3.StringUtils.truncate;

public final class ImageUtil {
    private ImageUtil() {
    }
    static int width = 200;
    static int height = 200;
    static int border = 0;
    public static byte[] createQRCode(String text){
        try {
            QrCode qr = QrCode.encodeText(text, QrCode.Ecc.HIGH);
            int paddedSize = qr.size + border * 2;
            int scale = Math.min(width, height) / paddedSize;
            BufferedImage bufferedImage = qr.toImage(scale, border);
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics g = scaled.createGraphics();
            g.drawImage(bufferedImage, (width - paddedSize * scale) / 2, (height - paddedSize * scale) / 2, null);
            g.dispose();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", outputStream);
            return outputStream.toByteArray();
        }
        catch ( IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] createQRCodeWithDescription(String text, String description) {
        try {
            InputStream fi = new ClassPathResource("/alfio/font/DejaVuSansMono.ttf").getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            QrCode qr = QrCode.encodeText(text, QrCode.Ecc.HIGH);
            int paddedSize = qr.size + border * 2;
            int scale = Math.min(width, height) / paddedSize;
            BufferedImage bufferedImage = qr.toImage(scale, border);
            BufferedImage scaled = new BufferedImage(200, 230, BufferedImage.TYPE_INT_ARGB);
            Graphics g = scaled.createGraphics();
            g.drawImage(bufferedImage, (width - paddedSize * scale) / 2, (height - paddedSize * scale) / 2, null);
            g.dispose();
            Graphics2D graphics = (Graphics2D) scaled.getGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.drawImage(bufferedImage, 0, 0, null);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 200, 200, 30);
            graphics.setColor(Color.BLACK);
            graphics.setFont(Font.createFont(Font.TRUETYPE_FONT, fi).deriveFont(14f));
            graphics.drawString(center(truncate(description, 23), 25), 0, 215);
            ImageIO.write(scaled, "png", baos);
            return baos.toByteArray();
        }
        catch (IOException | FontFormatException e) {
            throw new IllegalStateException(e);
        }
    }
}

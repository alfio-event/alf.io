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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ImageUtilTest {

    @Test
    public void createQRCodeWithDescription() {
        Map<String, Object> info = new HashMap<>();
        info.put("apiKey", UUID.randomUUID().toString());
        info.put("baseUrl", "https://veeerylongsubdomain.verylongdomain.fiesta");
        byte[] array = ImageUtil.createQRCodeWithDescription(Json.GSON.toJson(info), "1234567890123456789012345");
        assertNotNull(array);
    }
}
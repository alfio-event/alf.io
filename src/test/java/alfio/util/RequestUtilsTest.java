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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RequestUtilsTest {

    @Test
    public void testUA() {
        Assertions.assertTrue(RequestUtils.isSocialMediaShareUA("facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"));
        Assertions.assertTrue(RequestUtils.isSocialMediaShareUA("XING-contenttabreceiver/2.0"));
        Assertions.assertTrue(RequestUtils.isSocialMediaShareUA("LinkedInBot/1.0 (compatible; Mozilla/5.0; Jakarta Commons-HttpClient/3.1 +http://www.linkedin.com)"));
        Assertions.assertTrue(RequestUtils.isSocialMediaShareUA("Twitterbot/1.0"));
        Assertions.assertFalse(RequestUtils.isSocialMediaShareUA("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36"));
    }
}

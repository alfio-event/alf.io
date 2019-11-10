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

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.http.HttpClient;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
public class FileDownloadManagerIntegrationTest {

    @Autowired
    private HttpClient httpClient;

    @Test
    public void testFileDownloadSuccess() {
        var file = new FileDownloadManager(httpClient).downloadFile("https://raw.githubusercontent.com/alfio-event/alf.io/2.0-M2/src/main/webapp/resources/images/alfio-logo.svg");
        Assert.assertEquals("text/plain; charset=utf-8", file.getType());
        Assert.assertEquals("alfio-logo.svg", file.getName());
        Assert.assertEquals(7202, file.getFile().length);
    }

    @Test
    public void testFileDownloadNotFound() {
        var res = new FileDownloadManager(httpClient).downloadFile("https://raw.githubusercontent.com/alfio-event/alf.io/2.0-M2/src/main/webapp/resources/images/404");
        Assert.assertNull(res);
    }
}

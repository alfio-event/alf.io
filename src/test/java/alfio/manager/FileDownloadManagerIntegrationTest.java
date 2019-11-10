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

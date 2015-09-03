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
import alfio.model.FileBlobMetadata;
import alfio.model.modification.UploadBase64FileModification;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;

import static alfio.test.util.IntegrationTestUtil.initSystemProperties;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class FileUploadManagerIntegrationTest {

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
    }

    @Autowired
    FileUploadManager fileUploadManager;

    private static final byte[] FILE = {1,2,3,4};

    private static final byte[] ONE_PIXEL_BLACK_GIF = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAUEBAAAACwAAAAAAQABAAACAkQBADs=");

    @Test
    public void testInsert() {
        UploadBase64FileModification toInsert = new UploadBase64FileModification();
        toInsert.setFile(FILE);
        toInsert.setName("myfile.txt");
        toInsert.setType("text/plain");
        String id = fileUploadManager.insertFile(toInsert);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        fileUploadManager.outputFile(id, baos);

        Assert.assertArrayEquals(FILE, baos.toByteArray());

        Optional<FileBlobMetadata> metadata = fileUploadManager.findMetadata(id);
        Assert.assertTrue(metadata.isPresent());
        Assert.assertEquals("myfile.txt", metadata.get().getName());
        Assert.assertEquals("text/plain", metadata.get().getContentType());
    }


    @Test
    public void testInsertImage() {
        UploadBase64FileModification toInsert = new UploadBase64FileModification();
        toInsert.setFile(ONE_PIXEL_BLACK_GIF);
        toInsert.setName("image.gif");
        toInsert.setType("image/gif");
        String id = fileUploadManager.insertFile(toInsert);

        Optional<FileBlobMetadata> metadata = fileUploadManager.findMetadata(id);

        Assert.assertTrue(metadata.isPresent());

        Assert.assertEquals("1", metadata.get().getAttributes().get("width"));
        Assert.assertEquals("1", metadata.get().getAttributes().get("height"));
    }

    @Test
    public void testFindMetadataNotPresent() {
        Assert.assertFalse(fileUploadManager.findMetadata("unknownid").isPresent());
    }
}

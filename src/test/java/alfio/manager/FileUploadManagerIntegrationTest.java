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
import alfio.test.util.AlfioIntegrationTest;
import alfio.util.BaseIntegrationTest;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@AlfioIntegrationTest
class FileUploadManagerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    FileUploadManager fileUploadManager;

    private static final byte[] FILE = {1,2,3,4};

    @Test
    void testInsert() {
        UploadBase64FileModification toInsert = new UploadBase64FileModification();
        toInsert.setFile(FILE);
        toInsert.setName("myfile.txt");
        toInsert.setType("text/plain");
        String id = fileUploadManager.insertFile(toInsert);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        fileUploadManager.outputFile(id, baos);

        assertArrayEquals(FILE, baos.toByteArray());

        Optional<FileBlobMetadata> metadata = fileUploadManager.findMetadata(id);
        assertTrue(metadata.isPresent());
        assertEquals("myfile.txt", metadata.get().getName());
        assertEquals("text/plain", metadata.get().getContentType());

        //

        fileUploadManager.cleanupUnreferencedBlobFiles(DateUtils.addDays(new Date(), 1));
        assertFalse(fileUploadManager.findMetadata(id).isPresent());
    }


    @Test
    void testInsertImage() {
        UploadBase64FileModification toInsert = new UploadBase64FileModification();
        toInsert.setFile(ONE_PIXEL_BLACK_GIF);
        toInsert.setName("image.gif");
        toInsert.setType("image/gif");
        String id = fileUploadManager.insertFile(toInsert);

        Optional<FileBlobMetadata> metadata = fileUploadManager.findMetadata(id);

        assertTrue(metadata.isPresent());

        assertEquals("1", metadata.get().getAttributes().get("width"));
        assertEquals("1", metadata.get().getAttributes().get("height"));

        fileUploadManager.cleanupUnreferencedBlobFiles(DateUtils.addDays(new Date(), 1));
        assertFalse(fileUploadManager.findMetadata(id).isPresent());
    }

    @Test
    void testFindMetadataNotPresent() {
        assertFalse(fileUploadManager.findMetadata("unknownid").isPresent());
    }

    @Test
    void testInsertResizedImage() throws IOException {
        // Image credit: NASA, ESA, CSA, and STScI
        try (var in = getClass().getResourceAsStream("/images/main_image_star-forming_region_carina_reduced.jpg")) {
            UploadBase64FileModification toInsert = new UploadBase64FileModification();
            toInsert.setFile(Objects.requireNonNull(in).readAllBytes());
            toInsert.setName("image.jpg");
            toInsert.setType("image/jpeg");
            String id = fileUploadManager.insertFile(toInsert);

            Optional<FileBlobMetadata> metadata = fileUploadManager.findMetadata(id);

            assertTrue(metadata.isPresent());

            assertEquals(String.valueOf(FileUploadManager.IMAGE_THUMB_MAX_WIDTH_PX), metadata.get().getAttributes().get("width"));
            assertEquals("174", metadata.get().getAttributes().get("height"));

            fileUploadManager.cleanupUnreferencedBlobFiles(DateUtils.addDays(new Date(), 1));
            assertFalse(fileUploadManager.findMetadata(id).isPresent());
        }
    }
}

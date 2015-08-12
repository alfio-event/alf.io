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
package alfio.controller.support;

import alfio.manager.FileUploadManager;
import alfio.model.FileBlobMetadata;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import com.insightfullogic.lambdabehave.generators.Generator;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JunitSuiteRunner.class)
public class TemplateProcessorTest {{
    describe("TemplateProcessor.fillWithImageData", it -> {
        it.requires(1000)
            .example(Generator.integersUpTo(10_000), Generator.integersUpTo(100_000))
            .toShow("Given an image, the resulting image size must be always under 300x150 pixels", (expect, first, second) -> {
                Map<String, String> parameters = new HashMap<>();
                parameters.put(FileUploadManager.ATTR_IMG_WIDTH, String.valueOf(first));
                parameters.put(FileUploadManager.ATTR_IMG_HEIGHT, String.valueOf(second));
                FileBlobMetadata metadata = mock(FileBlobMetadata.class);
                when(metadata.getAttributes()).thenReturn(parameters);
                Map<String,Object> model = new HashMap<>();
                TemplateProcessor.fillWithImageData(metadata, mock(FileUploadManager.class), model);
                expect.that(Integer.parseInt(String.valueOf(model.get("imageWidth"))) <= 300);
                expect.that(Integer.parseInt(String.valueOf(model.get("imageHeight"))) <= 150);
            });

    });
}}
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
package alfio.controller;

import alfio.manager.FileUploadManager;
import alfio.model.FileBlobMetadata;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Controller
public class FileController {

    private final FileUploadManager manager;

    private static final String MAX_AGE_6_MONTH = "max-age=15778463";

    public FileController(FileUploadManager manager) {
        this.manager = manager;
    }

    @GetMapping("/file/{digest}")
    public void showFile(@PathVariable String digest, HttpServletRequest request, HttpServletResponse response) throws IOException {


        // fast path if the user has the header If-None-Match set and there is a matching file
        // this avoid a db connection
        var digestNoneMatchHeader =  request.getHeader("If-None-Match");
        if (digest.equals(digestNoneMatchHeader) && manager.hasCached(digest)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        //

        Optional<FileBlobMetadata> res = manager.findMetadata(digest);
        if (res.isPresent()) {
            FileBlobMetadata metadata = res.get();
            if (digest.equals(digestNoneMatchHeader)) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            } else {
                response.setContentType(metadata.getContentType());
                response.setContentLength(metadata.getContentSize());
                response.setHeader("ETag", metadata.getId()); //id = digest
                response.setHeader("Cache-Control", MAX_AGE_6_MONTH);
                try (var os = response.getOutputStream()) {
                    manager.outputFile(digest, os);
                }
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}

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

import alfio.model.FileBlobMetadata;
import alfio.model.modification.UploadBase64FileModification;
import alfio.repository.FileUploadRepository;
import alfio.util.Json;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.support.AbstractLobCreatingPreparedStatementCallback;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@Transactional
@Log4j2
@RequiredArgsConstructor
public class FileUploadManager {

    /**
     * Maximum allowed file size is 200kb
     */
    static final int MAXIMUM_ALLOWED_SIZE = 1024 * 200;
    private final NamedParameterJdbcTemplate jdbc;
    private final FileUploadRepository repository;
    private final Cache<String, byte[]> cache = Caffeine.newBuilder()
        .maximumSize(20)
        .expireAfterWrite(20, TimeUnit.MINUTES)
        .build();

    public Optional<FileBlobMetadata> findMetadata(String id) {
        return repository.findById(id);
    }

    public void outputFile(String id, OutputStream out) {
        byte[] res = cache.get(id, identifier -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            SqlParameterSource param = new MapSqlParameterSource("id", id);
            jdbc.query(repository.fileContent(id), param, rs -> {
                try (InputStream is = rs.getBinaryStream("content")) {
                    StreamUtils.copy(is, baos);
                } catch (IOException e) {
                    throw new IllegalStateException("Error while copying data", e);
                }
            });
            return baos.toByteArray();
        });
        try {
            StreamUtils.copy(res, out);
        } catch (IOException e) {
            throw new IllegalStateException("Error while copying data", e);
        }
    }


    public String insertFile(UploadBase64FileModification file) {

        Validate.exclusiveBetween(1, MAXIMUM_ALLOWED_SIZE, file.getFile().length);

        String digest = DigestUtils.sha256Hex(file.getFile());

        if(Integer.valueOf(1).equals(repository.isPresent(digest))) {
            return digest;
        }

        LobHandler lobHandler = new DefaultLobHandler();


        jdbc.getJdbcOperations().execute(repository.uploadTemplate(),
            new AbstractLobCreatingPreparedStatementCallback(lobHandler) {
                @Override
                protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                    ps.setString(1, digest);
                    ps.setString(2, file.getName());
                    ps.setLong(3, file.getFile().length);
                    lobCreator.setBlobAsBytes(ps, 4, file.getFile());
                    ps.setString(5, file.getType());
                    ps.setString(6, Json.GSON.toJson(getAttributes(file)));
                }
            });
        return digest;
    }

    public void cleanupUnreferencedBlobFiles(Date date) {
        int deleted = repository.cleanupUnreferencedBlobFiles(date);
        log.debug("removed {} unused file_blob", deleted);
    }

    private Map<String, String> getAttributes(UploadBase64FileModification file) {
        if(!StringUtils.startsWith(file.getType(), "image/")) {
            return Collections.emptyMap();
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getFile()));
            Map<String, String> attributes = new HashMap<>();
            attributes.put(FileBlobMetadata.ATTR_IMG_WIDTH, String.valueOf(image.getWidth()));
            attributes.put(FileBlobMetadata.ATTR_IMG_HEIGHT, String.valueOf(image.getHeight()));
            return attributes;
        } catch (IOException e) {
            log.error("error while processing image: ", e);
            return Collections.emptyMap();
        }
    }
}

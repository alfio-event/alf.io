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

import alfio.model.UploadedResource;
import alfio.model.modification.UploadBase64FileModification;
import alfio.repository.UploadedResourceRepository;
import alfio.util.Json;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Transactional
@Log4j2
public class UploadedResourceManager {

    public static final String ATTR_IMG_WIDTH = "width";
    public static final String ATTR_IMG_HEIGHT = "height";

    private final NamedParameterJdbcTemplate jdbc;
    private final UploadedResourceRepository uploadedResourceRepository;

    @Autowired
    public UploadedResourceManager(NamedParameterJdbcTemplate jdbc, UploadedResourceRepository uploadedResourceRepository) {
        this.jdbc = jdbc;
        this.uploadedResourceRepository = uploadedResourceRepository;
    }

    public boolean hasResource(String name) {
        return uploadedResourceRepository.hasResource(name);
    }

    public boolean hasResource(int organizationId, String name) {
        return uploadedResourceRepository.hasResource(organizationId, name);
    }

    public boolean hasResource(int organizationId, int eventId, String name) {
        return uploadedResourceRepository.hasResource(organizationId, eventId, name);
    }

    public UploadedResource get(String name) {
        return uploadedResourceRepository.get(name);
    }

    public UploadedResource get(int organizationId, String name) {
        return uploadedResourceRepository.get(organizationId, name);
    }

    public UploadedResource get(int organizationId, int eventId, String name) {
        return uploadedResourceRepository.get(organizationId, eventId, name);
    }


    public void outputResource(String name, OutputStream out) {
        SqlParameterSource param = new MapSqlParameterSource("name", name);
        jdbc.query(uploadedResourceRepository.fileContentTemplate(name), param, rs -> {
            try (InputStream is = rs.getBinaryStream("content")) {
                StreamUtils.copy(is, out);
            } catch (IOException e) {
                throw new IllegalStateException("Error while copying data", e);
            }
        });
    }

    public void outputResource(int organizationId, String name, OutputStream out) {
        SqlParameterSource param = new MapSqlParameterSource("name", name).addValue("organizationId", organizationId);
        jdbc.query(uploadedResourceRepository.fileContentTemplate(organizationId, name), param, rs -> {
            try (InputStream is = rs.getBinaryStream("content")) {
                StreamUtils.copy(is, out);
            } catch (IOException e) {
                throw new IllegalStateException("Error while copying data", e);
            }
        });
    }

    public void outputResource(int organizationId, int eventId, String name, OutputStream out) {
        SqlParameterSource param = new MapSqlParameterSource("name", name).addValue("organizationId", organizationId).addValue("eventId", eventId);
        jdbc.query(uploadedResourceRepository.fileContentTemplate(organizationId, eventId, name), param, rs -> {
            try (InputStream is = rs.getBinaryStream("content")) {
                StreamUtils.copy(is, out);
            } catch (IOException e) {
                throw new IllegalStateException("Error while copying data", e);
            }
        });
    }

    public int saveResource(UploadBase64FileModification file) {
        if (hasResource(file.getName())) {
            uploadedResourceRepository.delete(file.getName());
        }
        LobHandler lobHandler = new DefaultLobHandler();
        return jdbc.getJdbcOperations().execute(uploadedResourceRepository.uploadTemplate(file.getName()),
            new AbstractLobCreatingPreparedStatementCallback(lobHandler) {
                @Override
                protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                    setFileValues(ps, lobCreator, file, 1);
                }
            });

    }

    public int saveResource(int organizationId, UploadBase64FileModification file) {
        if (hasResource(organizationId, file.getName())) {
            uploadedResourceRepository.delete(organizationId, file.getName());
        }
        LobHandler lobHandler = new DefaultLobHandler();
        return jdbc.getJdbcOperations().execute(uploadedResourceRepository.uploadTemplate(organizationId, file.getName()),
            new AbstractLobCreatingPreparedStatementCallback(lobHandler) {
                @Override
                protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                    ps.setInt(2, organizationId);
                    setFileValues(ps, lobCreator, file, 2);
                }
            });
    }

    public int saveResource(int organizationId, int eventId, UploadBase64FileModification file) {
        if (hasResource(organizationId, eventId, file.getName())) {
            uploadedResourceRepository.delete(organizationId, eventId, file.getName());
        }
        LobHandler lobHandler = new DefaultLobHandler();
        return jdbc.getJdbcOperations().execute(uploadedResourceRepository.uploadTemplate(organizationId, eventId, file.getName()),
            new AbstractLobCreatingPreparedStatementCallback(lobHandler) {
                @Override
                protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                    ps.setInt(2, organizationId);
                    ps.setInt(3, eventId);
                    setFileValues(ps, lobCreator, file, 3);
                }
            });

    }

    private static void setFileValues(PreparedStatement ps, LobCreator lobCreator, UploadBase64FileModification file, int baseIndex) throws SQLException {
        ps.setString(1, file.getName());

        ps.setLong(baseIndex + 1, file.getFile().length);
        lobCreator.setBlobAsBytes(ps, baseIndex + 2, file.getFile());
        ps.setString(baseIndex + 3, file.getType());
        ps.setString(baseIndex + 4, Json.GSON.toJson(getAttributes(file)));
    }

    public void deleteResource(String name) {
        uploadedResourceRepository.delete(name);
    }

    public void deleteResource(int organizationId, String name) {
        uploadedResourceRepository.delete(organizationId, name);
    }

    public void deleteResource(int organizationId, int eventId, String name) {
        uploadedResourceRepository.delete(organizationId, eventId, name);
    }

    public List<UploadedResource> findAll() {
        return uploadedResourceRepository.findAll();
    }

    public List<UploadedResource> findAll(int organizationId) {
        return uploadedResourceRepository.findAll(organizationId);
    }

    public List<UploadedResource> findAll(int organizationId, int eventId) {
        return uploadedResourceRepository.findAll(organizationId, eventId);
    }

    private static Map<String, String> getAttributes(UploadBase64FileModification file) {
        if (!StringUtils.startsWith(file.getType(), "image/")) {
            return file.getAttributes();
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getFile()));
            Map<String, String> attributes = new HashMap<>(file.getAttributes());
            attributes.put(ATTR_IMG_WIDTH, String.valueOf(image.getWidth()));
            attributes.put(ATTR_IMG_HEIGHT, String.valueOf(image.getHeight()));
            return attributes;
        } catch (IOException e) {
            log.error("error while processing image: ", e);
            return file.getAttributes();
        }
    }

    public Optional<byte[]> findCascading(int organizationId, int eventId, String savedName) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if(hasResource(organizationId, eventId, savedName)) {
            outputResource(organizationId, eventId, savedName, baos);
            return Optional.of(baos.toByteArray());
        } else if (hasResource(organizationId, savedName)) {
            outputResource(organizationId, savedName, baos);
            return Optional.of(baos.toByteArray());
        } else if (hasResource(savedName)) {
            outputResource(savedName, baos);
            return Optional.of(baos.toByteArray());
        } else {
            return Optional.empty();
        }
    }
}

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
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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

    private final UploadedResourceRepository uploadedResourceRepository;

    @Autowired
    public UploadedResourceManager(UploadedResourceRepository uploadedResourceRepository) {
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
        uploadedResourceRepository.fileContent(name, out);
    }

    public void outputResource(int organizationId, String name, OutputStream out) {
        uploadedResourceRepository.fileContent(organizationId, name, out);
    }

    public void outputResource(int organizationId, int eventId, String name, OutputStream out) {
        uploadedResourceRepository.fileContent(organizationId, eventId, name, out);
    }

    public Optional<Integer> saveResource(UploadBase64FileModification file) {
        if (hasResource(file.getName())) {
            uploadedResourceRepository.delete(file.getName());
        }

        return Optional.ofNullable(uploadedResourceRepository.upload(null, null, file, getAttributes(file)));
    }

    public Optional<Integer> saveResource(int organizationId, UploadBase64FileModification file) {
        if (hasResource(organizationId, file.getName())) {
            uploadedResourceRepository.delete(organizationId, file.getName());
        }

        return Optional.ofNullable(uploadedResourceRepository.upload(organizationId, null, file, getAttributes(file)));
    }

    public Optional<Integer> saveResource(int organizationId, int eventId, UploadBase64FileModification file) {
        if (hasResource(organizationId, eventId, file.getName())) {
            uploadedResourceRepository.delete(organizationId, eventId, file.getName());
        }

        return Optional.ofNullable(uploadedResourceRepository.upload(organizationId, eventId, file, getAttributes(file)));
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

    public Optional<byte[]> findCascading(int organizationId, Integer eventId, String savedName) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if(eventId != null && hasResource(organizationId, eventId, savedName)) {
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

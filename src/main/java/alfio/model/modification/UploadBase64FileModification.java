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
package alfio.model.modification;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


public class UploadBase64FileModification {

    private byte[] file;
    private String fileAsString;
    private String type;
    private String name;
    private Map<String, String> attributes;

    public void setFile(byte[] file) {
        this.file = file;
    }

    public void setFileAsString(String fileAsString) {
        this.fileAsString = fileAsString;
    }

    public String getFileAsString() {
        return fileAsString;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public InputStream getInputStream() {
        return new ByteArrayInputStream(file);
    }

    public Map<String, String> getAttributes() {
        return attributes != null ? attributes : new HashMap<>(0);
    }

    public byte[] getFile() {
        if (fileAsString != null) {
            return fileAsString.getBytes(StandardCharsets.UTF_8);
        } else {
            return file;
        }
    }
}

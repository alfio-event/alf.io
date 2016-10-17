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
/**
 * This file is part of alf.io.
 * <p>
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.controller.api.admin;

import alfio.manager.UploadedResourceManager;
import alfio.manager.user.UserManager;
import alfio.model.UploadedResource;
import alfio.model.modification.UploadBase64FileModification;
import alfio.repository.EventRepository;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/admin/api")
public class ResourceController {


    private final UploadedResourceManager uploadedResourceManager;
    private final UserManager userManager;
    private final EventRepository eventRepository;


    @Autowired
    public ResourceController(UploadedResourceManager uploadedResourceManager, UserManager userManager, EventRepository eventRepository) {
        this.uploadedResourceManager = uploadedResourceManager;
        this.userManager = userManager;
        this.eventRepository = eventRepository;
    }

    //------------------

    @RequestMapping(value = "/resource/", method = RequestMethod.GET)
    public List<UploadedResource> findAll(Principal principal) {
        checkAccess(principal);
        return uploadedResourceManager.findAll();
    }

    @RequestMapping(value = "/resource-organization/{organizationId}", method = RequestMethod.GET)
    public List<UploadedResource> findAllForOrganization(@PathVariable("organizationId") int organizationId, Principal principal) {
        checkAccess(organizationId, principal);
        return uploadedResourceManager.findAll(organizationId);
    }

    @RequestMapping(value = "/resource-event/{organizationId}/{eventId}", method = RequestMethod.GET)
    public List<UploadedResource> findAllForEvent(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId, Principal principal) {
        checkAccess(organizationId, eventId, principal);
        return uploadedResourceManager.findAll(organizationId, eventId);
    }


    //------------------

    @RequestMapping(value = "/resource/{name}/metadata", method = RequestMethod.GET)
    public UploadedResource getMetadata(@PathVariable("name") String name, Principal principal) {
        checkAccess(principal);
        return uploadedResourceManager.get(name);
    }

    @RequestMapping(value = "/resource-organization/{organizationId}/{name}/metadata", method = RequestMethod.GET)
    public UploadedResource getMetadata(@PathVariable("organizationId") int organizationId, @PathVariable("name") String name, Principal principal) {
        checkAccess(organizationId, principal);
        return uploadedResourceManager.get(organizationId, name);
    }

    @RequestMapping(value = "/resource-event/{organizationId}/{eventId}/{name}/metadata", method = RequestMethod.GET)
    public UploadedResource getMetadata(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId, @PathVariable("name") String name, Principal principal) {
        checkAccess(organizationId, eventId, principal);
        return uploadedResourceManager.get(organizationId, eventId, name);
    }

    //------------------

    @RequestMapping(value = "/resource/", method = POST)
    public void uploadFile(@RequestBody UploadBase64FileModification upload, Principal principal) {
        checkAccess(principal);
        uploadedResourceManager.saveResource(upload);
    }

    @RequestMapping(value = "/resource-organization/{organizationId}/", method = POST)
    public void uploadFile(@PathVariable("organizationId") int organizationId, @RequestBody UploadBase64FileModification upload, Principal principal) {
        checkAccess(organizationId, principal);
        uploadedResourceManager.saveResource(organizationId, upload);
    }

    @RequestMapping(value = "/resource-event/{organizationId}/{eventId}/", method = POST)
    public void uploadFile(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId, @RequestBody UploadBase64FileModification upload, Principal principal) {
        checkAccess(organizationId, eventId, principal);
        uploadedResourceManager.saveResource(organizationId, eventId, upload);
    }

    //------------------
    @RequestMapping(value = "/resource/{name}", method = RequestMethod.GET)
    public void outputContent(@PathVariable("name") String name, Principal principal, HttpServletResponse response) throws IOException {
        checkAccess(principal);
        UploadedResource metadata = uploadedResourceManager.get(name);
        try (OutputStream os = response.getOutputStream()) {
            response.setContentType(metadata.getContentType());
            response.setContentLength(metadata.getContentSize());
            uploadedResourceManager.outputResource(name, os);
        }
    }

    @RequestMapping(value = "/resource-organization/{organizationId}/{name}", method = RequestMethod.GET)
    public void outputContent(@PathVariable("organizationId") int organizationId, @PathVariable("name") String name, Principal principal, HttpServletResponse response) throws IOException {
        checkAccess(organizationId, principal);
        UploadedResource metadata = uploadedResourceManager.get(organizationId, name);
        try (OutputStream os = response.getOutputStream()) {
            response.setContentType(metadata.getContentType());
            response.setContentLength(metadata.getContentSize());
            uploadedResourceManager.outputResource(organizationId, name, os);
        }
    }

    @RequestMapping(value = "/resource-event/{organizationId}/{eventId}/{name}", method = RequestMethod.GET)
    public void outputContent(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId, @PathVariable("name") String name, Principal principal, HttpServletResponse response) throws IOException {
        checkAccess(organizationId, eventId, principal);
        UploadedResource metadata = uploadedResourceManager.get(organizationId, eventId, name);
        try (OutputStream os = response.getOutputStream()) {
            response.setContentType(metadata.getContentType());
            response.setContentLength(metadata.getContentSize());
            uploadedResourceManager.outputResource(organizationId, eventId, name, os);
        }
    }

    //------------------

    @RequestMapping(value = "/resource/{name}", method = RequestMethod.DELETE)
    public void delete(@PathVariable("name") String name, Principal principal) {
        checkAccess(principal);
        uploadedResourceManager.deleteResource(name);
    }

    @RequestMapping(value = "/resource-organization/{organizationId}/{name}", method = RequestMethod.DELETE)
    public void delete(@PathVariable("organizationId") int organizationId, @PathVariable("name") String name, Principal principal) {
        checkAccess(organizationId, principal);
        uploadedResourceManager.deleteResource(organizationId, name);
    }

    @RequestMapping(value = "/resource-event/{organizationId}/{eventId}/{name}", method = RequestMethod.DELETE)
    public void delete(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId, @PathVariable("name") String name, Principal principal) {
        checkAccess(organizationId, eventId, principal);
        uploadedResourceManager.deleteResource(organizationId, eventId, name);
    }

    //------------------

    private void checkAccess(Principal principal) {
        Validate.isTrue(userManager.isAdmin(userManager.findUserByUsername(principal.getName())));
    }

    private void checkAccess(int organizationId, Principal principal) {
        Validate.isTrue(userManager.isOwnerOfOrganization(userManager.findUserByUsername(principal.getName()), organizationId));
    }

    private void checkAccess(int organizationId, int eventId, Principal principal) {
        Validate.isTrue(eventRepository.findById(eventId).getOrganizationId() == organizationId && userManager.isOwnerOfOrganization(userManager.findUserByUsername(principal.getName()), organizationId));
    }
}

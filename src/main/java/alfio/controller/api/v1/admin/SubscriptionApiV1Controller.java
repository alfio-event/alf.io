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
package alfio.controller.api.v1.admin;

import alfio.manager.FileDownloadManager;
import alfio.manager.FileUploadManager;
import alfio.manager.SubscriptionManager;
import alfio.manager.user.UserManager;
import alfio.model.api.v1.admin.SubscriptionDescriptorModificationRequest;
import alfio.model.modification.SubscriptionDescriptorModification;
import alfio.util.Json;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/admin/subscription")
@Transactional
public class SubscriptionApiV1Controller {
    private final SubscriptionManager subscriptionManager;
    private final FileUploadManager fileUploadManager;
    private final FileDownloadManager fileDownloadManager;
    private final UserManager userManager;

    public SubscriptionApiV1Controller(SubscriptionManager subscriptionManager,
                                       FileUploadManager fileUploadManager,
                                       FileDownloadManager fileDownloadManager,
                                       UserManager userManager) {
        this.subscriptionManager = subscriptionManager;
        this.fileUploadManager = fileUploadManager;
        this.fileDownloadManager = fileDownloadManager;
        this.userManager = userManager;
    }

    @PostMapping("/create")
    public ResponseEntity<String> create(@RequestBody SubscriptionDescriptorModificationRequest request, Principal principal) {
        var organization = userManager.findUserOrganizations(principal.getName()).get(0);
        String imageRef = null;
        if(StringUtils.isNotEmpty(request.getImageUrl())) {
            imageRef = fetchImage(request.getImageUrl());
        }
        var modification = request.toDescriptorModification(null, organization.getId(), imageRef)
            .flatMap(SubscriptionDescriptorModification::validate);
        if (modification.isSuccess()) {
            // request is valid
            var optionalId = subscriptionManager.createSubscriptionDescriptor(modification.getData());
            return optionalId.map(uuid -> ResponseEntity.ok(uuid.toString()))
                .orElseGet(() -> ResponseEntity.internalServerError().build());
        }
        return ResponseEntity.badRequest().body(Json.toJson(modification.getErrors()));

    }

    

    private String fetchImage(String url) {
        if(url != null) {
            FileDownloadManager.DownloadedFile file = fileDownloadManager.downloadFile(url);
            return file != null ? fileUploadManager.insertFile(file.toUploadBase64FileModification()) : null;
        } else {
            return null;
        }
    }
}

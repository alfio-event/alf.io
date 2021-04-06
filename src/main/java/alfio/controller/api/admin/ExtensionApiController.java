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

package alfio.controller.api.admin;

import alfio.controller.api.support.PageAndContent;
import alfio.extension.Extension;
import alfio.extension.ExtensionService;
import alfio.manager.user.UserManager;
import alfio.model.EventAndOrganizationId;
import alfio.model.ExtensionLog;
import alfio.model.ExtensionSupport;
import alfio.model.ExtensionSupport.ExtensionMetadataValue;
import alfio.model.ExtensionSupport.ExtensionParameterMetadataAndValue;
import alfio.model.user.Organization;
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.user.OrganizationRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
@RequestMapping("/admin/api/extensions")
@Log4j2
public class ExtensionApiController {

    private static final String SAMPLE_JS;


    static {
        try (InputStream is = new ClassPathResource("/alfio/extension/sample.js").getInputStream()){
            SAMPLE_JS = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read sample file", e);
        }
    }

    private final ExtensionService extensionService;
    private final UserManager userManager;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;


    @GetMapping("")
    public List<ExtensionSupport> listAll(Principal principal) {
        ensureAdmin(principal);
        return extensionService.listAll();
    }

    @GetMapping("/sample")
    public ExtensionSupport getSample() {
        return new ExtensionSupport(null, "-", "", null, true, true, SAMPLE_JS, null);
    }

    @PostMapping(value = "")
    public ResponseEntity<SerializablePair<Boolean, String>> create(@RequestBody Extension script, Principal principal) {
        return createOrUpdate(null, null, script, principal);
    }

    @PostMapping(value = "{path}/{name}")
    public ResponseEntity<SerializablePair<Boolean, String>> update(@PathVariable("path") String path, @PathVariable("name") String name, @RequestBody Extension script, Principal principal) {
        return createOrUpdate(path, name, script, principal);
    }

    private ResponseEntity<SerializablePair<Boolean, String>> createOrUpdate(String previousPath, String previousName, Extension script, Principal principal) {
        try {
            ensureAdmin(principal);
            extensionService.createOrUpdate(previousPath, previousName, script);
            return ResponseEntity.ok(SerializablePair.of(true, null));
        } catch (Throwable t) {
            log.error("unexpected exception", t);
            return ResponseEntity.badRequest().body(SerializablePair.of(false, t.getMessage()));
        }
    }

    @GetMapping("{path}/{name}")
    public ResponseEntity<ExtensionSupport> loadSingle(@PathVariable("path") String path, @PathVariable("name") String name, Principal principal) {
        ensureAdmin(principal);
        return extensionService.getSingle(URLDecoder.decode(path, StandardCharsets.UTF_8), name).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }


    @DeleteMapping(value = "{path}/{name}")
    public void delete(@PathVariable("path") String path, @PathVariable("name") String name, Principal principal) {
        ensureAdmin(principal);
        extensionService.delete(path, name);
    }

    @PostMapping(value = "/{path}/{name}/toggle/{enable}")
    public void toggle(@PathVariable("path") String path, @PathVariable("name") String name, @PathVariable("enable") boolean enable, Principal principal) {
        ensureAdmin(principal);
        extensionService.toggle(path, name, enable);
    }


    //
    @GetMapping("/setting/system")
    public Map<Integer, List<ExtensionParameterMetadataAndValue>> getParametersFor(Principal principal) {
        if(userManager.isAdmin(userManager.findUserByUsername(principal.getName()))) {
            return extensionService.getConfigurationParametersFor("-", "-%", "SYSTEM")
                .stream().collect(Collectors.groupingBy(ExtensionParameterMetadataAndValue::getExtensionId));
        }
        return Map.of();
    }

    @PostMapping("/setting/system/bulk-update")
    public void bulkUpdateSystem(@RequestBody List<ExtensionMetadataValue> toUpdate, Principal principal) {
        ensureAdmin(principal);
        ensureIdsArePresent(toUpdate, extensionService.getConfigurationParametersFor("-", "-%", "SYSTEM"));
        extensionService.bulkUpdateSystemSettings(toUpdate);
    }

    @DeleteMapping("/setting/system/{id}")
    public void deleteSystemSettingValue(@PathVariable("id") int id, Principal principal) {
        ensureAdmin(principal);
        extensionService.deleteSettingValue(id, "-");
    }

    @GetMapping("/setting/organization/{orgId}")
    public Map<Integer, List<ExtensionParameterMetadataAndValue>> getParametersFor(@PathVariable("orgId") int orgId, Principal principal) {
        Organization org = organizationRepository.getById(orgId);
        ensureOrganization(principal, org);
        return extensionService.getConfigurationParametersFor("-" + org.getId(), "-" + org.getId()+"-%", "ORGANIZATION")
            .stream().collect(Collectors.groupingBy(ExtensionParameterMetadataAndValue::getExtensionId));
    }

    @PostMapping("/setting/organization/{orgId}/bulk-update")
    public void bulkUpdateOrganization(@PathVariable("orgId") int orgId, @RequestBody List<ExtensionMetadataValue> toUpdate, Principal principal) {
        Organization org = organizationRepository.getById(orgId);
        ensureOrganization(principal, org);
        ensureIdsArePresent(toUpdate, extensionService.getConfigurationParametersFor("-" + org.getId(), "-" + org.getId()+"-%", "ORGANIZATION"));
        extensionService.bulkUpdateOrganizationSettings(org, toUpdate);
    }

    @DeleteMapping("/setting/organization/{orgId}/{id}")
    public void deleteOrganizationSettingValue(@PathVariable("orgId") int orgId, @PathVariable("id") int id, Principal principal) {
        Organization org = organizationRepository.getById(orgId);
        ensureOrganization(principal, org);
        extensionService.deleteSettingValue(id, "-" + org.getId());
    }

    @GetMapping("/setting/organization/{orgId}/event/{shortName}")
    public Map<Integer, List<ExtensionParameterMetadataAndValue>> getParametersFor(@PathVariable("orgId") int orgId,
                                                                     @PathVariable("shortName") String eventShortName,
                                                                     Principal principal) {

        Organization org = organizationRepository.getById(orgId);
        ensureOrganization(principal, org);
        var event = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventShortName).orElseThrow(IllegalStateException::new);
        ensureEventInOrganization(org, event);
        String pattern = generatePatternFrom(event);
        return extensionService.getConfigurationParametersFor(pattern, pattern,"EVENT")
            .stream().collect(Collectors.groupingBy(ExtensionParameterMetadataAndValue::getExtensionId));
    }

    @PostMapping("/setting/organization/{orgId}/event/{shortName}/bulk-update")
    public void bulkUpdateEvent(@PathVariable("orgId") int orgId, @PathVariable("shortName") String eventShortName,
                                @RequestBody List<ExtensionMetadataValue> toUpdate, Principal principal) {
        Organization org = organizationRepository.getById(orgId);
        ensureOrganization(principal, org);
        var event = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventShortName).orElseThrow(IllegalStateException::new);
        ensureEventInOrganization(org, event);
        String pattern = generatePatternFrom(event);
        ensureIdsArePresent(toUpdate, extensionService.getConfigurationParametersFor(pattern, pattern, "EVENT"));
        extensionService.bulkUpdateEventSettings(org, event, toUpdate);
    }

    @DeleteMapping("/setting/organization/{orgId}/event/{shortName}/{id}")
    public void deleteEventSettingValue(@PathVariable("orgId") int orgId, @PathVariable("shortName") String eventShortName, @PathVariable("id") int id, Principal principal) {
        Organization org = organizationRepository.getById(orgId);
        ensureOrganization(principal, org);
        var event = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventShortName).orElseThrow(IllegalStateException::new);
        ensureEventInOrganization(org, event);
        extensionService.deleteSettingValue(id, generatePatternFrom(event));
    }

    private static String generatePatternFrom(EventAndOrganizationId event) {
        return String.format("-%d-%d", event.getOrganizationId(), event.getId());
    }

    //check that the ids are coherent
    private static void ensureIdsArePresent(List<ExtensionMetadataValue> toUpdate, List<ExtensionParameterMetadataAndValue> system) {
        Set<Integer> validIds = system.stream().map(ExtensionParameterMetadataAndValue::getId).collect(Collectors.toSet());
        Set<Integer> toUpdateIds = toUpdate.stream().map(ExtensionMetadataValue::getId).collect(Collectors.toSet());
        if(!validIds.containsAll(toUpdateIds)) {
            throw new IllegalStateException();
        }
    }

    //

    @GetMapping("/log")
    public PageAndContent<List<ExtensionLog>> getLog(@RequestParam(required = false, name = "path") String path,
                                                     @RequestParam(required = false, name = "name") String name,
                                                     @RequestParam(required = false, name = "type") ExtensionLog.Type type,
                                                     @RequestParam(required = false, name = "page", defaultValue = "0") Integer page, Principal principal) {
        ensureAdmin(principal);
        final int pageSize = 50;
        Pair<List<ExtensionLog>, Integer> res = extensionService.getLog(StringUtils.trimToNull(path), StringUtils.trimToNull(name), type, pageSize, (page == null ? 0 : page) * pageSize);
        return new PageAndContent<>(res.getLeft(), res.getRight());
    }

    private void ensureAdmin(Principal principal) {
        Validate.isTrue(userManager.isAdmin(userManager.findUserByUsername(principal.getName())));
    }

    private void ensureOrganization(Principal principal, Organization organization) {
        User user = userManager.findUserByUsername(principal.getName());
        Validate.isTrue(userManager.isOwnerOfOrganization(user, organization.getId()));
    }

    private static void ensureEventInOrganization(Organization organization, EventAndOrganizationId event) {
        Validate.isTrue(organization.getId() == event.getOrganizationId());
    }
}

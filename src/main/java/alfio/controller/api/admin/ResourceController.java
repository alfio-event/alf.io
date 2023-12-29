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

import alfio.controller.support.TemplateProcessor;
import alfio.manager.*;
import alfio.manager.i18n.MessageSourceManager;
import alfio.model.*;
import alfio.model.modification.UploadBase64FileModification;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ClockProvider;
import alfio.util.LocaleUtil;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import com.samskivert.mustache.MustacheException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/admin/api")
@Log4j2
@AllArgsConstructor
public class ResourceController {


    private static final String TIMEZONE = "Europe/Zurich";
    private final UploadedResourceManager uploadedResourceManager;
    private final EventRepository eventRepository;
    private final MessageSourceManager messageSourceManager;
    private final TemplateManager templateManager;
    private final OrganizationRepository organizationRepository;
    private final FileUploadManager fileUploadManager;
    private final ExtensionManager extensionManager;
    private final ClockProvider clockProvider;
    private final SubscriptionManager subscriptionManager;
    private final AccessService accessService;


    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleSyntaxError(Exception ex) {
        log.warn("Exception in resource controller", ex);
        Optional<String> cause = Optional.ofNullable(ex.getCause())
            .filter(e -> e instanceof MustacheException || e instanceof TemplateProcessor.TemplateAccessException)
            .map(Throwable::getMessage);
        return cause.orElse("Something went wrong. Please check the syntax and retry");
    }

    @GetMapping("/overridable-template/")
    public List<TemplateResource> getOverridableTemplates() {
        return Stream.of(TemplateResource.values()).filter(TemplateResource::overridable).collect(Collectors.toList());
    }

    @GetMapping("/overridable-template/{name}/{locale}")
    public void getTemplate(@PathVariable("name") TemplateResource name, @PathVariable("locale") String locale, HttpServletResponse response) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (InputStream is = new ClassPathResource(name.classPath()).getInputStream()) {
            is.transferTo(os);
        }
        Locale loc = LocaleUtil.forLanguageTag(locale);
        String template = os.toString(StandardCharsets.UTF_8);

        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().print(TemplateManager.translate(template, loc, messageSourceManager.getRootMessageSource()));
    }

    @PostMapping("/overridable-template/{name}/{locale}/preview")
    public void previewTemplate(@PathVariable("name") TemplateResource name, @PathVariable("locale") String locale,
                                @RequestParam(required = false, value = "organizationId") Integer organizationId,
                                @RequestParam(required = false, value = "eventId") Integer eventId,
                                @RequestParam(required = false, value = "subscriptionDescriptorId") UUID subscriptionDescriptorId,
                                @RequestBody UploadBase64FileModification template,
                                Principal principal,
                                HttpServletResponse response) throws IOException {


        Locale loc = LocaleUtil.forLanguageTag(locale);

        if (organizationId != null) {
            PurchaseContext purchaseContext = getPurchaseContext(organizationId, eventId, subscriptionDescriptorId, principal, name);
            if (eventId != null || subscriptionDescriptorId != null) {
                accessService.checkPurchaseContextOwnership(principal, organizationId, eventId, subscriptionDescriptorId);
            } else {
                accessService.checkOrganizationOwnership(principal, organizationId);
            }
            Organization organization = organizationRepository.getById(organizationId);
            Optional<TemplateResource.ImageData> image = TemplateProcessor.extractImageModel(purchaseContext, fileUploadManager);
            Map<String, Object> model = name.prepareSampleModel(organization, purchaseContext, image);
            String renderedTemplate = templateManager.renderString(purchaseContext, template.getFileAsString(), model, loc, name.getTemplateOutput());
            if(MediaType.TEXT_PLAIN_VALUE.equals(name.getRenderedContentType()) || TemplateResource.MULTIPART_ALTERNATIVE_MIMETYPE.equals(name.getRenderedContentType())) {
                response.addHeader("Content-Disposition", "attachment; filename="+name.name()+".txt");
                response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                response.setCharacterEncoding("UTF-8");
                try(OutputStream os = response.getOutputStream()) {
                    StreamUtils.copy(renderedTemplate,StandardCharsets.UTF_8, os);
                }
            } else if (MediaType.APPLICATION_PDF_VALUE.equals(name.getRenderedContentType())) {
                try (OutputStream os = response.getOutputStream()) {
                    response.setContentType(MediaType.APPLICATION_PDF_VALUE);
                    response.addHeader("Content-Disposition", "attachment; filename="+name.name()+".pdf");
                    TemplateProcessor.renderToPdf(renderedTemplate, os, extensionManager, purchaseContext);
                }
            } else {
                throw new IllegalStateException("cannot enter here!");
            }
        }
    }

    private PurchaseContext getPurchaseContext(int organizationId,
                                               Integer eventId,
                                               UUID subscriptionDescriptorId,
                                               Principal principal,
                                               TemplateResource templateResource) {
        if (templateResource.getPurchaseContextType() == PurchaseContext.PurchaseContextType.event) {
            return getEvent(organizationId, eventId, principal);
        }
        return getSubscriptionDescriptor(organizationId, subscriptionDescriptorId, principal);
    }

    private SubscriptionDescriptor getSubscriptionDescriptor(int organizationId, UUID subscriptionDescriptorId, Principal principal) {
        if (subscriptionDescriptorId != null) {
            return subscriptionManager.getSubscriptionById(subscriptionDescriptorId).orElseThrow();
        }

        Function<String, Map<String, String>> contentProducer = prefix -> ContentLanguage.ALL_LANGUAGES.stream()
            .map(cl -> Map.entry(cl.getLanguage(), cl.getDisplayLanguage() + " " + prefix))
            .reduce(new HashMap<String, String>(), (map, entry) -> {
                map.put(entry.getKey(), entry.getValue());
                return map;
            }, (map1, map2) -> {
                map1.putAll(map2);
                return map1;
            });
        checkAccess(organizationId, principal);
        var zoneId = ZoneId.of(TIMEZONE);
        return new SubscriptionDescriptor(UUID.randomUUID(),
            contentProducer.apply("title"),
            contentProducer.apply("description"),
            -1,
            ZonedDateTime.now(clockProvider.withZone(zoneId)),
            ZonedDateTime.now(clockProvider.withZone(zoneId)),
            ZonedDateTime.now(clockProvider.withZone(zoneId)).plusDays(1),
            100,
            new BigDecimal("7.7"),
            PriceContainer.VatStatus.INCLUDED,
            "CHF",
            true,
            organizationId,
            1,
            SubscriptionDescriptor.SubscriptionValidityType.NOT_SET,
            null,
            -1,
            ZonedDateTime.now(clockProvider.withZone(zoneId)),
            ZonedDateTime.now(clockProvider.withZone(zoneId)).plusDays(1),
            SubscriptionDescriptor.SubscriptionUsageType.ONCE_PER_EVENT,
            "https://alf.io",
            "https://alf.io",
            null,
            List.of(PaymentProxy.STRIPE.name()),
            "42",
            TIMEZONE,
            true
        );
    }

    private Event getEvent(Integer organizationId, Integer eventId, Principal principal) {
        Event event;
        if (eventId != null) {
            checkAccess(organizationId, eventId, principal);
            event = eventRepository.findById(eventId);
        } else {
            checkAccess(organizationId, principal);
            var zoneId = ZoneId.of(TIMEZONE);
            event = new Event(-1, Event.EventFormat.IN_PERSON, "TEST", "TEST", "TEST", "0", "0", ZonedDateTime.now(clockProvider.withZone(zoneId)),
                ZonedDateTime.now(clockProvider.withZone(zoneId)), TIMEZONE, "http://localhost", "http://localhost", null,
                "http://localhost", null, null, "CHF", BigDecimal.TEN, null, "42", organizationId,
                ContentLanguage.ALL_LANGUAGES_IDENTIFIER, 0, PriceContainer.VatStatus.NONE, "1", Event.Status.PUBLIC);
        }
        return event;
    }


    //------------------

    @GetMapping("/resource/")
    public List<UploadedResource> findAll(Principal principal) {
        checkAccess(principal);
        return uploadedResourceManager.findAll();
    }

    @GetMapping("/resource-organization/{organizationId}")
    public List<UploadedResource> findAllForOrganization(@PathVariable("organizationId") int organizationId, Principal principal) {
        checkAccess(organizationId, principal);
        return uploadedResourceManager.findAll(organizationId);
    }

    @GetMapping("/resource-event/{organizationId}/{eventId}")
    public List<UploadedResource> findAllForEvent(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId, Principal principal) {
        checkAccess(organizationId, eventId, principal);
        return uploadedResourceManager.findAll(organizationId, eventId);
    }


    //------------------

    @GetMapping("/resource/{name}/metadata")
    public ResponseEntity<UploadedResource> getMetadata(@PathVariable("name") String name, Principal principal) {
        checkAccess(principal);
        if (uploadedResourceManager.hasResource(name)) {
            return new ResponseEntity<>(uploadedResourceManager.get(name), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/resource-organization/{organizationId}/{name}/metadata")
    public ResponseEntity<UploadedResource> getMetadata(@PathVariable("organizationId") int organizationId, @PathVariable("name") String name, Principal principal) {
        checkAccess(organizationId, principal);
        if (uploadedResourceManager.hasResource(organizationId, name)) {
            return new ResponseEntity<>(uploadedResourceManager.get(organizationId, name), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/resource-event/{organizationId}/{eventId}/{name}/metadata")
    public ResponseEntity<UploadedResource> getMetadata(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId, @PathVariable("name") String name, Principal principal) {
        checkAccess(organizationId, eventId, principal);
        if (uploadedResourceManager.hasResource(organizationId, eventId, name)) {
            return new ResponseEntity<>(uploadedResourceManager.get(organizationId, eventId, name), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    //------------------

    @PostMapping("/resource/")
    public void uploadFile(@RequestBody UploadBase64FileModification upload, Principal principal) {
        checkAccess(principal);
        uploadedResourceManager.saveResource(upload).orElseThrow(IllegalArgumentException::new);
    }

    @PostMapping("/resource-organization/{organizationId}/")
    public void uploadFile(@PathVariable("organizationId") int organizationId, @RequestBody UploadBase64FileModification upload, Principal principal) {
        checkAccess(organizationId, principal);
        uploadedResourceManager.saveResource(organizationId, upload).orElseThrow(IllegalArgumentException::new);
    }

    @PostMapping("/resource-event/{organizationId}/{eventId}/")
    public void uploadFile(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId, @RequestBody UploadBase64FileModification upload, Principal principal) {
        checkAccess(organizationId, eventId, principal);
        uploadedResourceManager.saveResource(organizationId, eventId, upload).orElseThrow(IllegalArgumentException::new);
    }

    //------------------
    @GetMapping("/resource/{name:.*}")
    public void outputContent(@PathVariable("name") String name, Principal principal, HttpServletResponse response) throws IOException {
        checkAccess(principal);
        if (!uploadedResourceManager.hasResource(name)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        UploadedResource metadata = uploadedResourceManager.get(name);
        try (OutputStream os = response.getOutputStream()) {
            response.setContentType(metadata.getContentType());
            response.setContentLength(metadata.getContentSize());
            uploadedResourceManager.outputResource(name, os);
        }
    }

    @GetMapping("/resource-organization/{organizationId}/{name:.*}")
    public void outputContent(@PathVariable("organizationId") int organizationId, @PathVariable("name") String name, Principal principal, HttpServletResponse response) throws IOException {
        checkAccess(organizationId, principal);
        if (!uploadedResourceManager.hasResource(organizationId, name)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        UploadedResource metadata = uploadedResourceManager.get(organizationId, name);
        try (OutputStream os = response.getOutputStream()) {
            response.setContentType(metadata.getContentType());
            response.setContentLength(metadata.getContentSize());
            uploadedResourceManager.outputResource(organizationId, name, os);
        }
    }

    @GetMapping("/resource-event/{organizationId}/{eventId}/{name:.*}")
    public void outputContent(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId, @PathVariable("name") String name, Principal principal, HttpServletResponse response) throws IOException {
        checkAccess(organizationId, eventId, principal);
        if (!uploadedResourceManager.hasResource(organizationId, eventId, name)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        UploadedResource metadata = uploadedResourceManager.get(organizationId, eventId, name);
        try (OutputStream os = response.getOutputStream()) {
            response.setContentType(metadata.getContentType());
            response.setContentLength(metadata.getContentSize());
            uploadedResourceManager.outputResource(organizationId, eventId, name, os);
        }
    }

    //------------------

    @DeleteMapping("/resource/{name:.*}")
    public void delete(@PathVariable("name") String name, Principal principal) {
        checkAccess(principal);
        uploadedResourceManager.deleteResource(name);
    }

    @DeleteMapping("/resource-organization/{organizationId}/{name:.*}")
    public void delete(@PathVariable("organizationId") int organizationId, @PathVariable("name") String name, Principal principal) {
        checkAccess(organizationId, principal);
        uploadedResourceManager.deleteResource(organizationId, name);
    }

    @DeleteMapping("/resource-event/{organizationId}/{eventId}/{name:.*}")
    public void delete(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId, @PathVariable("name") String name, Principal principal) {
        checkAccess(organizationId, eventId, principal);
        uploadedResourceManager.deleteResource(organizationId, eventId, name);
    }

    //------------------

    private void checkAccess(Principal principal) {
        accessService.ensureAdmin(principal);
    }

    private void checkAccess(int organizationId, Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
    }

    private void checkAccess(int organizationId, int eventId, Principal principal) {
        accessService.checkEventOwnership(principal, eventId, organizationId);
    }
}

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

import alfio.config.Initializer;
import alfio.config.WebSecurityConfig;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.ContentLanguage;
import alfio.model.EventDescription;
import alfio.model.FileBlobMetadata;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.EventRepository;
import alfio.repository.FileUploadRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.MustacheCustomTag;
import alfio.util.RequestUtils;
import alfio.util.TemplateManager;
import ch.digitalfondue.jfiveparse.*;
import lombok.AllArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static alfio.model.system.ConfigurationKeys.ENABLE_CAPTCHA_FOR_LOGIN;
import static alfio.model.system.ConfigurationKeys.RECAPTCHA_API_KEY;

@Controller
@AllArgsConstructor
public class IndexController {

    private static final String REDIRECT_ADMIN = "redirect:/admin/";
    private static final String TEXT_HTML_CHARSET_UTF_8 = "text/html;charset=UTF-8";
    private static final String UTF_8 = "UTF-8";


    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Document INDEX_PAGE;
    private static final Document OPEN_GRAPH_PAGE;

    static {
        try (var idxIs = new ClassPathResource("alfio-public-frontend-index.html").getInputStream();
             var idxOpenGraph = new ClassPathResource("alfio-public-frontend-index.html").getInputStream()) {
            var parser = new Parser();
            INDEX_PAGE = parser.parse(new InputStreamReader(idxIs, StandardCharsets.UTF_8));
            OPEN_GRAPH_PAGE = parser.parse(new InputStreamReader(idxOpenGraph, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private final ConfigurationManager configurationManager;
    private final EventRepository eventRepository;
    private final Environment environment;
    private final UserManager userManager;
    private final TemplateManager templateManager;
    private final FileUploadRepository fileUploadRepository;
    private final MessageSourceManager messageSourceManager;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final OrganizationRepository organizationRepository;


    @RequestMapping(value = "/", method = RequestMethod.HEAD)
    public ResponseEntity<String> replyToProxy() {
        return ResponseEntity.ok("Up and running!");
    }

    @GetMapping("/healthz")
    public ResponseEntity<String> replyToK8s() {
        return ResponseEntity.ok("Up and running!");
    }


    //url defined in the angular app in app-routing.module.ts
    /**
    <pre>
    { path: '', component: EventListComponent, canActivate: [LanguageGuard] },
    { path: 'event/:eventShortName', component: EventDisplayComponent, canActivate: [LanguageGuard] },
    { path: 'event/:eventShortName/reservation/:reservationId', component: ReservationComponent, canActivate: [LanguageGuard, ReservationGuard], children: [
        { path: 'book', component: BookingComponent, canActivate: [ReservationGuard] },
        { path: 'overview', component: OverviewComponent, canActivate: [ReservationGuard] },
        { path: 'waitingPayment', redirectTo: 'waiting-payment'},
        { path: 'waiting-payment', component: OfflinePaymentComponent, canActivate: [ReservationGuard] },
        { path: 'processing-payment', component: ProcessingPaymentComponent, canActivate: [ReservationGuard] },
        { path: 'success', component: SuccessComponent, canActivate: [ReservationGuard]},
        { path: 'not-found', component: NotFoundComponent, canActivate: [ReservationGuard]}
    ]},
    { path: 'event/:eventShortName/ticket/:ticketId/view', component: ViewTicketComponent, canActivate: [LanguageGuard] }
    </pre>

     */
    @GetMapping({
        "/",
        "/event/{eventShortName}",
        "/event/{eventShortName}/reservation/{reservationId}",
        "/event/{eventShortName}/reservation/{reservationId}/book",
        "/event/{eventShortName}/reservation/{reservationId}/overview",
        "/event/{eventShortName}/reservation/{reservationId}/waitingPayment",
        "/event/{eventShortName}/reservation/{reservationId}/waiting-payment",
        "/event/{eventShortName}/reservation/{reservationId}/processing-payment",
        "/event/{eventShortName}/reservation/{reservationId}/success",
        "/event/{eventShortName}/reservation/{reservationId}/not-found",
        "/event/{eventShortName}/ticket/{ticketId}/view"
    })
    public void replyToIndex(@PathVariable(value = "eventShortName", required = false) String eventShortName,
                             @RequestHeader(value = "User-Agent", required = false) String userAgent,
                             @RequestParam(value = "lang", required = false) String lang,
                             ServletWebRequest request,
                             HttpServletResponse response) throws IOException {

        response.setContentType(TEXT_HTML_CHARSET_UTF_8);
        response.setCharacterEncoding(UTF_8);
        var nonce = addCspHeader(response);

        if (eventShortName != null && RequestUtils.isSocialMediaShareUA(userAgent) && eventRepository.existsByShortName(eventShortName)) {
            try (var os = response.getOutputStream()) {
                var res = getOpenGraphPage((Document) OPEN_GRAPH_PAGE.cloneNode(true), eventShortName, request, lang);
                os.write(res);
            }
        } else {
            try (var os = response.getOutputStream()) {
                var idx = INDEX_PAGE.cloneNode(true);
                idx.getElementsByTagName("script").forEach(element -> element.setAttribute("nonce", nonce));
                os.write(idx.getOuterHTML().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // see https://github.com/alfio-event/alf.io/issues/708
    // use ngrok to test the preview
    private byte[] getOpenGraphPage(Document eventOpenGraph, String eventShortName, ServletWebRequest request, String lang) {
        var event = eventRepository.findByShortName(eventShortName);
        var locale = RequestUtils.getMatchingLocale(request, event);
        if (lang != null && event.getContentLanguages().stream().map(ContentLanguage::getLanguage).anyMatch(lang::equalsIgnoreCase)) {
            locale = Locale.forLanguageTag(lang);
        }

        var baseUrl = configurationManager.getForSystem(ConfigurationKeys.BASE_URL).getRequiredValue();

        var title = messageSourceManager.getMessageSourceForEvent(event).getMessage("event.get-your-ticket-for", new String[] {event.getDisplayName()}, locale);

        var head = eventOpenGraph.getElementsByTagName("head").get(0);

        eventOpenGraph.getElementsByTagName("html").get(0).setAttribute("lang", locale.getLanguage());

        //

        getMetaElement(eventOpenGraph, "name", "twitter:image").setAttribute("content", baseUrl + "/file/" + event.getFileBlobId());
        //

        eventOpenGraph.getElementsByTagName("title").get(0).appendChild(new Text(title));
        getMetaElement(eventOpenGraph, "property", "og:title").setAttribute("content", title);
        getMetaElement(eventOpenGraph, "property","og:image").setAttribute("content", baseUrl + "/file/" + event.getFileBlobId());

        var eventDesc = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale.toLanguageTag()).orElse("").trim();
        var firstLine = Pattern.compile("\n").splitAsStream(MustacheCustomTag.renderToTextCommonmark(eventDesc)).findFirst().orElse("");
        getMetaElement(eventOpenGraph, "property","og:description").setAttribute("content", firstLine);


        var org = organizationRepository.getById(event.getOrganizationId());
        var author = String.format("%s <%s>", org.getName(), org.getEmail());
        getMetaElement(eventOpenGraph, "name", "author").setAttribute("content", author);

        fileUploadRepository.findById(event.getFileBlobId()).ifPresent(metadata -> {
            var attributes = metadata.getAttributes();
            if (attributes.containsKey(FileBlobMetadata.ATTR_IMG_HEIGHT) && attributes.containsKey(FileBlobMetadata.ATTR_IMG_WIDTH)) {
                head.appendChild(buildMetaTag("og:image:width", attributes.get(FileBlobMetadata.ATTR_IMG_WIDTH)));
                head.appendChild(buildMetaTag("og:image:height", attributes.get(FileBlobMetadata.ATTR_IMG_HEIGHT)));
            }
        });

        return eventOpenGraph.getOuterHTML().getBytes(StandardCharsets.UTF_8);
    }

    private static Element buildMetaTag(String propertyValue, String contentValue) {
        var meta = new Element("meta");
        meta.setAttribute("property", propertyValue);
        meta.setAttribute("content", contentValue);
        return meta;
    }

    private static Element getMetaElement(Document document, String attrName, String propertyValue) {
        return (Element) document.getAllNodesMatching(Selector.select().element("meta").attrValEq(attrName, propertyValue).toMatcher(), true).get(0);
    }

    @GetMapping(value = {
        "/event/{eventShortName}/code/{code}",
        "/e/{eventShortName}/c/{code}"})
    public String redirectCode(@PathVariable("eventShortName") String eventName,
                             @PathVariable("code") String code) {
        return "redirect:" + UriComponentsBuilder.fromPath("/api/v2/public/event/{eventShortName}/code/{code}")
            .build(Map.of("eventShortName", eventName, "code", code))
            .toString();
    }

    @GetMapping("/e/{eventShortName}")
    public String redirectEvent(@PathVariable("eventShortName") String eventName) {
        return "redirect:" + UriComponentsBuilder.fromPath("/event/{eventShortName}").build(Map.of("eventShortName", eventName)).toString();
    }

    // login related
    @GetMapping("/authentication")
    public void getLoginPage(@RequestParam(value="failed", required = false) String failed, @RequestParam(value = "recaptchaFailed", required = false) String recaptchaFailed,
                             Model model,
                             Principal principal,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        if(principal != null) {
            response.sendRedirect("/admin/");
            return;
        }
        model.addAttribute("failed", failed != null);
        model.addAttribute("recaptchaFailed", recaptchaFailed != null);
        model.addAttribute("hasRecaptchaApiKey", false);

        //
        model.addAttribute("request", request);
        model.addAttribute("demoModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO)));
        model.addAttribute("devModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEV)));
        model.addAttribute("prodModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE)));
        model.addAttribute(WebSecurityConfig.CSRF_PARAM_NAME, request.getAttribute(CsrfToken.class.getName()));
        //

        var configuration = configurationManager.getFor(EnumSet.of(RECAPTCHA_API_KEY, ENABLE_CAPTCHA_FOR_LOGIN), ConfigurationLevel.system());

        configuration.get(RECAPTCHA_API_KEY).getValue()
            .filter(key -> configuration.get(ENABLE_CAPTCHA_FOR_LOGIN).getValueAsBooleanOrDefault(true))
            .ifPresent(key -> {
                model.addAttribute("hasRecaptchaApiKey", true);
                model.addAttribute("recaptchaApiKey", key);
            });
        try (var os = response.getOutputStream()) {
            response.setContentType(TEXT_HTML_CHARSET_UTF_8);
            response.setCharacterEncoding(UTF_8);
            var nonce = addCspHeader(response);
            model.addAttribute("nonce", nonce);
            templateManager.renderHtml(new ClassPathResource("alfio/web-templates/login.ms"), model.asMap(), os);
        }
    }

    @PostMapping("/authenticate")
    public String doLogin() {
        return REDIRECT_ADMIN;
    }
    //


    // admin index
    @GetMapping("/admin")
    public void adminHome(Model model, @Value("${alfio.version}") String version, HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        model.addAttribute("alfioVersion", version);
        model.addAttribute("username", principal.getName());
        model.addAttribute("basicConfigurationNeeded", configurationManager.isBasicConfigurationNeeded());
        model.addAttribute("isAdmin", principal.getName().equals("admin"));
        model.addAttribute("isOwner", userManager.isOwner(userManager.findUserByUsername(principal.getName())));
        //
        model.addAttribute("request", request);
        model.addAttribute("demoModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO)));
        model.addAttribute("devModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEV)));
        model.addAttribute("prodModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE)));
        model.addAttribute(WebSecurityConfig.CSRF_PARAM_NAME, request.getAttribute(CsrfToken.class.getName()));
        //

        try (var os = response.getOutputStream()) {
            response.setContentType(TEXT_HTML_CHARSET_UTF_8);
            response.setCharacterEncoding(UTF_8);
            var nonce = addCspHeader(response);
            model.addAttribute("nonce", nonce);
            templateManager.renderHtml(new ClassPathResource("alfio/web-templates/admin-index.ms"), model.asMap(), os);
        }
    }


    private static String getNonce() {
        var nonce = new byte[16]; //128 bit = 16 bytes
        SECURE_RANDOM.nextBytes(nonce);
        return Hex.encodeHexString(nonce);
    }

    public String addCspHeader(HttpServletResponse response) {

        String nonce = getNonce();

        String reportUri = "";

        var conf = configurationManager.getFor(List.of(ConfigurationKeys.SECURITY_CSP_REPORT_ENABLED, ConfigurationKeys.SECURITY_CSP_REPORT_URI), ConfigurationLevel.system());

        boolean enabledReport = conf.get(ConfigurationKeys.SECURITY_CSP_REPORT_ENABLED).getValueAsBooleanOrDefault(false);
        if (enabledReport) {
            reportUri = " report-uri " + conf.get(ConfigurationKeys.SECURITY_CSP_REPORT_URI).getValueOrDefault("/report-csp-violation");
        }
        //
        // https://csp.withgoogle.com/docs/strict-csp.html
        // with base-uri set to 'self'

        response.addHeader("Content-Security-Policy", "object-src 'none'; "+
            "script-src 'nonce-" + nonce + "' 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:; " +
            "base-uri 'self'; "
            + reportUri);

        return nonce;
    }
}

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
import alfio.config.authentication.support.OpenIdAlfioAuthentication;
import alfio.controller.api.v2.model.Language;
import alfio.controller.api.v2.user.support.EventLoader;
import alfio.manager.PurchaseContextManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.openid.OpenIdAuthenticationManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Role;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.Json;
import alfio.util.MustacheCustomTag;
import alfio.util.RequestUtils;
import alfio.util.TemplateManager;
import ch.digitalfondue.jfiveparse.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.HttpUtils.APPLICATION_JSON;

@Controller
@AllArgsConstructor
@Slf4j
public class IndexController {

    private static final String REDIRECT_ADMIN = "redirect:/admin/";
    private static final String TEXT_HTML_CHARSET_UTF_8 = "text/html;charset=UTF-8";
    private static final String UTF_8 = "UTF-8";


    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Document INDEX_PAGE;
    private static final Document OPEN_GRAPH_PAGE;
    private static final String REDIRECT_PREFIX = "redirect:";
    private static final String NOT_FOUND = "not-found";
    private static final String EVENT_SHORT_NAME = "eventShortName";
    private static final String NONCE = "nonce";
    private static final String CONTENT = "content";
    private static final String PROPERTY = "property";

    static {
        try (var idxIs = new ClassPathResource("alfio-public-frontend-index.html").getInputStream();
             var idxOpenIs = new ClassPathResource("alfio/web-templates/event-open-graph-page.html").getInputStream();
             var idxIsR = new InputStreamReader(idxIs, StandardCharsets.UTF_8);
             var idxOpenGraphReader = new InputStreamReader(idxOpenIs, StandardCharsets.UTF_8)) {
            INDEX_PAGE = JFiveParse.parse(idxIsR);
            OPEN_GRAPH_PAGE = JFiveParse.parse(idxOpenGraphReader);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private final ConfigurationManager configurationManager;
    private final EventRepository eventRepository;
    private final Environment environment;
    private final TemplateManager templateManager;
    private final FileUploadRepository fileUploadRepository;
    private final MessageSourceManager messageSourceManager;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EventLoader eventLoader;
    private final PurchaseContextManager purchaseContextManager;
    private final Json json;


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
     <pre>{@code
     { path: '', component: EventListComponent, canActivate: [LanguageGuard] },
     { path: 'event/:eventShortName', component: EventDisplayComponent, canActivate: [EventGuard, LanguageGuard] },
     { path: 'event/:eventShortName/poll', loadChildren: () => import('./poll/poll.module').then(m => m.PollModule), canActivate: [EventGuard, LanguageGuard] },
     { path: 'event/:eventShortName/reservation/:reservationId', children: [
     { path: 'book', component: BookingComponent, canActivate: reservationsGuard },
     { path: 'overview', component: OverviewComponent, canActivate: reservationsGuard },
     { path: 'waitingPayment', redirectTo: 'waiting-payment'},
     { path: 'waiting-payment', component: OfflinePaymentComponent, canActivate: reservationsGuard },
     { path: 'deferred-payment', component: DeferredOfflinePaymentComponent, canActivate: reservationsGuard },
     { path: 'processing-payment', component: ProcessingPaymentComponent, canActivate: reservationsGuard },
     { path: 'success', component: SuccessComponent, canActivate: reservationsGuard },
     { path: 'not-found', component: NotFoundComponent, canActivate: reservationsGuard },
     { path: 'error', component: ErrorComponent, canActivate: reservationsGuard },
     ]},
     { path: 'event/:eventShortName/ticket/:ticketId', children: [
     { path: 'view', component: ViewTicketComponent, canActivate: [EventGuard, LanguageGuard] },
     { path: 'update', component: UpdateTicketComponent, canActivate: [EventGuard, LanguageGuard] }
     ]}
    }
     </pre>
     Poll routing:
     <pre>{@code
     { path: '', component: PollComponent, children: [
     {path: '', component: PollSelectionComponent },
     {path: ':pollId', component: DisplayPollComponent }
     ]}
    }
     </pre>

     */
    @GetMapping({
        "/",
        "/o/*",
        "/o/*/events-all",
        "/events-all",
        "/event/{eventShortName}",
        "/event/{eventShortName}/reservation/{reservationId}/book",
        "/event/{eventShortName}/reservation/{reservationId}/overview",
        "/event/{eventShortName}/reservation/{reservationId}/waitingPayment",
        "/event/{eventShortName}/reservation/{reservationId}/waiting-payment",
        "/event/{eventShortName}/reservation/{reservationId}/deferred-payment",
        "/event/{eventShortName}/reservation/{reservationId}/processing-payment",
        "/event/{eventShortName}/reservation/{reservationId}/success",
        "/event/{eventShortName}/reservation/{reservationId}/not-found",
        "/event/{eventShortName}/reservation/{reservationId}/error",
        "/event/{eventShortName}/ticket/{ticketId}/view",
        "/event/{eventShortName}/ticket/{ticketId}/update",
        "/event/{eventShortName}/ticket/{ticketId}/check-in/{ticketCodeHash}/waiting-room",
        //
        // subscription
        "/subscriptions-all",
        "/o/*/subscriptions-all",
        "/subscription/{subscriptionId}",
        "/subscription/{subscriptionId}/reservation/{reservationId}/book",
        "/subscription/{subscriptionId}/reservation/{reservationId}/overview",
        "/subscription/{subscriptionId}/reservation/{reservationId}/waitingPayment",
        "/subscription/{subscriptionId}/reservation/{reservationId}/waiting-payment",
        "/subscription/{subscriptionId}/reservation/{reservationId}/deferred-payment",
        "/subscription/{subscriptionId}/reservation/{reservationId}/processing-payment",
        "/subscription/{subscriptionId}/reservation/{reservationId}/success",
        "/subscription/{subscriptionId}/reservation/{reservationId}/not-found",
        "/subscription/{subscriptionId}/reservation/{reservationId}/error",
        // poll
        "/event/{eventShortName}/poll",
        "/event/{eventShortName}/poll/{pollId}",
        // user
        "/my-orders",
        "/my-profile",
    })
    public void replyToIndex(@PathVariable(value = EVENT_SHORT_NAME, required = false) String eventShortName,
                             @PathVariable(value = "subscriptionId", required = false) String subscriptionId,
                             @RequestHeader(value = "User-Agent", required = false) String userAgent,
                             @RequestParam(value = "lang", required = false) String lang,
                             ServletWebRequest request,
                             HttpServletResponse response,
                             HttpSession session) throws IOException {

        response.setContentType(TEXT_HTML_CHARSET_UTF_8);
        response.setCharacterEncoding(UTF_8);
        var nonce = addCspHeader(response, detectConfigurationLevel(eventShortName, subscriptionId), true);

        if (eventShortName != null && RequestUtils.isSocialMediaShareUA(userAgent) && eventRepository.existsByShortName(eventShortName)) {
            try (var os = response.getOutputStream(); var osw = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                var res = getOpenGraphPage((Document) OPEN_GRAPH_PAGE.cloneNode(true), eventShortName, request, lang);
                JFiveParse.serialize(res, osw);
            }
        } else {
            try (var os = response.getOutputStream(); var osw = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                var baseCustomCss = configurationManager.getForSystem(BASE_CUSTOM_CSS).getValueOrNull();
                var idx = INDEX_PAGE.cloneNode(true);
                if(session.getAttribute(OpenIdAuthenticationManager.USER_SIGNED_UP) != null) {
                    Optional.ofNullable(IterableUtils.get(idx.getElementsByTagName("html"), 0))
                            .ifPresent(html -> html.setAttribute("data-signed-up", "true"));
                    session.removeAttribute(OpenIdAuthenticationManager.USER_SIGNED_UP);
                }
                idx.getElementsByTagName("script").forEach(element -> element.setAttribute(NONCE, nonce));
                var head = idx.getElementsByTagName("head").get(0);
                head.appendChild(buildScripTag(json.asJsonString(configurationManager.getInfo(session)), APPLICATION_JSON, "preload-info", null));
                if (baseCustomCss != null) {
                    var style = new Element("style");
                    style.setAttribute("type", "text/css");
                    style.appendChild(new Text(baseCustomCss));
                    head.appendChild(style);
                }
                preloadTranslations(eventShortName, request, session, eventLoader, head, messageSourceManager, idx, json, lang);
                JFiveParse.serialize(idx, osw);
            }
        }
    }

    @GetMapping("/event/{eventShortName}/reservation/{reservationId}")
    public String redirectEventToReservation(@PathVariable(value = EVENT_SHORT_NAME) String eventShortName,
                                             @PathVariable(value = "reservationId") String reservationId,
                                             @RequestParam(value = "subscription", required = false) String subscriptionId) {
        if (eventRepository.existsByShortName(eventShortName)) {
            var reservationStatusUrlSegment = ticketReservationRepository.findOptionalStatusAndValidationById(reservationId)
                .map(IndexController::reservationStatusToUrlMapping).orElse(NOT_FOUND);
            return REDIRECT_PREFIX + UriComponentsBuilder.fromPath("/event/{eventShortName}/reservation/{reservationId}/{status}")
                // if subscription param is present, we forward it to the reservation resource
                .queryParamIfPresent("subscription", Optional.ofNullable(StringUtils.trimToNull(subscriptionId)))
                .buildAndExpand(Map.of(EVENT_SHORT_NAME, eventShortName, "reservationId", reservationId, "status",reservationStatusUrlSegment))
                .toUriString();
        } else {
            return "redirect:/";
        }
    }

    @GetMapping("/subscription/{subscriptionId}/reservation/{reservationId}")
    public String redirectSubscriptionToReservation(@PathVariable("subscriptionId") String subscriptionId, @PathVariable("reservationId") String reservationId) {
        if (subscriptionRepository.existsById(UUID.fromString(subscriptionId))) {
            var reservationStatusUrlSegment = ticketReservationRepository.findOptionalStatusAndValidationById(reservationId)
                .map(IndexController::reservationStatusToUrlMapping).orElse(NOT_FOUND);

            return REDIRECT_PREFIX + UriComponentsBuilder.fromPath("/subscription/{subscriptionId}/reservation/{reservationId}/{status}")
                .buildAndExpand(Map.of("subscriptionId", subscriptionId, "reservationId", reservationId, "status",reservationStatusUrlSegment))
                .toUriString();
        } else {
            return "redirect:/";
        }
    }

    static void preloadTranslations(String eventShortName,
                                    ServletWebRequest request,
                                    HttpSession session,
                                    EventLoader eventLoader,
                                    Element head,
                                    MessageSourceManager messageSourceManager,
                                    Node idx,
                                    Json json,
                                    String lang) {
        String preloadLang = Objects.requireNonNullElse(lang, "en");
        if (eventShortName != null) {
            var eventInfoOptional = eventLoader.loadEventInfo(eventShortName, session);
            if (eventInfoOptional.isPresent()) {
                var ev = eventInfoOptional.get();
                head.appendChild(buildScripTag(json.asJsonString(ev), APPLICATION_JSON, "preload-event", eventShortName));
                preloadLang = getMatchingLocale(request, ev.getContentLanguages().stream().map(Language::getLocale).collect(Collectors.toList()), lang).getLanguage();
            }
        }
        head.appendChild(buildScripTag(json.asJsonString(messageSourceManager.getBundleAsMap("alfio.i18n.public", true, preloadLang)), "application/json", "preload-bundle", preloadLang));
        // add fallback in english
        if (!"en".equals(preloadLang)) {
            head.appendChild(buildScripTag(json.asJsonString(messageSourceManager.getBundleAsMap("alfio.i18n.public", true, "en")), "application/json", "preload-bundle", "en"));
        }
        var htmlElement = IterableUtils.get(idx.getElementsByTagName("html"), 0);
        htmlElement.setAttribute("lang", preloadLang);
    }

    private static Element buildScripTag(String content, String type, String id, String param) {
        var e = new Element("script");
        e.appendChild(new Text(content));
        e.setAttribute("type", type);
        e.setAttribute("id", id);
        if (param != null) {
            e.setAttribute("data-param", param);
        }
        return e;
    }

    private static String reservationStatusToUrlMapping(TicketReservationStatusAndValidation status) {
        switch (status.getStatus()) {
            case PENDING: return Boolean.TRUE.equals(status.getValidated()) ? "overview" : "book";
            case COMPLETE:
            case FINALIZING:
                return "success";
            case OFFLINE_PAYMENT:
            case OFFLINE_FINALIZING:
                return "waiting-payment";
            case DEFERRED_OFFLINE_PAYMENT: return "deferred-payment";
            case EXTERNAL_PROCESSING_PAYMENT:
            case WAITING_EXTERNAL_CONFIRMATION: return "processing-payment";
            case IN_PAYMENT:
            case STUCK: return "error";
            default: return NOT_FOUND; // <- this may be a little bit aggressive
        }
    }


    /**
     * Return the best matching locale.
     *
     * @param request
     * @param contextLanguages list of languages configured for the event (o other contexts)
     * @param lang override passed as parameter
     * @return
     */
    private static Locale getMatchingLocale(ServletWebRequest request, List<String> contextLanguages, String lang) {
        var locale = RequestUtils.getMatchingLocale(request, contextLanguages);
        if (lang != null && contextLanguages.stream().anyMatch(lang::equalsIgnoreCase)) {
            locale = Locale.forLanguageTag(lang);
        }
        return locale;
    }

    // see https://github.com/alfio-event/alf.io/issues/708
    // use ngrok to test the preview
    private Document getOpenGraphPage(Document eventOpenGraph, String eventShortName, ServletWebRequest request, String lang) {
        var event = eventRepository.findByShortName(eventShortName);
        var locale = getMatchingLocale(request, event.getContentLanguages().stream().map(ContentLanguage::getLanguage).collect(Collectors.toList()), lang);

        var baseUrl = configurationManager.getForSystem(ConfigurationKeys.BASE_URL).getRequiredValue();

        var title = messageSourceManager.getMessageSourceFor(event).getMessage("event.get-your-ticket-for", new String[] {event.getDisplayName()}, locale);

        var head = eventOpenGraph.getElementsByTagName("head").get(0);

        eventOpenGraph.getElementsByTagName("html").get(0).setAttribute("lang", locale.getLanguage());

        //

        getMetaElement(eventOpenGraph, "name", "twitter:image").setAttribute(CONTENT, baseUrl + "/file/" + event.getFileBlobId());
        //

        eventOpenGraph.getElementsByTagName("title").get(0).appendChild(new Text(title));
        getMetaElement(eventOpenGraph, PROPERTY, "og:title").setAttribute(CONTENT, title);
        getMetaElement(eventOpenGraph, PROPERTY,"og:image").setAttribute(CONTENT, baseUrl + "/file/" + event.getFileBlobId());

        var eventDesc = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale.toLanguageTag()).orElse("").trim();
        var firstLine = Pattern.compile("\n").splitAsStream(MustacheCustomTag.renderToTextCommonmark(eventDesc)).findFirst().orElse("");
        getMetaElement(eventOpenGraph, PROPERTY,"og:description").setAttribute(CONTENT, firstLine);


        var org = organizationRepository.getById(event.getOrganizationId());
        var author = String.format("%s <%s>", org.getName(), org.getEmail());
        getMetaElement(eventOpenGraph, "name", "author").setAttribute(CONTENT, author);

        fileUploadRepository.findById(event.getFileBlobId()).ifPresent(metadata -> {
            var attributes = metadata.getAttributes();
            if (attributes.containsKey(FileBlobMetadata.ATTR_IMG_HEIGHT) && attributes.containsKey(FileBlobMetadata.ATTR_IMG_WIDTH)) {
                head.appendChild(buildMetaTag("og:image:width", attributes.get(FileBlobMetadata.ATTR_IMG_WIDTH)));
                head.appendChild(buildMetaTag("og:image:height", attributes.get(FileBlobMetadata.ATTR_IMG_HEIGHT)));
            }
        });

        return eventOpenGraph;
    }

    private static Element buildMetaTag(String propertyValue, String contentValue) {
        var meta = new Element("meta");
        meta.setAttribute(PROPERTY, propertyValue);
        meta.setAttribute(CONTENT, contentValue);
        return meta;
    }

    private static Element getMetaElement(Document document, String attrName, String propertyValue) {
        return (Element) document.getAllNodesMatching(Selector.select().element("meta").attrValEq(attrName, propertyValue).toMatcher(), true).get(0);
    }

    @GetMapping(value = {
        "/event/{eventShortName}/code/{code}",
        "/e/{eventShortName}/c/{code}"})
    public String redirectCode(@PathVariable(EVENT_SHORT_NAME) String eventName,
                               @PathVariable("code") String code) {
        return REDIRECT_PREFIX + UriComponentsBuilder.fromPath("/api/v2/public/event/{eventShortName}/code/{code}")
            .build(Map.of(EVENT_SHORT_NAME, eventName, "code", code));
    }

    @GetMapping("/e/{eventShortName}")
    public String redirectEvent(@PathVariable(EVENT_SHORT_NAME) String eventName) {
        return REDIRECT_PREFIX + UriComponentsBuilder.fromPath("/event/{eventShortName}").build(Map.of(EVENT_SHORT_NAME, eventName));
    }

    // login related
    @GetMapping("/authentication")
    public void getLoginPage(@RequestParam(value="failed", required = false) String failed,
                             @RequestParam(value = "recaptchaFailed", required = false) String recaptchaFailed,
                             Model model,
                             Principal principal,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             @Value("${alfio.version}") String version) throws IOException {

        if(principal != null) {
            response.sendRedirect("/admin/");
            return;
        }
        model.addAttribute("failed", failed != null);
        model.addAttribute("recaptchaFailed", recaptchaFailed != null);
        model.addAttribute("hasRecaptchaApiKey", false);

        //
        addCommonModelAttributes(model, request, version);
        model.addAttribute("request", request);

        //

        var configuration = configurationManager.getFor(EnumSet.of(RECAPTCHA_API_KEY, ENABLE_CAPTCHA_FOR_LOGIN), ConfigurationLevel.system());

        configuration.get(RECAPTCHA_API_KEY).getValue()
            .filter(key -> configuration.get(ENABLE_CAPTCHA_FOR_LOGIN).getValueAsBooleanOrDefault())
            .ifPresent(key -> {
                model.addAttribute("hasRecaptchaApiKey", true);
                model.addAttribute("recaptchaApiKey", key);
            });
        try (var os = response.getOutputStream()) {
            response.setContentType(TEXT_HTML_CHARSET_UTF_8);
            response.setCharacterEncoding(UTF_8);
            var nonce = addCspHeader(response, false);
            model.addAttribute(NONCE, nonce);
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

        boolean isDBAuthentication = !(principal instanceof OpenIdAlfioAuthentication);
        model.addAttribute("isDBAuthentication", isDBAuthentication);
        if (!isDBAuthentication) {
            String idpLogoutRedirectionUrl = ((OpenIdAlfioAuthentication) SecurityContextHolder.getContext().getAuthentication()).getIdpLogoutRedirectionUrl();
            model.addAttribute("idpLogoutRedirectionUrl", idpLogoutRedirectionUrl);
        } else {
            model.addAttribute("idpLogoutRedirectionUrl", null);
        }

        Collection<String> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
            .stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());

        boolean isAdmin = authorities.contains(Role.ADMIN.getRoleName());
        model.addAttribute("isOwner", isAdmin || authorities.contains(Role.OWNER.getRoleName()));
        model.addAttribute("isAdmin", isAdmin);
        //
        addCommonModelAttributes(model, request, version);
        model.addAttribute("displayProjectBanner", isAdmin && configurationManager.getForSystem(SHOW_PROJECT_BANNER).getValueAsBooleanOrDefault());
        //

        try (var os = response.getOutputStream()) {
            response.setContentType(TEXT_HTML_CHARSET_UTF_8);
            response.setCharacterEncoding(UTF_8);
            var nonce = addCspHeader(response, false);
            model.addAttribute(NONCE, nonce);
            templateManager.renderHtml(new ClassPathResource("alfio/web-templates/admin-index.ms"), model.asMap(), os);
        }
    }

    private void addCommonModelAttributes(Model model, HttpServletRequest request, String version) {
        var contextPath = StringUtils.appendIfMissing(request.getContextPath(), "/") + version;
        model.addAttribute("contextPath", contextPath);
        model.addAttribute("demoModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO)));
        model.addAttribute("devModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEV)));
        model.addAttribute("prodModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE)));
        model.addAttribute(WebSecurityConfig.CSRF_PARAM_NAME, request.getAttribute(CsrfToken.class.getName()));
    }


    private static String getNonce() {
        var nonce = new byte[16]; //128 bit = 16 bytes
        SECURE_RANDOM.nextBytes(nonce);
        return Hex.encodeHexString(nonce);
    }

    public String addCspHeader(HttpServletResponse response, boolean embeddingSupported) {
        return addCspHeader(response, ConfigurationLevel.system(), embeddingSupported);
    }

    public String addCspHeader(HttpServletResponse response, ConfigurationLevel configurationLevel, boolean embeddingSupported) {

        var nonce = getNonce();

        String reportUri = "";

        var conf = configurationManager.getFor(List.of(SECURITY_CSP_REPORT_ENABLED, SECURITY_CSP_REPORT_URI, EMBED_ALLOWED_ORIGINS), configurationLevel);

        boolean enabledReport = conf.get(SECURITY_CSP_REPORT_ENABLED).getValueAsBooleanOrDefault();
        if (enabledReport) {
            reportUri = " report-uri " + conf.get(SECURITY_CSP_REPORT_URI).getValueOrDefault("/report-csp-violation");
        }
        //
        // https://csp.withgoogle.com/docs/strict-csp.html
        // with base-uri set to 'self'

        var frameAncestors = "'none'";
        var allowedContainer = conf.get(EMBED_ALLOWED_ORIGINS).getValueOrNull();
        if (embeddingSupported && StringUtils.isNotBlank(allowedContainer)) {
            var splitHosts = allowedContainer.split("[,\n]");
            frameAncestors = String.join(" ", splitHosts);
            // IE11
            response.addHeader("X-Frame-Options", "ALLOW-FROM "+splitHosts[0]);
        } else {
            response.addHeader("X-Frame-Options", "DENY");
        }

        response.addHeader("Content-Security-Policy", "object-src 'none'; "+
            "script-src 'strict-dynamic' 'nonce-" + nonce + "' 'unsafe-inline' http: https:; " +
            "base-uri 'self'; " +
            "frame-ancestors " + frameAncestors + "; "
            + reportUri);

        return nonce;
    }

    private ConfigurationLevel detectConfigurationLevel(String eventShortName, String subscriptionId) {
        return purchaseContextManager.detectConfigurationLevel(eventShortName, subscriptionId)
            .orElseGet(ConfigurationLevel::system);
    }
}

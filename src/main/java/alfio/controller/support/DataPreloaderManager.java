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
package alfio.controller.support;

import alfio.config.authentication.support.OpenIdPrincipal;
import alfio.controller.api.support.TicketHelper;
import alfio.controller.api.v2.model.Language;
import alfio.controller.api.v2.user.support.EventLoader;
import alfio.manager.PurchaseContextManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.ContentLanguage;
import alfio.model.Event;
import alfio.model.EventDescription;
import alfio.model.FileBlobMetadata;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.EventRepository;
import alfio.repository.FileUploadRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ClockProvider;
import alfio.util.Json;
import alfio.util.MustacheCustomTag;
import alfio.util.RequestUtils;
import ch.digitalfondue.jfiveparse.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.collections4.IterableUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static alfio.controller.Constants.*;
import static alfio.controller.Constants.CONTENT;
import static alfio.model.system.ConfigurationKeys.BASE_CUSTOM_CSS;
import static alfio.util.HttpUtils.APPLICATION_JSON;
import static java.util.Objects.requireNonNull;

@Component
public class DataPreloaderManager {

    private final ConfigurationManager configurationManager;
    private final CsrfTokenRepository csrfTokenRepository;
    private final EventLoader eventLoader;
    private final CSPConfigurer cspConfigurer;
    private final PurchaseContextManager purchaseContextManager;
    private final EventRepository eventRepository;
    private final MessageSourceManager messageSourceManager;
    private final OrganizationRepository organizationRepository;
    private final FileUploadRepository fileUploadRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final Json json;

    public DataPreloaderManager(ConfigurationManager configurationManager, CsrfTokenRepository csrfTokenRepository, EventLoader eventLoader, CSPConfigurer cspConfigurer, PurchaseContextManager purchaseContextManager, EventRepository eventRepository, MessageSourceManager messageSourceManager, OrganizationRepository organizationRepository, FileUploadRepository fileUploadRepository, EventDescriptionRepository eventDescriptionRepository, Json json) {
        this.configurationManager = configurationManager;
        this.csrfTokenRepository = csrfTokenRepository;
        this.eventLoader = eventLoader;
        this.cspConfigurer = cspConfigurer;
        this.purchaseContextManager = purchaseContextManager;
        this.eventRepository = eventRepository;
        this.messageSourceManager = messageSourceManager;
        this.organizationRepository = organizationRepository;
        this.fileUploadRepository = fileUploadRepository;
        this.eventDescriptionRepository = eventDescriptionRepository;
        this.json = json;
    }

    @Transactional
    public Document generateIndexDocument(String eventShortName,
                                          String subscriptionId,
                                          String userAgent,
                                          String lang,
                                          ServletWebRequest request,
                                          HttpServletResponse response,
                                          HttpSession session,
                                          Authentication authentication,
                                          Document openGraphPage,
                                          Document indexPage) {
        var nonce = cspConfigurer.addCspHeader(response, detectConfigurationLevel(eventShortName, subscriptionId), true);
        if (eventShortName != null && RequestUtils.isSocialMediaShareUA(userAgent) && eventRepository.existsByShortName(eventShortName)) {
            return getOpenGraphPage((Document) openGraphPage.cloneNode(true), eventShortName, request, lang);
        } else {
            var baseCustomCss = configurationManager.getForSystem(BASE_CUSTOM_CSS).getValueOrNull();
            Document idx = (Document) indexPage.cloneNode(true);
            if (authentication instanceof OAuth2AuthenticationToken oauth
                && oauth.getPrincipal() instanceof OpenIdPrincipal principal
                && principal.isSignedUp()
                && session.isNew()) {
                Optional.ofNullable(IterableUtils.get(idx.getElementsByTagName("html"), 0))
                    .ifPresent(html -> html.setAttribute("data-signed-up", "true"));
            }
            idx.getElementsByTagName("script").forEach(element -> element.setAttribute(NONCE, nonce));
            var head = idx.getElementsByTagName("head").get(0);
            head.appendChild(buildScripTag(json.asJsonString(configurationManager.getInfo(session)), APPLICATION_JSON, "preload-info", null));
            var httpServletRequest = requireNonNull(request.getNativeRequest(HttpServletRequest.class));
            head.appendChild(buildMetaTag("GID", request.getSessionId()));
            var csrf = csrfTokenRepository.loadToken(httpServletRequest);
            if (csrf == null) {
                csrf = csrfTokenRepository.generateToken(httpServletRequest);
            }
            head.appendChild(buildMetaTag("XSRF_TOKEN", csrf.getToken()));
            if (baseCustomCss != null) {
                var style = new Element("style");
                style.setAttribute("type", "text/css");
                style.appendChild(new Text(baseCustomCss));
                head.appendChild(style);
            }
            head.appendChild(buildMetaTag("authentication-enabled", Boolean.toString(configurationManager.isPublicOpenIdEnabled())));
            preloadEventData(eventShortName, request, session, eventLoader, head, messageSourceManager, idx, json, lang);
            return idx;
        }
    }

    private ConfigurationLevel detectConfigurationLevel(String eventShortName, String subscriptionId) {
        return purchaseContextManager.detectConfigurationLevel(eventShortName, subscriptionId)
            .orElseGet(ConfigurationLevel::system);
    }

    // see https://github.com/alfio-event/alf.io/issues/708
    // use ngrok to test the preview
    private Document getOpenGraphPage(Document eventOpenGraph, String eventShortName, ServletWebRequest request, String lang) {
        var event = eventRepository.findByShortName(eventShortName);
        var locale = getMatchingLocale(request, event.getContentLanguages().stream().map(ContentLanguage::getLanguage).toList(), lang);

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
                head.appendChild(buildOGMetaTag("og:image:width", attributes.get(FileBlobMetadata.ATTR_IMG_WIDTH)));
                head.appendChild(buildOGMetaTag("og:image:height", attributes.get(FileBlobMetadata.ATTR_IMG_HEIGHT)));
            }
        });

        return eventOpenGraph;
    }

    private static Element buildOGMetaTag(String propertyValue, String contentValue) {
        return buildMetaTag(PROPERTY, propertyValue, contentValue);
    }

    private static Element buildMetaTag(String name, String content) {
        return buildMetaTag("name", name, content);
    }

    private static Element buildMetaTag(String property, String propertyValue, String content) {
        var meta = new Element("meta");
        meta.setAttribute(property, propertyValue);
        meta.setAttribute(CONTENT, content);
        return meta;
    }

    private static Element getMetaElement(Document document, String attrName, String propertyValue) {
        return (Element) document.getAllNodesMatching(Selector.select().element("meta").attrValEq(attrName, propertyValue).toMatcher(), true).get(0);
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

    private static Element buildScripTag(String content, String type, String id, String param) {
        var encodedContent = UriUtils.encodeFragment(content, StandardCharsets.UTF_8);
        var e = new Element("script");
        e.appendChild(new Text(encodedContent));
        e.setAttribute("type", type);
        e.setAttribute("id", id);
        if (param != null) {
            e.setAttribute("data-param", param);
        }
        return e;
    }

    public static void preloadEventData(String eventShortName,
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
                preloadLang = getMatchingLocale(request, ev.getContentLanguages().stream().map(Language::getLocale).toList(), lang).getLanguage();
                if (ZonedDateTime.now(ClockProvider.clock()).isAfter(((Event)ev.purchaseContext()).getEnd())) {
                    // event is over.
                    head.appendChild(buildMetaTag("robots", "noindex"));
                }
            }
        }
        head.appendChild(buildScripTag(json.asJsonString(messageSourceManager.getBundleAsMap("alfio.i18n.public", true, preloadLang, MessageSourceManager.PUBLIC_FRONTEND)), "application/json", "preload-bundle", preloadLang));
        head.appendChild(buildScripTag(countriesForVatAsJson(json, preloadLang), "application/json", "preload-vat-countries", preloadLang));
        head.appendChild(buildScripTag(countriesAsJson(json, preloadLang), "application/json", "preload-countries", preloadLang));
        // add fallback in english
        if (!"en".equals(preloadLang)) {
            head.appendChild(buildScripTag(json.asJsonString(messageSourceManager.getBundleAsMap("alfio.i18n.public", true, "en", MessageSourceManager.PUBLIC_FRONTEND)), "application/json", "preload-bundle", "en"));
        }
        var htmlElement = IterableUtils.get(idx.getElementsByTagName("html"), 0);
        htmlElement.setAttribute("lang", preloadLang);
    }

    private static String countriesForVatAsJson(Json json, String preloadLang) {
        return json.asJsonString(TicketHelper.getSortedLocalizedVatCountries(Locale.forLanguageTag(preloadLang)));
    }

    private static String countriesAsJson(Json json, String preloadLang) {
        return json.asJsonString(TicketHelper.getSortedLocalizedCountries(Locale.forLanguageTag(preloadLang)));
    }

}

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
package alfio.util;

import alfio.model.EventFeed;
import alfio.model.EventFeedEntry;
import org.apache.commons.lang3.StringUtils;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Serializes an {@link EventFeed} as iCalendar or RSS 2.0.
 */
public final class EventFeedWriter {

    private static final DateTimeFormatter RSS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
    // characters not allowed by XML 1.0, e.g. control characters pasted from word processors
    private static final Pattern INVALID_XML_CHARS = Pattern.compile("[^\\x09\\x0A\\x0D\\x20-\\x{D7FF}\\x{E000}-\\x{FFFD}\\x{10000}-\\x{10FFFF}]");

    private EventFeedWriter() {
    }

    public static Optional<byte[]> toICal(EventFeed feed) {
        var ical = EventUtil.newCalendar();
        ical.setExperimentalProperty("X-WR-CALNAME", feed.title());
        for (var entry : feed.entries()) {
            var vEvent = EventUtil.buildVEvent(entry.event(), null, entry.description(), null);
            // the public event page is a stable identifier, so calendar clients can update existing entries
            vEvent.setUid(entry.eventUrl());
            vEvent.setUrl(entry.eventUrl());
            ical.addEvent(vEvent);
        }
        return EventUtil.writeCalendar(ical, "feed " + feed.title());
    }

    public static byte[] toRss(EventFeed feed) {
        var out = new ByteArrayOutputStream();
        try {
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(out, StandardCharsets.UTF_8.name());
            writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            writer.writeStartElement("rss");
            writer.writeAttribute("version", "2.0");
            writer.writeStartElement("channel");
            writeElement(writer, "title", feed.title());
            writeElement(writer, "link", feed.link());
            writeElement(writer, "description", feed.title());
            for (var entry : feed.entries()) {
                writer.writeStartElement("item");
                writeElement(writer, "title", entry.event().getDisplayName());
                writeElement(writer, "link", entry.eventUrl());
                writer.writeStartElement("guid");
                writer.writeAttribute("isPermaLink", "true");
                writer.writeCharacters(sanitize(entry.eventUrl()));
                writer.writeEndElement();
                writeElement(writer, "description", rssDescription(entry));
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("was not able to generate RSS for feed " + feed.title(), e);
        }
        return out.toByteArray();
    }

    private static void writeElement(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
        writer.writeStartElement(name);
        writer.writeCharacters(sanitize(value));
        writer.writeEndElement();
    }

    private static String sanitize(String value) {
        return INVALID_XML_CHARS.matcher(StringUtils.defaultString(value)).replaceAll("");
    }

    private static String rssDescription(EventFeedEntry entry) {
        var event = entry.event();
        var dates = event.getBegin().format(RSS_DATE_FORMAT) + " - " + event.getEnd().format(RSS_DATE_FORMAT);
        var location = EventUtil.isAccessOnline(null, event) ? null : StringUtils.trimToNull(event.getLocation());
        var text = StringUtils.trimToNull(MustacheCustomTag.renderToTextCommonmark(entry.description()));
        return String.join("\n\n", Stream.of(dates, location, text).filter(Objects::nonNull).toList());
    }
}

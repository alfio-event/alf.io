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
package alfio.e2e;

import alfio.model.transaction.PaymentProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static alfio.e2e.AdminConsole.elapsedMillis;

/**
 * Check-in on a big event: the attendees are imported from a CSV, then a group of them is checked in.
 * <p>
 * Importing tens of thousands of attendees takes a while, so the test runs only if E2E_PERFORMANCE_TEST=true.
 * The number of attendees can be changed through E2E_PERFORMANCE_ATTENDEES (default: 50'000).
 */
@EnabledIfEnvironmentVariable(named = "E2E_PERFORMANCE_TEST", matches = "true")
class CheckInPerformanceE2ETest extends BaseE2ETest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckInPerformanceE2ETest.class);
    private static final int ATTENDEES = Integer.parseInt(System.getenv().getOrDefault("E2E_PERFORMANCE_ATTENDEES", "50000"));
    // the last attendees of the CSV have an e-mail address on a different domain
    private static final int GROUP_SIZE = 50;
    private static final String GROUP_DOMAIN = "example.org";
    private static final List<String> COUNTRIES = List.of("CH", "IT", "DE", "FR", "AT", "US");
    private static final List<String> T_SHIRT_SIZES = List.of("S", "M", "L", "XL");
    private static final Duration IMPORT_TIMEOUT = Duration.ofHours(3);
    private static final Duration LOAD_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration ACTION_TIMEOUT = Duration.ofMinutes(1);

    /**
     * The attendees are imported: any payment method would do
     */
    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Override
    protected int maxTickets() {
        return ATTENDEES;
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.HOURS)
    void checkInAGroupOnABigEvent() {
        adminConsole.login();
        adminConsole.addAttendeeField(slug, "company", "input:text", List.of());
        adminConsole.addAttendeeField(slug, "country", "country", List.of());
        adminConsole.addAttendeeField(slug, "tShirtSize", "select", T_SHIRT_SIZES);
        adminConsole.importAttendees(slug, CATEGORY_NAME, attendeesCsv(), ATTENDEES, IMPORT_TIMEOUT);

        //
        // the operator filters by domain while the attendees are being downloaded
        var start = System.nanoTime();
        var checkIn = adminConsole.openCheckIn(slug);
        adminConsole.waitForAttendeesDisplayed(checkIn, LOAD_TIMEOUT);
        var firstAttendees = elapsedMillis(start);
        var filteredWhileLoading = adminConsole.isLoadingAttendees(checkIn);
        adminConsole.searchAttendees(checkIn, GROUP_DOMAIN);
        adminConsole.waitForCheckInCounts(checkIn, GROUP_SIZE, 0, LOAD_TIMEOUT);
        adminConsole.waitForAttendeesDownloaded(checkIn, LOAD_TIMEOUT);
        var allAttendees = elapsedMillis(start);
        adminConsole.waitForAcquiredTickets(checkIn, IMPORT_TIMEOUT);

        // now that all the attendees are there, measure a search on the full list
        adminConsole.searchAttendees(checkIn, "");
        start = System.nanoTime();
        adminConsole.waitForCheckInCounts(checkIn, ATTENDEES, 0, ACTION_TIMEOUT);
        var clearSearch = elapsedMillis(start);
        adminConsole.searchAttendees(checkIn, GROUP_DOMAIN);
        start = System.nanoTime();
        adminConsole.waitForCheckInCounts(checkIn, GROUP_SIZE, 0, ACTION_TIMEOUT);
        var groupSearch = elapsedMillis(start);

        //
        // the operator checks in the whole group. The filter is kept after each check-in
        var checkInTimes = new ArrayList<Long>();
        for (int i = 1; i <= GROUP_SIZE; i++) {
            start = System.nanoTime();
            adminConsole.checkInFirstPendingAttendee(checkIn);
            adminConsole.waitForCheckInCounts(checkIn, GROUP_SIZE - i, i, ACTION_TIMEOUT);
            checkInTimes.add(elapsedMillis(start));
        }

        //
        // then a single attendee
        var attendee = email(ATTENDEES / 2);
        adminConsole.searchAttendees(checkIn, attendee);
        start = System.nanoTime();
        adminConsole.waitForCheckInCounts(checkIn, 1, 0, ACTION_TIMEOUT);
        var singleSearch = elapsedMillis(start);
        start = System.nanoTime();
        adminConsole.checkInFirstPendingAttendee(checkIn);
        adminConsole.waitForCheckInCounts(checkIn, 0, 1, ACTION_TIMEOUT);
        var singleCheckIn = elapsedMillis(start);

        var stats = checkInTimes.stream().mapToLong(Long::longValue).summaryStatistics();
        LOGGER.info("""

            Check-in performance, {} attendees
            first attendees displayed after:   {} ms
            filter typed while loading:        {}
            all attendees downloaded after:    {} ms
            clear search (full list):          {} ms
            search "{}" ({} results):  {} ms
            search single attendee:            {} ms
            check-in of the group:             avg {} ms, min {} ms, max {} ms, median {} ms
            check-in single attendee:          {} ms
            (search times start at the end of typing: the search field waits 300 ms after the last keystroke)
            """,
            ATTENDEES, firstAttendees, filteredWhileLoading, allAttendees, clearSearch, GROUP_DOMAIN, GROUP_SIZE, groupSearch,
            singleSearch, Math.round(stats.getAverage()), stats.getMin(), stats.getMax(), median(checkInTimes), singleCheckIn);
    }

    private static long median(List<Long> values) {
        var sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    private static String email(int index) {
        var domain = index > ATTENDEES - GROUP_SIZE ? GROUP_DOMAIN : "example.com";
        return String.format(Locale.ROOT, "attendee-%06d@%s", index, domain);
    }

    /**
     * firstName, lastName, email, lang, then the attendee fields
     */
    private static Path attendeesCsv() {
        var lines = new ArrayList<String>(ATTENDEES + 1);
        lines.add("firstName,lastName,email,lang,company,country,tShirtSize");
        for (int i = 1; i <= ATTENDEES; i++) {
            lines.add(String.join(",",
                "Attendee",
                String.format(Locale.ROOT, "Number %06d", i),
                email(i),
                "en",
                "Company " + (i % 500),
                COUNTRIES.get(i % COUNTRIES.size()),
                T_SHIRT_SIZES.get(i % T_SHIRT_SIZES.size())));
        }
        try {
            var file = Files.createTempFile("e2e-attendees", ".csv");
            file.toFile().deleteOnExit();
            return Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

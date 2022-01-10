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

import alfio.model.AdditionalService;
import alfio.model.AdditionalServiceText;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.result.ValidationResult;
import alfio.test.util.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidatorTest {

    private static final DateTimeModification VALID_EXPIRATION;
    private static final DateTimeModification VALID_INCEPTION;

    static {
        var clock = TestUtil.clockProvider().getClock();
        VALID_EXPIRATION = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(clock).plusHours(1L));
        VALID_INCEPTION = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(clock).minusDays(1L));
    }

    private EventModification eventModification;
    private Errors errors;
    private TicketCategoryModification ticketCategoryModification;
    private final EventModification.AdditionalServiceText title = new EventModification.AdditionalServiceText(0, "it", "titolo", AdditionalServiceText.TextType.TITLE);
    private final EventModification.AdditionalServiceText description = new EventModification.AdditionalServiceText(0, "it", "descrizione", AdditionalServiceText.TextType.DESCRIPTION);

    @BeforeEach
    void init() {
        eventModification = mock(EventModification.class);
        errors = new MapBindingResult(new HashMap<>(), "test");
        ticketCategoryModification = mock(TicketCategoryModification.class);
        when(ticketCategoryModification.getInception()).thenReturn(VALID_INCEPTION);
        when(ticketCategoryModification.getExpiration()).thenReturn(VALID_EXPIRATION);
        when(ticketCategoryModification.getName()).thenReturn("name");
    }

    @Test
    void successfulDescriptionValidation() {
        when(eventModification.getDescription()).thenReturn(Map.of("it", "12345", "en", "1234"));
        Validator.validateEventHeader(Optional.empty(), eventModification, 5, errors);
        assertFalse(errors.hasFieldErrors("description"));
    }

    @Test
    void failedDescriptionValidation() {
        when(eventModification.getDescription()).thenReturn(Map.of("it", "12345", "en", "1234"));
        Validator.validateEventHeader(Optional.empty(), eventModification, 4, errors);
        assertTrue(errors.hasFieldErrors("description"));
    }

    @Test
    void successfulCategoryDescriptionValidation() {
        when(ticketCategoryModification.getDescription()).thenReturn(Map.of("it", "12345", "en", "1234"));
        Validator.validateCategory(ticketCategoryModification, errors, 5);
        assertFalse(errors.hasFieldErrors("description"));
    }

    @Test
    void successfulCategoryDescriptionValidationWhenEmpty() {
        when(ticketCategoryModification.getDescription()).thenReturn(Map.of());
        Validator.validateCategory(ticketCategoryModification, errors, 5);
        assertFalse(errors.hasFieldErrors("description"));
    }

    @Test
    void failedCategoryDescriptionValidation() {
        when(ticketCategoryModification.getDescription()).thenReturn(Map.of("it", "12345", "en", "1234"));
        Validator.validateCategory(ticketCategoryModification, errors, 4);
        assertTrue(errors.hasFieldErrors("description"));
    }

    @Test
    void testValidationSuccess() {
        EventModification.AdditionalService valid1 = new EventModification.AdditionalService(0, BigDecimal.ZERO, false, 0, -1, 1, VALID_INCEPTION, VALID_EXPIRATION, null, AdditionalService.VatType.NONE, Collections.emptyList(), singletonList(title), singletonList(description), AdditionalService.AdditionalServiceType.DONATION, null);
        EventModification.AdditionalService valid2 = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, VALID_INCEPTION, VALID_EXPIRATION, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(description), AdditionalService.AdditionalServiceType.DONATION, null);
        assertTrue(Stream.of(valid1, valid2).map(as -> Validator.validateAdditionalService(as, errors)).allMatch(ValidationResult::isSuccess));
        assertFalse(errors.hasFieldErrors());
    }

    @Test
    void testValidationErrorExpirationBeforeInception() {
        EventModification.AdditionalService invalid = new EventModification.AdditionalService(0, BigDecimal.ZERO, false, 0, -1, 1, VALID_EXPIRATION, VALID_INCEPTION, null, AdditionalService.VatType.NONE, Collections.emptyList(), singletonList(title), singletonList(description), AdditionalService.AdditionalServiceType.DONATION, null);
        assertFalse(Validator.validateAdditionalService(invalid, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    void testValidationErrorInceptionNull() {
        EventModification.AdditionalService invalid = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, null, VALID_EXPIRATION, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(description), AdditionalService.AdditionalServiceType.DONATION, null);
        assertFalse(Validator.validateAdditionalService(invalid, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    void testValidationExpirationNull() {
        EventModification.AdditionalService invalid = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, VALID_INCEPTION, null, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(description), AdditionalService.AdditionalServiceType.DONATION, null);
        assertFalse(Validator.validateAdditionalService(invalid, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    void testValidationInceptionExpirationNull() {
        EventModification.AdditionalService invalid = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, null, null, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(description), AdditionalService.AdditionalServiceType.DONATION, null);
        assertFalse(Validator.validateAdditionalService(invalid, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    void testValidationFailedDescriptionsDontMatchTitles() {
        EventModification.AdditionalService invalid = new EventModification.AdditionalService(0, BigDecimal.ZERO, false, 0, -1, 1, VALID_INCEPTION, VALID_EXPIRATION, null, AdditionalService.VatType.NONE, Collections.emptyList(), emptyList(), singletonList(description), AdditionalService.AdditionalServiceType.DONATION, null);
        EventModification.AdditionalService valid = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, VALID_INCEPTION, VALID_EXPIRATION, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(description), AdditionalService.AdditionalServiceType.DONATION, null);
        assertTrue(Validator.validateAdditionalService(valid, errors).isSuccess());
        assertFalse(Validator.validateAdditionalService(invalid, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    void testValidationFailedDescription() {
        EventModification.AdditionalService invalid1 = new EventModification.AdditionalService(0, BigDecimal.ZERO, false, 0, -1, 1, VALID_INCEPTION, VALID_EXPIRATION, null, AdditionalService.VatType.NONE, Collections.emptyList(), emptyList(), singletonList(description), AdditionalService.AdditionalServiceType.DONATION, null);//English is required here
        EventModification.AdditionalService invalid2 = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, VALID_INCEPTION, VALID_EXPIRATION, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(new EventModification.AdditionalServiceText(0, "en", "", AdditionalServiceText.TextType.DESCRIPTION)), AdditionalService.AdditionalServiceType.DONATION, null);
        assertFalse(Validator.validateAdditionalService(invalid1, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));

        assertFalse(Validator.validateAdditionalService(invalid2, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    void accessTypeMandatoryForHybridEvent() {
        when(eventModification.getFormat()).thenReturn(Event.EventFormat.HYBRID);
        when(ticketCategoryModification.getTicketAccessType()).thenReturn(TicketCategory.TicketAccessType.INHERIT);
        when(eventModification.getTicketCategories()).thenReturn(List.of(ticketCategoryModification));
        Validator.validateTicketCategories(eventModification, errors);
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("ticketCategories"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "test@example.org",
        "test@sub.example.org",
        "test_test-test@sub.sub.example-example.org",
        "test@example.เน็ต.ไทย"
    })
    void validCanonicalEmails(String address) {
        assertTrue(Validator.isCanonicalMailAddress(address));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "test@example.o",
        "test@example.org.",
        "test@example.org<",
        "test@localhost",
        "test_test-test@sub.sub.o"
    })
    void invalidCanonicalEmails(String address) {
        assertFalse(Validator.isCanonicalMailAddress(address));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "test@example.org",
        "test@localhost",
        "test_test-test@sub.sub.example-example.org",
        "test@example.เน็ต.ไทย"
    })
    void validEmails(String address) {
        assertTrue(Validator.isEmailValid(address));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "test.example.org",
        "test_test-test"
    })
    void invalidEmails(String address) {
        assertFalse(Validator.isEmailValid(address));
    }
}
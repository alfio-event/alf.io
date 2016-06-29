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
import alfio.model.ContentLanguage;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ValidatorTest {

    private static final DateTimeModification VALID_EXPIRATION = DateTimeModification.fromZonedDateTime(ZonedDateTime.now().plusHours(1L));
    private static final DateTimeModification VALID_INCEPTION = DateTimeModification.fromZonedDateTime(ZonedDateTime.now().minusDays(1L));

    @Mock
    private EventModification eventModification;
    private Errors errors;
    private EventModification.AdditionalServiceText title = new EventModification.AdditionalServiceText(0, "it", "titolo", AdditionalServiceText.TextType.TITLE);
    private EventModification.AdditionalServiceText description = new EventModification.AdditionalServiceText(0, "it", "descrizione", AdditionalServiceText.TextType.DESCRIPTION);

    @Before
    public void init() {
        errors = new MapBindingResult(new HashMap<>(), "test");
    }

    @Test
    public void testValidationSuccess() {
        EventModification.AdditionalService valid1 = new EventModification.AdditionalService(0, BigDecimal.ZERO, false, 0, -1, 1, VALID_INCEPTION, VALID_EXPIRATION, null, AdditionalService.VatType.NONE, Collections.emptyList(), singletonList(title), singletonList(description));
        EventModification.AdditionalService valid2 = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, VALID_INCEPTION, VALID_EXPIRATION, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(description));
        assertTrue(Stream.of(valid1, valid2).map(as -> Validator.validateAdditionalService(as, errors)).allMatch(ValidationResult::isSuccess));
        assertFalse(errors.hasFieldErrors());
    }

    @Test
    public void testValidationErrorExpirationBeforeInception() {
        EventModification.AdditionalService invalid = new EventModification.AdditionalService(0, BigDecimal.ZERO, false, 0, -1, 1, VALID_EXPIRATION, VALID_INCEPTION, null, AdditionalService.VatType.NONE, Collections.emptyList(), singletonList(title), singletonList(description));
        assertFalse(Validator.validateAdditionalService(invalid, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    public void testValidationErrorInceptionNull() {
        EventModification.AdditionalService invalid = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, null, VALID_EXPIRATION, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(description));
        assertFalse(Validator.validateAdditionalService(invalid, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    public void testValidationExpirationNull() {
        EventModification.AdditionalService invalid = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, VALID_INCEPTION, null, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(description));
        assertFalse(Validator.validateAdditionalService(invalid, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    public void testValidationInceptionExpirationNull() {
        EventModification.AdditionalService invalid = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, null, null, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(description));
        assertFalse(Validator.validateAdditionalService(invalid, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    public void testValidationFailedDescriptionsDontMatchTitles() {
        EventModification.AdditionalService invalid = new EventModification.AdditionalService(0, BigDecimal.ZERO, false, 0, -1, 1, VALID_INCEPTION, VALID_EXPIRATION, null, AdditionalService.VatType.NONE, Collections.emptyList(), emptyList(), singletonList(description));
        EventModification.AdditionalService valid = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, VALID_INCEPTION, VALID_EXPIRATION, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(description));
        assertTrue(Validator.validateAdditionalService(valid, errors).isSuccess());
        assertFalse(Validator.validateAdditionalService(invalid, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }

    @Test
    public void testValidationFailedDescription() {
        when(eventModification.getLocales()).thenReturn(ContentLanguage.ENGLISH.getValue());
        EventModification.AdditionalService invalid1 = new EventModification.AdditionalService(0, BigDecimal.ZERO, false, 0, -1, 1, VALID_INCEPTION, VALID_EXPIRATION, null, AdditionalService.VatType.NONE, Collections.emptyList(), emptyList(), singletonList(description));//English is required here
        EventModification.AdditionalService invalid2 = new EventModification.AdditionalService(0, BigDecimal.ONE, true, 1, 100, 1, VALID_INCEPTION, VALID_EXPIRATION, BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), singletonList(title), singletonList(new EventModification.AdditionalServiceText(0, "en", "", AdditionalServiceText.TextType.DESCRIPTION)));
        assertFalse(Validator.validateAdditionalService(invalid1, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));

        assertFalse(Validator.validateAdditionalService(invalid2, errors).isSuccess());
        assertTrue(errors.hasFieldErrors());
        assertNotNull(errors.getFieldError("additionalServices"));
    }
}
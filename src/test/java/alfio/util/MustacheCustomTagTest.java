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

import com.samskivert.mustache.Template;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Locale;

import static alfio.util.MustacheCustomTag.ADDITIONAL_FIELD_VALUE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class MustacheCustomTagTest {

    private Template.Fragment fragment = Mockito.mock(Template.Fragment.class);
    private Writer out = Mockito.mock(Writer.class);

    @Test
    public void translateCountryCode() {
        assertEquals("Greece", MustacheCustomTag.translateCountryCode("GR", null));
        assertEquals("Greece", MustacheCustomTag.translateCountryCode("EL", null));
        assertEquals("Grecia", MustacheCustomTag.translateCountryCode("EL", Locale.ITALIAN));
    }

    @Test
    public void additionalFieldValueMapIsEmptyOrNull() throws IOException {
        ADDITIONAL_FIELD_VALUE.apply(Collections.emptyMap()).execute(fragment, out);
        ADDITIONAL_FIELD_VALUE.apply(null).execute(fragment, out);
        verifyNoMoreInteractions(fragment, out);
    }

    @Test
    public void additionalFieldValueMapDoesNotContainValue() throws IOException {
        when(fragment.execute()).thenReturn("[not-existing]");
        ADDITIONAL_FIELD_VALUE.apply(Collections.singletonMap("test", "test")).execute(fragment, out);
        verifyNoMoreInteractions(out);
    }

    @Test
    public void additionalFieldValue() throws IOException {
        when(fragment.execute()).thenReturn("[existing]");
        ADDITIONAL_FIELD_VALUE.apply(Collections.singletonMap("existing", "existing value")).execute(fragment, out);
        verify(out).write("existing value");
    }

    @Test
    public void additionalFieldValuePrefix() throws IOException {
        when(fragment.execute()).thenReturn("[prefix!][existing]");
        ADDITIONAL_FIELD_VALUE.apply(Collections.singletonMap("existing", "existing value")).execute(fragment, out);
        verify(out).write("prefix! existing value");
    }

    @Test
    public void additionalFieldValueSuffix() throws IOException {
        when(fragment.execute()).thenReturn("[prefix!][existing][suffix-]");
        ADDITIONAL_FIELD_VALUE.apply(Collections.singletonMap("existing", "existing value")).execute(fragment, out);
        verify(out).write("prefix! existing value suffix-");
    }
}
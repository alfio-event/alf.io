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
package alfio.manager;

import alfio.repository.EventAdminRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class EventNameManagerTest {

    private final EventAdminRepository eventAdminRepository = mock(EventAdminRepository.class);
    private final EventNameManager eventNameManager = new EventNameManager(eventAdminRepository);

    @Test
    public void testSingleWord() {
        assertEquals("myevent", eventNameManager.generateShortName("myEvent"));
    }

    @Test
    public void dashedLowerCaseIfLessThan15Chars() {
        when(eventAdminRepository.existsBySlug("my-event-2015")).thenReturn(false);
        assertEquals("my-event-2015", eventNameManager.generateShortName("my Event 2015"));
    }

    @Test
    public void croppedNameIfMoreThan15Chars() {
        when(eventAdminRepository.existsBySlug("vdt2016")).thenReturn(false);
        when(eventAdminRepository.existsBySlug("vdz2016")).thenReturn(false);
        assertEquals("vdt2016", eventNameManager.generateShortName("Voxxed Days Ticino 2016"));
        assertEquals("vdz2016", eventNameManager.generateShortName("Voxxed Days Zürich 2016"));
    }

    @Test
    public void randomNameIfCroppedNotAvailable() {
        when(eventAdminRepository.existsBySlug("vdt2016")).thenReturn(true);
        when(eventAdminRepository.existsBySlug("vdz2016")).thenReturn(true);
        Assertions.assertNotEquals("vdt2016", eventNameManager.generateShortName("Voxxed Days Ticino 2016"));
        Assertions.assertNotEquals("vdz2016", eventNameManager.generateShortName("Voxxed Days Zürich 2016"));
    }

    @Test
    public void removePunctuation() {
        assertEquals("bigg-i-o-2015", eventNameManager.generateShortName("BigG I/O 2015"));
    }

    @Test
    public void giveUpAfter5Times() {
        when(eventAdminRepository.existsBySlug(anyString())).thenReturn(true);
        assertEquals("", eventNameManager.generateShortName("Pippo Baudo and Friends 2017"));
        verify(eventAdminRepository, times(6)).existsBySlug(anyString());
    }

    @Test
    public void removeUnicodeCharacters() {
        assertEquals("sa-lsia", eventNameManager.generateShortName("sa\u2013lsia"));
    }

}
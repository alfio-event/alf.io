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

import alfio.repository.EventRepository;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class EventNameManagerTest {

    private EventRepository eventRepository = mock(EventRepository.class);
    private EventNameManager eventNameManager = new EventNameManager(eventRepository);

    @Test
    public void testSingleWord() {
        assertEquals("myevent", eventNameManager.generateShortName("myEvent"));
    }

    @Test
    public void dashedLowerCaseIfLessThan15Chars() throws Exception {
        when(eventRepository.countByShortName("my-event-2015")).thenReturn(0);
        assertEquals("my-event-2015", eventNameManager.generateShortName("my Event 2015"));
    }

    @Test
    public void croppedNameIfMoreThan15Chars() {
        when(eventRepository.countByShortName("vdt2016")).thenReturn(0);
        when(eventRepository.countByShortName("vdz2016")).thenReturn(0);
        assertEquals("vdt2016", eventNameManager.generateShortName("Voxxed Days Ticino 2016"));
        assertEquals("vdz2016", eventNameManager.generateShortName("Voxxed Days Zürich 2016"));
    }

    @Test
    public void randomNameIfCroppedNotAvailable() throws Exception {
        when(eventRepository.countByShortName("vdt2016")).thenReturn(1);
        when(eventRepository.countByShortName("vdz2016")).thenReturn(1);
        assertNotEquals("vdt2016", eventNameManager.generateShortName("Voxxed Days Ticino 2016"));
        assertNotEquals("vdz2016", eventNameManager.generateShortName("Voxxed Days Zürich 2016"));
    }

    @Test
    public void removePunctuation() throws Exception {
        assertEquals("bigg-i-o-2015", eventNameManager.generateShortName("BigG I/O 2015"));
    }

    @Test
    public void giveUpAfter5Times() throws Exception {
        when(eventRepository.countByShortName(anyString())).thenReturn(1);
        assertEquals("", eventNameManager.generateShortName("Pippo Baudo and Friends 2017"));
        verify(eventRepository, times(6)).countByShortName(anyString());
    }

    @Test
    public void removeUnicodeCharacters() throws Exception {
        assertEquals("sa-lsia", eventNameManager.generateShortName("sa\u2013lsia"));
    }

}
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
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
public class EventNameManagerTest {{
    describe("generateUniqueName", it -> {
        EventRepository eventRepository = mock(EventRepository.class);
        EventNameManager eventNameManager = new EventNameManager(eventRepository);
        it.should("return the same name (lower case) if the display name is a single word", expect -> expect.that(eventNameManager.generateShortName("myEvent")).is("myevent"));

        it.should("return the same name ( with dashes and lower case) if the display name is less than 15 chars", expect -> {
            when(eventRepository.countByShortName("my-event-2015")).thenReturn(0);
            expect.that(eventNameManager.generateShortName("my Event 2015")).is("my-event-2015");
        });

        it.should("return the cropped name (with dashes and lower case) if the display name is more than 15 chars", expect -> {
            when(eventRepository.countByShortName("vdt2016")).thenReturn(0);
            when(eventRepository.countByShortName("vdz2016")).thenReturn(0);
            expect.that(eventNameManager.generateShortName("Voxxed Days Ticino 2016")).is("vdt2016");
            expect.that(eventNameManager.generateShortName("Voxxed Days Zürich 2016")).is("vdz2016");
        });

        it.should("return a random name if the cropped names are not available", expect -> {
            when(eventRepository.countByShortName("vdt2016")).thenReturn(1);
            when(eventRepository.countByShortName("vdz2016")).thenReturn(1);
            expect.that(eventNameManager.generateShortName("Voxxed Days Ticino 2016")).never().is("vdt2016");
            expect.that(eventNameManager.generateShortName("Voxxed Days Zürich 2016")).never().is("vdz2016");
        });

        it.should("remove punctuation", expect -> {
            expect.that(eventNameManager.generateShortName("BigG I/O 2015")).is("bigg-i-o-2015");
        });

        it.should("try to generate a random short name for a maximum of 5 times and then give up", expect -> {
            Mockito.reset(eventRepository);
            when(eventRepository.countByShortName(anyString())).thenReturn(1);
            expect.that(eventNameManager.generateShortName("Pippo Baudo and Friends 2017")).is("");
            verify(eventRepository, times(6)).countByShortName(anyString());
        });
    });
}}
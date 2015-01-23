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
package alfio.manager.support;

import alfio.model.EmailMessage;
import org.apache.commons.lang3.Validate;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EmailQueue {

    private final Queue<EmailMessage> messages = new ConcurrentLinkedQueue<>();

    public boolean offer(EmailMessage emailMessage) {
        return messages.offer(emailMessage);
    }

    public Set<EmailMessage> poll(int maxElements) {
        Validate.isTrue(maxElements > 0, "MaxElements cannot be a negative number");
        Set<EmailMessage> result = new HashSet<>(maxElements);
        EmailMessage current;
        int counter = 0;
        while(counter < maxElements && (current = messages.poll()) != null) {
            result.add(current);
            counter++;
        }
        return result;
    }

}

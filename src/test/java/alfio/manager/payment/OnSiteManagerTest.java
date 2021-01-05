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
package alfio.manager.payment;

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ConfigurationManager.MaybeConfiguration;
import alfio.model.Event;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.ON_SITE_ENABLED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnSiteManagerTest {

    @Test
    void onSiteNotAvailableIfEventIsOnline() {
        var configurationManager = mock(ConfigurationManager.class);
        var event = mock(Event.class);
        var cl = ConfigurationLevel.event(event);
        when(event.getConfigurationLevel()).thenReturn(cl);
        when(event.event()).thenReturn(Optional.of(event));
        var configuration = mock(MaybeConfiguration.class);
        when(configurationManager.getFor(eq(ON_SITE_ENABLED), any(ConfigurationLevel.class))).thenReturn(configuration);
        when(configuration.getValueAsBooleanOrDefault()).thenReturn(true);
        var onSiteManager = new OnSiteManager(configurationManager, null);
        when(event.isOnline()).thenReturn(true);
        assertFalse(onSiteManager.accept(PaymentMethod.ON_SITE, new PaymentContext(event), null));
        when(event.isOnline()).thenReturn(false);
        assertTrue(onSiteManager.accept(PaymentMethod.ON_SITE, new PaymentContext(event), null));
    }
}
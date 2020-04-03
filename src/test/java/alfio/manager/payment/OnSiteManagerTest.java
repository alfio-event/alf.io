package alfio.manager.payment;

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ConfigurationManager.MaybeConfiguration;
import alfio.model.Event;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import org.junit.jupiter.api.Test;

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
        var configuration = mock(MaybeConfiguration.class);
        when(configurationManager.getFor(eq(ON_SITE_ENABLED), any(ConfigurationLevel.class))).thenReturn(configuration);
        when(configuration.getValueAsBooleanOrDefault(eq(false))).thenReturn(true);
        var onSiteManager = new OnSiteManager(configurationManager, null);
        when(event.isOnline()).thenReturn(true);
        assertFalse(onSiteManager.accept(PaymentMethod.ON_SITE, new PaymentContext(event), null));
        when(event.isOnline()).thenReturn(false);
        assertTrue(onSiteManager.accept(PaymentMethod.ON_SITE, new PaymentContext(event), null));
    }
}
package alfio.manager.system;

import alfio.model.EventAndOrganizationId;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

public class SendGridMailerTest {

    private SendGridMailer sendGridMailer;

    private ConfigurationManager configurationManager;

    @Before
    public void setUp() throws Exception {
        configurationManager = mock(ConfigurationManager.class);
        sendGridMailer = new SendGridMailer(configurationManager);
    }

    @Test
    public void name() {
        //Test data
        final var apiConfig = new ConfigurationManager.MaybeConfiguration(ConfigurationKeys.SENDGRID_API_KEY, new ConfigurationKeyValuePathLevel("key", "value", ConfigurationPathLevel.SYSTEM));
        final var fromConfig = new ConfigurationManager.MaybeConfiguration(ConfigurationKeys.SENDGRID_FROM, new ConfigurationKeyValuePathLevel("key", "value", ConfigurationPathLevel.SYSTEM));
        //Mock
        when(configurationManager.getFor(anySet(), any(ConfigurationLevel.class))).thenReturn(Map.of(ConfigurationKeys.SENDGRID_API_KEY, apiConfig, ConfigurationKeys.SENDGRID_FROM, fromConfig));
        //Service call
        sendGridMailer.send(new EventAndOrganizationId(1, 2), "Test", "TestEmail", List.<String>of(), "TestSubject", "Test", Optional.empty(), ArrayUtils.toArray());
        //Verify
        verify(configurationManager).getFor(anySet(), any(ConfigurationLevel.class));
        verifyNoMoreInteractions(configurationManager);
    }
}
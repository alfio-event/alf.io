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
package alfio.extension;

import alfio.manager.support.extension.ExtensionCapability;
import alfio.manager.support.extension.ExtensionEvent;
import alfio.manager.system.ExternalConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static alfio.manager.support.extension.ExtensionCapability.CREATE_GUEST_LINK;
import static alfio.manager.support.extension.ExtensionCapability.CREATE_VIRTUAL_ROOM;
import static alfio.manager.support.extension.ExtensionEvent.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ExtensionServiceTest {

    ExtensionService extensionService;

    @BeforeEach
    void setUp() {
        extensionService = new ExtensionService(null, null, null, mock(PlatformTransactionManager.class), mock(ExternalConfiguration.class), mock(NamedParameterJdbcTemplate.class));
    }

    @Test
    void validationOKNoCapabilities() {
        var metadata = generateMetadata(EnumSet.of(EVENT_METADATA_UPDATE, ONLINE_CHECK_IN_REDIRECT), Set.of());
        extensionService.validateCapabilities(metadata);
    }

    @Test
    void validationOK() {
        var metadata = generateMetadata(EnumSet.of(EVENT_METADATA_UPDATE, ONLINE_CHECK_IN_REDIRECT), EnumSet.of(CREATE_VIRTUAL_ROOM, CREATE_GUEST_LINK));
        extensionService.validateCapabilities(metadata);
    }

    @Test
    void validationFailed() {
        var metadata = generateMetadata(EnumSet.of(REFUND_ISSUED), EnumSet.of(CREATE_VIRTUAL_ROOM, CREATE_GUEST_LINK));
        assertThrows(IllegalArgumentException.class, () -> extensionService.validateCapabilities(metadata));
    }

    private ExtensionMetadata generateMetadata(Collection<ExtensionEvent> events, Collection<ExtensionCapability> capabilities) {
        return new ExtensionMetadata("id",
            "displayName",
            1,
            true,
            events.stream().map(ExtensionEvent::name).collect(Collectors.toList()),
            null,
            capabilities.stream().map(ExtensionCapability::name).collect(Collectors.toList()),
            capabilities.stream().map(ec -> new ExtensionMetadata.CapabilityDetail(ec.name(), ec.name() + " label", ec.name() + " description", null)).collect(Collectors.toList())
        );
    }
}
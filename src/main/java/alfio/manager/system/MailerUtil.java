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
package alfio.manager.system;

import alfio.model.Configurable;
import alfio.repository.user.OrganizationRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.function.Consumer;

final class MailerUtil {

    private static final Cache<Integer, String> ORG_ADDRESS_CACHE = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1L))
        .build();

    private MailerUtil() {}

    static void setReplyToIfPresent(String replyToConfig,
                                    Configurable configurable,
                                    OrganizationRepository organizationRepository,
                                    boolean setOrganizationEmailAsReplyTo,
                                    Consumer<String> replyToSetter) {
        if (StringUtils.isNotBlank(replyToConfig)) {
            replyToSetter.accept(replyToConfig);
        } else if(setOrganizationEmailAsReplyTo) {
            int organizationId = configurable.getOrganizationId();
            var address = ORG_ADDRESS_CACHE.get(organizationId, id -> {
                var organization = organizationRepository.getById(organizationId);
                if (StringUtils.isNotBlank(organization.getEmail())) {
                    return organization.getEmail();
                }
                return null;
            });
            if (StringUtils.isNotBlank(address)) {
                replyToSetter.accept(address);
            }
        }
    }
}

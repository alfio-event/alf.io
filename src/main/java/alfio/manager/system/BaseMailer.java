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

import alfio.manager.system.ConfigurationManager.MaybeConfiguration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.user.OrganizationRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

abstract class BaseMailer implements Mailer {

    static final String MISSING_CONFIG_MESSAGE = "config cannot be null";
    private final OrganizationRepository organizationRepository;
    private static final Cache<Integer, String> ORG_ADDRESS_CACHE = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1L))
        .build();

    BaseMailer(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    void setReplyToIfPresent(Map<ConfigurationKeys, MaybeConfiguration> conf,
                             int organizationId,
                             Consumer<String> replyToSetter) {
        var replyToConfig = requireNonNull(requireNonNull(conf, MISSING_CONFIG_MESSAGE).get(ConfigurationKeys.MAIL_REPLY_TO), "MAIL_REPLY_TO is required")
            .getValueOrDefault("");
        if (StringUtils.isNotBlank(replyToConfig)) {
            replyToSetter.accept(replyToConfig);
        } else if(requireNonNull(conf.get(ConfigurationKeys.MAIL_SET_ORG_REPLY_TO), "MAIL_SET_ORG_REPLY_TO is required").getValueAsBooleanOrDefault()) {
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

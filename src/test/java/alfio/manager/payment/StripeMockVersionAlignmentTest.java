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

import com.stripe.Stripe;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import static alfio.BaseTestConfiguration.STRIPE_MOCK_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * stripe-mock validates requests against the OpenAPI spec embedded in its binary.
 * If that spec targets a different API version than the stripe-java SDK, requests built by the SDK
 * may be rejected (e.g. "additional properties are not allowed").
 */
class StripeMockVersionAlignmentTest {

    @Test
    void stripeMockApiVersionMatchesSdk() throws Exception {
        try (var stripeMock = new GenericContainer<>(STRIPE_MOCK_IMAGE)) {
            stripeMock.start();
            var result = stripeMock.execInContainer("sh", "-c",
                "grep -aoE '\"version\": *\"[0-9]{4}-[0-9]{2}-[0-9]{2}\\.[a-z]+\"' /bin/stripe-mock | head -1 | cut -d'\"' -f4");
            assertEquals(Stripe.API_VERSION, result.getStdout().trim(),
                STRIPE_MOCK_IMAGE + " does not embed the API version used by stripe-java. Update STRIPE_MOCK_IMAGE in BaseTestConfiguration");
        }
    }
}

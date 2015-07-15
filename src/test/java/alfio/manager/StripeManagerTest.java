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

import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import com.stripe.exception.*;
import org.junit.runner.RunWith;

import static com.insightfullogic.lambdabehave.Suite.describe;

@RunWith(JunitSuiteRunner.class)
public class StripeManagerTest {{

    StripeManager stripeManager = new StripeManager(null, null);

    describe("Exception handler", it -> {

        it.should("return stripe's code in case of CardException", expect ->
                expect.that(stripeManager.handleException(new CardException("abcd", "houston_we_ve_a_problem", "param", null, null, null, null)))
                      .is("error.STEP2_STRIPE_houston_we_ve_a_problem"));

        it.should("return a code containing the field in error in case of InvalidRequestException", expect ->
                expect.that(stripeManager.handleException(new InvalidRequestException("abcd", "param", null, null)))
                        .is("error.STEP2_STRIPE_invalid_param"));

        it.should("return the 'abort' error code in case of AuthenticationException, " +
                "APIConnectionException and RateLimitException", expect -> {
            expect.that(stripeManager.handleException(new AuthenticationException("abcd", null)))
                    .is("error.STEP2_STRIPE_abort");
            expect.that(stripeManager.handleException(new APIConnectionException("abcd")))
                    .is("error.STEP2_STRIPE_abort");
        });

        it.should("return the 'unexpected' error in the other cases", expect ->
                expect.that(stripeManager.handleException(new StripeException("", null) {}))
                        .is("error.STEP2_STRIPE_unexpected"));

    });
}}
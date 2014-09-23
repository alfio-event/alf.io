/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.manager;

import com.stripe.Stripe;
import com.stripe.exception.*;
import com.stripe.model.Charge;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by yanke on 22.09.14.
 */
@Component
public class StripeManager {

    /**
     * After client side integration with stripe widget, our server receives the stripeToken
     * StripeToken is a single-use token that allows our server to effectively charge the credit card and
     * get money on our account.
     *
     * as documented in https://stripe.com/docs/tutorials/charges
     * @param stripeToken
     * @throws StripeException 
     */
    public void chargeCreditCard(String stripeToken) throws StripeException {
        // Use Stripe's bindings...
        Stripe.apiKey = "sk_test_cayJOFUUYF9cWOoMXemJd61Z";

        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", 1000);
        chargeParams.put("currency", "chf");
        //chargeParams.put("card", "tok_14fYSAJSN8lunZypoZ7bQ2nm"); // obtained with Stripe.js
        chargeParams.put("card", stripeToken); // obtained with Stripe.js
        chargeParams.put("description", "Charge for test@example.com");
        Map<String, String> initialMetadata = new HashMap<String, String>();
        initialMetadata.put("order_id", "6735");
        chargeParams.put("metadata", initialMetadata);
        Charge charge = Charge.create(chargeParams);
    }

}

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
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import io.bagarino.manager.system.ConfigurationManager;
import io.bagarino.model.system.ConfigurationKeys;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class StripeManager {

    private final ConfigurationManager configurationManager;

	@Autowired
	public StripeManager(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
	}

	public String getSecretKey() {
        return configurationManager.getRequiredValue(ConfigurationKeys.STRIPE_SECRET_KEY);
	}

	public String getPublicKey() {
		return configurationManager.getRequiredValue(ConfigurationKeys.STRIPE_PUBLIC_KEY);
	}

    /**
     * After client side integration with stripe widget, our server receives the stripeToken
     * StripeToken is a single-use token that allows our server to effectively charge the credit card and
     * get money on our account.
     *
     * as documented in https://stripe.com/docs/tutorials/charges
     * @return 
     *
     * @throws StripeException
     */
    public Charge chargeCreditCard(String stripeToken, long amountInCent, String currency, String reservationId, String email, String fullName, String billingAddress) throws StripeException {
        // Use Stripe's bindings...
        Stripe.apiKey = getSecretKey();

        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", amountInCent);
        chargeParams.put("currency", currency);
        chargeParams.put("card", stripeToken); // obtained with Stripe.js


        chargeParams.put("description", "Charge for test@example.com");//TODO replace

        Map<String, String> initialMetadata = new HashMap<String, String>();

        initialMetadata.put("reservationId", reservationId);
        initialMetadata.put("email", email);
        initialMetadata.put("fullName", fullName);
        if(StringUtils.isNotBlank(billingAddress)) {
        	initialMetadata.put("billingAddress", billingAddress);
        }



        chargeParams.put("metadata", initialMetadata);
        return Charge.create(chargeParams);
    }

}

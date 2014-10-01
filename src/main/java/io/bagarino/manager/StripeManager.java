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


import io.bagarino.repository.system.ConfigurationRepository;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;

/**
 * Created by yanke on 22.09.14.
 */
@Component
public class StripeManager {
	
	private final ConfigurationRepository configurationRepository;
	
	@Autowired
	public StripeManager(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}
	
	public String getSecretKey() {
		return configurationRepository.findByKey("stripe_secret_key").getValue();
	}
	
	public String getPublicKey() {
		return configurationRepository.findByKey("stripe_public_key").getValue();
	}

    /**
     * After client side integration with stripe widget, our server receives the stripeToken
     * StripeToken is a single-use token that allows our server to effectively charge the credit card and
     * get money on our account.
     *
     * as documented in https://stripe.com/docs/tutorials/charges
     * @param stripeToken
     * @param bigDecimal 
     * @throws StripeException 
     */
    public void chargeCreditCard(String stripeToken, long amountInCent, String currency) throws StripeException {
        // Use Stripe's bindings...
        Stripe.apiKey = getSecretKey();

        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", amountInCent);
        chargeParams.put("currency", currency);
        chargeParams.put("card", stripeToken); // obtained with Stripe.js
        chargeParams.put("description", "Charge for test@example.com");//TODO replace
        Map<String, String> initialMetadata = new HashMap<String, String>();
        initialMetadata.put("order_id", "6735");//TODO: replace
        chargeParams.put("metadata", initialMetadata);
        Charge charge = Charge.create(chargeParams);
    }

}

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
     */
    public void chargeCreditCard(String stripeToken){

        try {
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


        } catch (CardException e) {
            // Since it's a decline, CardException will be caught
            System.out.println("Status is: " + e.getCode());
            System.out.println("Message is: " + e.getParam());
        } catch (InvalidRequestException e) {
            // Invalid parameters were supplied to Stripe's API
            e.printStackTrace();
        } catch (AuthenticationException e) {
            // Authentication with Stripe's API failed
            // (maybe you changed API keys recently)
            e.printStackTrace();
        } catch (APIConnectionException e) {
            // Network communication with Stripe failed
            e.printStackTrace();
        } catch (StripeException e) {
            // Display a very generic error to the user, and maybe send
            // yourself an email
            e.printStackTrace();
        } catch (Exception e) {
            // Something else happened, completely unrelated to Stripe
            e.printStackTrace();
        }

    }

}

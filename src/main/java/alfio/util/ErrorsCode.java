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
package alfio.util;

public interface ErrorsCode {


    String STEP_1_SELECT_AT_LEAST_ONE = "error.STEP_1_SELECT_AT_LEAST_ONE";
    String STEP_1_OVER_MAXIMUM = "error.STEP_1_OVER_MAXIMUM";
    String STEP_1_OVER_MAXIMUM_FOR_RESTRICTED_CATEGORY = "error.STEP_1_OVER_MAXIMUM_FOR_RESTRICTED_CATEGORY";
    String STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE = "error.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE";
    String STEP_1_ACCESS_RESTRICTED = "error.STEP_1_ACCESS_RESTRICTED";
    String STEP_1_NOT_ENOUGH_TICKETS = "error.STEP_1_NOT_ENOUGH_TICKETS";
    
    String STEP_1_CODE_NOT_FOUND = "error.STEP_1_CODE_NOT_FOUND";

    String STEP_2_PAYMENT_PROCESSING_ERROR = "error.STEP_2_PAYMENT_PROCESSING_ERROR";
    
    String STEP_2_ORDER_EXPIRED = "error.STEP_2_ORDER_HAS_EXPIRED";
    String STEP_2_CAPTCHA_VALIDATION_FAILED = "error.STEP_2_CAPTCHA_VALIDATION_FAILED";
    
    
    String STEP_2_MISSING_STRIPE_TOKEN = "error.STEP_2_MISSING_STRIPE_TOKEN";
    String STEP_2_MISSING_PAYMENT_METHOD = "error.STEP_2_MISSING_PAYMENT_METHOD";
    String STEP_2_TERMS_NOT_ACCEPTED = "error.STEP_2_TERMS_CONDITIONS";
    String STEP_2_EMPTY_EMAIL = "error.STEP_2_EMPTY_EMAIL";
    String STEP_2_MAX_LENGTH_EMAIL ="error.STEP_2_EMAIL_IS_TOO_LONG";
    String STEP_2_INVALID_EMAIL = "error.STEP_2_INVALID_EMAIL";
    String STEP_2_EMPTY_FULLNAME = "error.STEP_2_EMPTY_FULLNAME";
    String STEP_2_MAX_LENGTH_FULLNAME = "error.STEP_2_MAX_LENGTH_FULLNAME";
    String STEP_2_MAX_LENGTH_BILLING_ADDRESS = "error.STEP_2_MAX_LENGTH_BILLING_ADDRESS";
    String STEP_2_EMPTY_BILLING_ADDRESS = "error.STEP_2_EMPTY_BILLING_ADDRESS";

    String STEP_2_INVALID_HMAC = "error.STEP_2_INVALID_HMAC";
    String STEP_2_PAYMENT_REQUEST_CREATION = "error.STEP_2_PAYMENT_REQUEST_CREATION";
    String STEP_2_PAYPAL_UNEXPECTED = "error.STEP_2_PAYPAL_unexpected";


    String STEP_2_MAX_LENGTH_FIRSTNAME = "error.STEP_2_MAX_LENGTH_FIRSTNAME";
    String STEP_2_EMPTY_FIRSTNAME = "error.STEP_2_EMPTY_FIRSTNAME";

    String STEP_2_MAX_LENGTH_LASTNAME = "error.STEP_2_MAX_LENGTH_LASTNAME";
    String STEP_2_EMPTY_LASTNAME = "error.STEP_2_EMPTY_LASTNAME";

    String STEP_2_MISSING_ATTENDEE_DATA = "error.STEP_2_MISSING_ATTENDEE_DATA";

}

/**
 * Shared types for admin GUI.
 * NOTE: These types are shared with the public frontend in public/src/app/model/payment.ts
 */

/**
 * Used to identify and describe an organizer-created
 * offline payment method.
 */
type CustomOfflinePayment = {
    paymentMethodId: string | null;
    localizations: {
        [lang: string]: CustomOfflinePaymentLocalization;
    }
};

/**
 * Defines a single language localization for a payment method
 */
type CustomOfflinePaymentLocalization = {
    paymentName: string;
    paymentDescription: string;
    paymentInstructions: string;
};

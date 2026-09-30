export class ReservationPaymentResult {
    success: boolean;
    failure: boolean;
    redirect: boolean;
    redirectUrl: string;
    gatewayIdOrNull: string;
}

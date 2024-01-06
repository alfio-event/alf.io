import {Injectable} from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {ReservationService} from '../shared/reservation.service';
import {ReservationStatus} from '../model/reservation-info';
import {SuccessComponent} from './success/success.component';
import {OverviewComponent} from './overview/overview.component';
import {BookingComponent} from './booking/booking.component';
import {OfflinePaymentComponent} from './offline-payment/offline-payment.component';
import {ProcessingPaymentComponent} from './processing-payment/processing-payment.component';
import {NotFoundComponent} from './not-found/not-found.component';
import {ErrorComponent} from './error/error.component';
import {DeferredOfflinePaymentComponent} from './deferred-offline-payment/deferred-offline-payment.component';
import {PurchaseContextType} from '../shared/purchase-context.service';
import {SuccessSubscriptionComponent} from './success-subscription/success-subscription.component';

@Injectable({
    providedIn: 'root'
})
export class ReservationGuard  {

    constructor(private reservationService: ReservationService, private router: Router) {
    }

    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean | UrlTree> | boolean {
        return this.checkAndRedirect(route.data['type'], route.params[route.data['publicIdentifierParameter']], route.params['reservationId'], route.component);
    }

    private checkAndRedirect(type: PurchaseContextType, publicIdentifier: string, reservationId: string, component: any): Observable<boolean | UrlTree> {
        return this.reservationService.getReservationStatusInfo(reservationId)
            .pipe(catchError(err => of({ status: <ReservationStatus>'NOT_FOUND', validatedBookingInformation: false })), map(reservation => {
                const selectedComponent = getCorrespondingController(type, reservation.status, reservation.validatedBookingInformation);
                if (component === selectedComponent) {
                    return true;
                }
                return this.router.createUrlTree(getRouteFromComponent(selectedComponent, type, publicIdentifier, reservationId));
            }));
    }
}

function getRouteFromComponent(component: any, type: PurchaseContextType, publicIdentifier: string, reservationId: string): string[] {
    if (component === OverviewComponent) {
        return [type, publicIdentifier, 'reservation', reservationId, 'overview'];
    } else if (component === BookingComponent) {
        return [type, publicIdentifier, 'reservation', reservationId, 'book'];
    } else if (component === SuccessComponent || component === SuccessSubscriptionComponent) {
        return [type, publicIdentifier, 'reservation', reservationId, 'success'];
    } else if (component === OfflinePaymentComponent) {
        return [type, publicIdentifier, 'reservation', reservationId, 'waiting-payment'];
    } else if (component === DeferredOfflinePaymentComponent) {
        return [type, publicIdentifier, 'reservation', reservationId, 'deferred-payment'];
    } else if (component === ProcessingPaymentComponent) {
        return [type, publicIdentifier, 'reservation', reservationId, 'processing-payment'];
    } else if (component === NotFoundComponent) {
        return [type, publicIdentifier, 'reservation', reservationId, 'not-found'];
    } else if (component === ErrorComponent) {
        return [type, publicIdentifier, 'reservation', reservationId, 'error'];
    } else {
        return ['/'];
    }
}

function getCorrespondingController(type: PurchaseContextType, status: ReservationStatus, validatedBookingInformations: boolean) {
    switch (status) {
        case 'PENDING': return validatedBookingInformations ? OverviewComponent : BookingComponent;
        case 'COMPLETE':
        case 'FINALIZING':
          return type === 'subscription' ? SuccessSubscriptionComponent : SuccessComponent;
        case 'OFFLINE_PAYMENT':
        case 'OFFLINE_FINALIZING':
          return OfflinePaymentComponent;
        case 'DEFERRED_OFFLINE_PAYMENT': return DeferredOfflinePaymentComponent;
        case 'EXTERNAL_PROCESSING_PAYMENT':
        case 'WAITING_EXTERNAL_CONFIRMATION': return ProcessingPaymentComponent;
        case 'IN_PAYMENT':
        case 'STUCK': return ErrorComponent;
        default: return NotFoundComponent;
    }
}

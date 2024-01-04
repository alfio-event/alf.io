import {ReservationStatusChanged} from '../model/embedding-configuration';
import {PurchaseContext} from '../model/purchase-context';
import {AdditionalServiceWithData, ReservationInfo, ReservationStatus} from '../model/reservation-info';
import {HttpErrorResponse} from '@angular/common/http';
import {ReservationService} from './reservation.service';
import {interval} from 'rxjs';
import {filter, mergeMap} from 'rxjs/operators';

export const DELETE_ACCOUNT_CONFIRMATION = 'alfio.delete-account.confirmation';

export function writeToSessionStorage(key: string, value: string): void {
  try {
    window.sessionStorage.setItem(key, value);
  } catch (e) {
    // session storage might be disabled in some contexts
  }
}

export function getFromSessionStorage(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch (e) {
    // session storage might be disabled in some contexts
    return null;
  }
}

export function removeFromSessionStorage(key: string): void {
  try {
    window.sessionStorage.removeItem(key);
  } catch (e) {
  }
}

export const mobile = window.matchMedia('(max-width: 767px)').matches;
export const embedded = window.parent !== window;

export function notifyPaymentErrorToParent(purchaseContext: PurchaseContext,
                                           reservationInfo: ReservationInfo,
                                           reservationId: string,
                                           err: Error) {
  if (embedded && purchaseContext.embeddingConfiguration.enabled) {
    window.parent.postMessage(
      new ReservationStatusChanged(reservationInfo.status, reservationId, errorMessage(err)),
      purchaseContext.embeddingConfiguration.notificationOrigin
    );
  }
}

export function pollReservationStatus(reservationId: string,
  reservationService: ReservationService,
  processSuccessful: (res: ReservationInfo) => void,
  desiredStatuses: Array<ReservationStatus> = ['COMPLETE']): void {
  const subscription = interval(5000)
    .pipe(
      mergeMap(() => reservationService.getReservationInfo(reservationId)),
      filter(reservationInfo => desiredStatuses.includes(reservationInfo.status))
    ).subscribe(reservationInfo => {
      processSuccessful(reservationInfo);
      subscription.unsubscribe();
    });
}

function errorMessage(err: Error): string {
  if (err instanceof HttpErrorResponse) {
    return `${err.message} (${err.status})`;
  }
  return err.message;
}

export function groupAdditionalData(data: AdditionalServiceWithData[]): GroupedAdditionalServiceWithData[] {
  if (data == null || data.length === 0) {
    return [];
  }
  const byServiceId = data.map(d => {
    return {
      count: 1,
      ...d
    };
  }).reduce((accumulator, currentValue) => {
    const existing = accumulator[currentValue.serviceId];
    if (existing != null) {
      existing.count += 1;
      existing.ticketFieldConfiguration = [...existing.ticketFieldConfiguration, ...currentValue.ticketFieldConfiguration];
    } else {
      accumulator[currentValue.serviceId] = {
        count: currentValue.count,
        ...currentValue
      };
    }
    return accumulator;
  }, <{[k: number]: GroupedAdditionalServiceWithData}>{});
  return Object.values(byServiceId);
}

export interface GroupedAdditionalServiceWithData extends AdditionalServiceWithData {
  count: number;
}

import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {EventService} from './event.service';
import {SubscriptionService} from './subscription.service';
import {PurchaseContext} from '../model/purchase-context';

@Injectable({
  providedIn: 'root'
})
export class PurchaseContextService {

  constructor(private eventService: EventService, private subscriptionService: SubscriptionService) { }


  getContext(type: PurchaseContextType, publicIdentifier: string): Observable<PurchaseContext> {
    if (type === 'event') {
      return this.eventService.getEvent(publicIdentifier);
    } else if (type === 'subscription') {
      return this.subscriptionService.getSubscriptionById(publicIdentifier);
    } else {
      throw new Error();
    }
  }
}

export type PurchaseContextType = 'event' | 'subscription';

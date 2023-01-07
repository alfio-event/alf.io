import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {BasicEventInfo} from '../model/basic-event-info';
import {Event} from '../model/event';
import {ItemsByCategory} from '../model/items-by-category';
import {WaitingListSubscriptionRequest} from '../model/waiting-list-subscription-request';
import {ValidatedResponse} from '../model/validated-response';
import {shareReplay} from 'rxjs/operators';
import {EventCode} from '../model/event-code';
import {DateValidity} from '../model/date-validity';
import {SearchParams} from '../model/search-params';

@Injectable({
  providedIn: 'root'
})
export class EventService {

  private eventCache: {[key: string]: Observable<Event>} = {};

  constructor(private http: HttpClient) { }

  getEvents(searchParams?: SearchParams): Observable<BasicEventInfo[]> {
    const params = searchParams?.toHttpParams();
    return this.http.get<BasicEventInfo[]>('/api/v2/public/events', {
      responseType: 'json',
      params
    });
  }

  getEvent(eventShortName: string): Observable<Event> {
    // caching as explained with https://blog.angularindepth.com/fastest-way-to-cache-for-lazy-developers-angular-with-rxjs-444a198ed6a6
    if (!this.eventCache[eventShortName]) {
      const preloadEvent = document.getElementById('preload-event');
      if (preloadEvent && preloadEvent.getAttribute('data-param') === eventShortName) {
        this.eventCache[eventShortName] = of(JSON.parse(preloadEvent.textContent)).pipe(shareReplay(1));
      } else {
        this.eventCache[eventShortName] = this.http.get<Event>(`/api/v2/public/event/${eventShortName}`).pipe(shareReplay(1));
      }
      setTimeout(() => {
        delete this.eventCache[eventShortName];
      }, 60000 * 20); // clean up cache after 20 minutes
    }

    return this.eventCache[eventShortName];
  }

  getEventTicketsInfo(eventShortName: string, code?: string): Observable<ItemsByCategory> {
    const params = code ? {params: {code: code}} : {};
    return this.http.get<ItemsByCategory>(`/api/v2/public/event/${eventShortName}/ticket-categories`, params);
  }

  submitWaitingListSubscriptionRequest(eventShortName: string, waitingListSubscriptionRequest: WaitingListSubscriptionRequest): Observable<ValidatedResponse<boolean>> {
    return this.http.post<ValidatedResponse<boolean>>(`/api/v2/public/event/${eventShortName}/waiting-list/subscribe`, waitingListSubscriptionRequest);
  }

  validateCode(eventShortName: string, code: string): Observable<ValidatedResponse<EventCode>> {
    return this.http.get<ValidatedResponse<EventCode>>(`/api/v2/public/event/${eventShortName}/validate-code`, {params: {code: code}});
  }
}

export function shouldDisplayTimeZoneInfo(provider: DateValidity): boolean {
  const datesWithOffset = provider.datesWithOffset;
  return isDifferentTimeZone(datesWithOffset.startDateTime, datesWithOffset.startTimeZoneOffset)
      || isDifferentTimeZone(datesWithOffset.endDateTime, datesWithOffset.endTimeZoneOffset);
}

export function isDifferentTimeZone(serverTs: number, serverOffset: number): boolean {
  // client:
  //    The time-zone offset is the difference, in minutes, from local time to UTC.
  //    Note that this means that the offset is positive if the local timezone is behind UTC and negative if it is ahead.
  //    source: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getTimezoneOffset
  //
  // server:
  //    nothing special, the offset is returned as it should be (positive if ahead of UTC, negative otherwise), in seconds

  const clientOffset = new Date(serverTs).getTimezoneOffset() * 60;
  return (clientOffset + serverOffset) !== 0;
}

/**
 * This is a polyfill for Node.remove(), which is not supported in IE
 * @param node the Node to remove
 * @returns true if the Node has been removed, false otherwise
 */
export function removeDOMNode(node: Node): boolean {
  if (node != null && node.parentNode != null) {
    node.parentNode.removeChild(node);
    return true;
  }
  return false;
}

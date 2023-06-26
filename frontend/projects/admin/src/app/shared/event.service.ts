import {HttpClient} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {map, Observable} from 'rxjs';
import {Event, EventInfo, EventOrganizationInfo, EventTicketsStatistics,} from '../model/event';
import {UiTicketCategory} from '../model/ticket-category';


@Injectable()
export class EventService {
  constructor(private httpClient: HttpClient) {}

  getActiveEvents(organizationId: string): Observable<EventInfo[]> {
    const orgId = parseInt(organizationId);
    return this.httpClient
      .get<EventInfo[]>('/admin/api/active-events')
      .pipe(map((events) => events.filter((e) => e.organizationId === orgId)));
  }

  getExpiredEvents(organizationId: string): Observable<EventInfo[]> {
    const orgId = parseInt(organizationId);
    return this.httpClient
      .get<EventInfo[]>('/admin/api/expired-events')
      .pipe(map((events) => events.filter((e) => e.organizationId == orgId)));
  }

  getEvent(eventId: string): Observable<Event> {
    return this.httpClient.get<Event>(`/admin/api/events/id/${eventId}`);
  }

  getEventByShortName(
    eventShortName: string
  ): Observable<EventOrganizationInfo> {
    return this.httpClient.get<EventOrganizationInfo>(
      `/admin/api/events/${eventShortName}`
    );
  }

  getTicketsStatistics(
    eventShortName: string
  ): Observable<EventTicketsStatistics> {
    return this.httpClient.get<EventTicketsStatistics>(
      `/admin/api/events/${eventShortName}/ticket-sold-statistics`
    );
  }
  unbindTickets(eventShortName: string, category: UiTicketCategory) {
    return this.httpClient.put(
      '/admin/api/events/' +
        eventShortName +
        '/category/' +
        category.id +
        '/unbind-tickets',
      null
    );
  }
}

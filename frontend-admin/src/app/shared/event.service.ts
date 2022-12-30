import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { map, Observable } from "rxjs";
import { Event, EventInfo } from "../model/event";

@Injectable()
export class EventService {
  constructor(private httpClient: HttpClient) {
  }

  getActiveEvents(organizationId: string): Observable<EventInfo[]> {
    const orgId = parseInt(organizationId);
    return this.httpClient.get<EventInfo[]>('/admin/api/active-events')
        .pipe(map(events => events.filter(e => e.organizationId === orgId)));
  }


  getExpiredEvents(organizationId: string): Observable<EventInfo[]> {
    const orgId = parseInt(organizationId);
    return this.httpClient.get<EventInfo[]>('/admin/api/expired-events')
        .pipe(map(events => events.filter(e => e.organizationId == orgId)));
  }

  getEvent(eventId: string): Observable<Event> {
    return this.httpClient.get<Event>(`/admin/api/events/id/${eventId}`);
  }
}

import {Component} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { map, Observable, of, switchMap } from 'rxjs';
import { Event } from 'src/app/model/event';
import { EventService } from 'src/app/shared/event.service';

@Component({
  selector: 'app-event-dashboard',
  templateUrl: './event-dashboard.component.html',
  styleUrls: ['./event-dashboard.component.scss']
})
export class EventDashboardComponent {

  public eventId$: Observable<string | null> = of();
  public event$: Observable<Event | null> = of();

  constructor(private readonly eventService: EventService, route: ActivatedRoute) {
    this.eventId$ = route.paramMap.pipe(map((pm) => pm.get('eventId')));
    this.event$ = this.eventId$.pipe(switchMap(v => v != null ? eventService.getEvent(v) : of()));
  }

}

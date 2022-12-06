import {Component, OnInit} from "@angular/core";
import {ActivatedRoute} from "@angular/router";
import { Observable, of } from "rxjs";
import { EventInfo } from "../model/event";
import { EventService } from "../shared/event.service";

@Component({
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {

  public readonly organizationId: string;
  public activeEvents$: Observable<EventInfo[]> = of();
  public expiredEvents$: Observable<EventInfo[]> = of();

  constructor(route: ActivatedRoute, private readonly eventService: EventService) {
    this.organizationId = route.snapshot.paramMap.get('id') as string;
  }

  ngOnInit(): void {
    this.activeEvents$ = this.eventService.getActiveEvents(this.organizationId);
    this.expiredEvents$ = this.eventService.getExpiredEvents(this.organizationId);
  }
}

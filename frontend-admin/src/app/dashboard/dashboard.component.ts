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
  public activeFilter: boolean = true;
  public inactiveFilter: boolean = false;

  constructor(route: ActivatedRoute, private readonly eventService: EventService) {
    this.organizationId = route.snapshot.paramMap.get('organizationId') as string;
  }

  public ngOnInit(): void {
    this.activeEvents$ = this.eventService.getActiveEvents(this.organizationId); // default
  }

  public toggleActiveFilter(toggle: boolean): void {
    this.activeFilter = toggle;
    if (toggle) {
      this.activeEvents$ = this.eventService.getActiveEvents(this.organizationId);
    } else {
      this.activeEvents$ = of();
    }
  }

  public toggleInactiveFilter(toggle: boolean): void {
    this.inactiveFilter = toggle;
    if (toggle) {
      this.expiredEvents$ = this.eventService.getExpiredEvents(this.organizationId);
    } else {
      this.expiredEvents$ = of();
    }
  }
}

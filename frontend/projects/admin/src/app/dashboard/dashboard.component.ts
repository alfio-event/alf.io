import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { map, mergeMap, Observable, of } from 'rxjs';
import { EventInfo } from '../model/event';
import { EventService } from '../shared/event.service';
import { NgbModal, NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExportDateSelectorComponent } from './export-date-selector/export-date-selector.component';

@Component({
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  public organizationId$: Observable<string | null> = of();
  public activeEvents$: Observable<EventInfo[]> = of();
  public expiredEvents$: Observable<EventInfo[]> = of();
  public activeFilter: boolean = true;
  public inactiveFilter: boolean = false;

  constructor(
    route: ActivatedRoute,
    private readonly eventService: EventService,
    private readonly modalService: NgbModal
  ) {
    this.organizationId$ = route.paramMap.pipe(
      map((pm) => pm.get('organizationId'))
    );
  }

  public ngOnInit(): void {
    this.activeEvents$ = this.loadActiveEvents(); // default
  }

  private loadActiveEvents() {
    return this.organizationId$.pipe(
      mergeMap((orgId) =>
        orgId != null ? this.eventService.getActiveEvents(orgId) : []
      )
    );
  }

  private loadInactiveEvents() {
    return this.organizationId$.pipe(
      mergeMap((orgId) =>
        orgId != null ? this.eventService.getExpiredEvents(orgId) : []
      )
    );
  }

  public toggleActiveFilter(toggle: boolean): void {
    this.activeFilter = toggle;
    if (toggle) {
      this.activeEvents$ = this.loadActiveEvents();
    } else {
      this.activeEvents$ = of();
    }
  }

  public toggleInactiveFilter(toggle: boolean): void {
    this.inactiveFilter = toggle;
    if (toggle) {
      this.expiredEvents$ = this.loadInactiveEvents();
    } else {
      this.expiredEvents$ = of();
    }
  }
  public openExportDateSelector(): void {
    const modalRef = this.modalService.open(ExportDateSelectorComponent, {
      size: 'lg',
    });
  }
}

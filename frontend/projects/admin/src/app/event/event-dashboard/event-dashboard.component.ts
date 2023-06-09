import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ChartConfiguration, ChartData, ChartOptions } from 'chart.js';
import { Observable, map, of, switchMap } from 'rxjs';
import {
  Event,
  EventOrganizationInfo,
  EventTicketsStatistics,
} from '../../model/event';
import { EventService } from '../../shared/event.service';

@Component({
  selector: 'app-event-dashboard',
  templateUrl: './event-dashboard.component.html',
  styleUrls: ['./event-dashboard.component.scss'],
})
export class EventDashboardComponent implements OnInit {
  public eventId$: Observable<string | null> = of();
  public event$: Observable<Event | null> = of();
  public eventOrganizationInfo$: Observable<EventOrganizationInfo | null> =
    of();
  public eventTicketsStatistics$: Observable<EventTicketsStatistics | null> =
    of();

  public pieChartOptions: ChartOptions<'pie'> = {
    responsive: false,
    plugins: {
      legend: {
        display: true,
        position: 'right',
      },
    },
  };
  public pieChartData: ChartData<'pie', number[], string | string[]> = {
    labels: [],
    datasets: [
      {
        data: [],
        backgroundColor: [],
      },
    ],
  };

  public lineChartData: ChartConfiguration<'line'>['data'] = {
    labels: [],
    datasets: [
      {
        data: [],
        label: 'Series A',
        fill: true,
        tension: 0.5,
        borderColor: 'black',
        backgroundColor: 'rgba(255,0,0,0.3)',
      },
    ],
  };
  public lineChartOptions: ChartOptions<'line'> = {
    responsive: false,
  };
  public lineChartLegend = true;

  constructor(
    private readonly eventService: EventService,
    route: ActivatedRoute
  ) {
    this.eventId$ = route.paramMap.pipe(map((pm) => pm.get('eventId')));

    this.event$ = this.eventId$.pipe(
      switchMap((eventId) =>
        eventId != null ? eventService.getEvent(eventId) : of()
      )
    );

    this.eventOrganizationInfo$ = this.event$.pipe(
      switchMap((event) =>
        event != null ? eventService.getEventByShortName(event.shortName) : of()
      )
    );

    this.eventTicketsStatistics$ = this.event$.pipe(
      switchMap((event) =>
        event != null
          ? eventService.getTicketsStatistics(event.shortName)
          : of()
      )
    );

    this.eventOrganizationInfo$.subscribe((eventOrganizationInfo) => {
      const event = eventOrganizationInfo?.event;
      if (!event) {
        return;
      }
      const pieData = [
        {
          value: event.checkedInTickets,
          name: 'Checked in',
          backgroundColor: '#5cb85c',
          meta: 'Checked in (' + event.checkedInTickets + ')',
        },
        {
          value: event.soldTickets,
          name: 'Sold',
          backgroundColor: '#f0ad4e',
          meta: 'Sold (' + event.soldTickets + ')',
        },
        {
          value: event.pendingTickets + event.releasedTickets,
          name: 'Pending',
          backgroundColor: '#7670b7',
          meta:
            'Pending (' + (event.pendingTickets + event.releasedTickets) + ')',
        },
        {
          value: event.notSoldTickets,
          name: 'Reserved for categories',
          backgroundColor: '#f0ad4e',
          meta: 'Reserved for categories (' + event.notSoldTickets + ')',
        },
        {
          value: event.notAllocatedTickets,
          name: 'Not yet allocated',
          backgroundColor: '#d9534f',
          meta: 'Not yet allocated (' + event.notAllocatedTickets + ')',
        },
        {
          value: event.dynamicAllocation,
          name: 'Available',
          backgroundColor: '#428bca',
          meta: 'Available (' + event.dynamicAllocation + ')',
        },
      ].filter((item) => item.value > 0);

      this.pieChartData = {
        labels: pieData.map((item) => item.name),
        datasets: [
          {
            data: pieData.map((item) => item.value),
            backgroundColor: pieData.map((item) => item.backgroundColor),
          },
        ],
      };
    });
  }

  ngOnInit(): void {}
}

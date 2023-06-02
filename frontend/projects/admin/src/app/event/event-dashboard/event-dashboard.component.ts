import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { map, Observable, of, switchMap } from 'rxjs';
import { EventService } from '../../shared/event.service';
import { Event } from '../../model/event';
import { ChartOptions, ChartConfiguration } from 'chart.js';

@Component({
  selector: 'app-event-dashboard',
  templateUrl: './event-dashboard.component.html',
  styleUrls: ['./event-dashboard.component.scss'],
})
export class EventDashboardComponent implements OnInit {
  public eventId$: Observable<string | null> = of();
  public event$: Observable<Event | null> = of();
  public pieChartOptions: ChartOptions<'pie'> = {
    responsive: false,
  };
  public pieChartLabels = [
    ['Download', 'Sales'],
    ['In', 'Store', 'Sales'],
    'Mail Sales',
  ];
  public pieChartDatasets = [
    {
      data: [300, 500, 100],
    },
  ];
  public pieChartLegend = true;
  public pieChartPlugins = [];
  public lineChartData: ChartConfiguration<'line'>['data'] = {
    labels: ['January', 'February', 'March', 'April', 'May', 'June', 'July'],
    datasets: [
      {
        data: [65, 59, 80, 81, 56, 55, 40],
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
    route.paramMap.subscribe((result) => console.log(result));
    this.event$ = this.eventId$.pipe(
      switchMap((eventId) =>
        eventId != null ? eventService.getEvent(eventId) : of()
      )
    );
  }

  ngOnInit(): void {}
}

import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {ChartConfiguration, ChartData, ChartOptions} from 'chart.js';
import {map, Observable, of, switchMap} from 'rxjs';
import {Event, EventInfo, EventOrganizationInfo, EventTicketsStatistics,} from '../../model/event';
import {EventService} from '../../shared/event.service';
import {formatDate} from '@angular/common';
import {ConfigurationService} from '../../shared/configuration.service';
import {InstanceSetting} from '../../model/instance-settings';
import {TicketCategory, TicketCategoryFilter, TicketTokenStatus, UiTicketCategory} from '../../model/ticket-category';

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
  public instanceSetting$: Observable<InstanceSetting> = of();

  public pieChartOptions: ChartOptions<'pie'> = {
    responsive: true,

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
    datasets: [],
  };
  public lineChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
  };
  public lineChartLegend = true;

  public categoryFilter: TicketCategoryFilter = {
    active: true,
    expired: false,
    search: '',
  };

  constructor(
    private readonly eventService: EventService,
    private readonly route: ActivatedRoute,
    private readonly configurationService: ConfigurationService
  ) {
    this.eventId$ = route.paramMap.pipe(map((pm) => pm.get('eventId')));
    this.instanceSetting$ = this.configurationService.loadInstanceSetting();

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
      this.loadPieData(event);
    });

    this.eventTicketsStatistics$.subscribe((eventTicketsStatistics) => {
      if (!eventTicketsStatistics) {
        return;
      }
      this.loadLineData(eventTicketsStatistics);
    });
  }

  loadLineData(eventTicketsStatistics: EventTicketsStatistics) {
    this.lineChartData = {
      labels: eventTicketsStatistics.sold.map((item) =>
        formatDate(item.date, 'mediumDate', 'en')
      ),
      datasets: [
        {
          data: eventTicketsStatistics.sold.map((item) => item.count),
          label: 'Created Reservations',
          borderColor: 'green',
          backgroundColor: 'green',
        },
        {
          data: eventTicketsStatistics.reserved.map((item) => item.count),
          label: 'Confirmed Reservations',
          borderColor: 'orange',
          backgroundColor: 'orange',
        },
      ],
    };
  }

  loadPieData(event: EventInfo) {
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
        backgroundColor: '#00C4FF',
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
  }
  countExpired(ticketCategory: TicketCategory[]) {
    return ticketCategory.filter((ticketCategory) => ticketCategory.expired)
      .length;
  }
  countActive(ticketCategory: TicketCategory[]) {
    return ticketCategory.filter((ticketCategory) => !ticketCategory.expired)
      .length;
  }

  getActualCapacity(ticketCategory: TicketCategory, event: EventInfo) {
    return ticketCategory.bounded
      ? ticketCategory.maxTickets
      : event.dynamicAllocation + ticketCategory.soldTickets;
  }

  toggleTokenViewCollapse(ticketCategory: UiTicketCategory) {
    ticketCategory.tokenViewExpanded = !ticketCategory.tokenViewExpanded;
  }

  openConfiguration(event: EventInfo, ticketCategory: TicketCategory) {
    alert('TODO');
  }

  containsValidTokens(tokens: TicketTokenStatus[]) {
    return tokens.every((token) => token.status !== 'WAITING');
  }

  unbindTickets(eventShortName: string, category: UiTicketCategory) {
    this.eventService.unbindTickets(eventShortName, category);
  }

  ngOnInit(): void {}
}

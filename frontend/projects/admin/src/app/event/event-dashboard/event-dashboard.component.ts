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
  ngOnInit(): void {}
}

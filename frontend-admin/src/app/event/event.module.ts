import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { provideSvgIconsConfig, SvgIconComponent } from '@ngneat/svg-icon';
import { RouterModule } from '@angular/router';
import { OrganizationService } from '../shared/organization.service';
import { EventService } from '../shared/event.service';
import { ICON_CONFIG } from '../shared/icons';
import { EventDashboardComponent } from './event-dashboard/event-dashboard.component';
import { EventMenuComponent } from './event-menu/event-menu.component';

@NgModule({
  declarations: [
    EventMenuComponent,
    EventDashboardComponent
  ],
  imports: [
    TranslateModule.forChild(),
    CommonModule,
    SvgIconComponent,
    RouterModule.forChild([
      {path: '', component: EventDashboardComponent},
      {path: '', component: EventMenuComponent, outlet: 'sidebar-content'}
    ])
  ],
  providers: [
    OrganizationService,
    EventService,
    provideSvgIconsConfig(ICON_CONFIG)
  ]
})
export class EventModule { }

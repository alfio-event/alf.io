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
import { SharedModule } from '../shared/shared.module';

@NgModule({
  declarations: [EventMenuComponent, EventDashboardComponent],
  imports: [
    TranslateModule.forChild(),
    CommonModule,
    SvgIconComponent,
    SharedModule,
    RouterModule.forChild([{ path: '', component: EventDashboardComponent }]),
  ],
  providers: [
    OrganizationService,
    EventService,
    provideSvgIconsConfig(ICON_CONFIG),
  ],
})
export class EventModule {}

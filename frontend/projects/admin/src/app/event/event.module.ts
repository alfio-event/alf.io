import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgbCarouselModule, NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { provideSvgIconsConfig, SvgIconComponent } from '@ngneat/svg-icon';
import { TranslateModule } from '@ngx-translate/core';
import { NgChartsModule } from 'ng2-charts';
import { EventService } from '../shared/event.service';
import { ICON_CONFIG } from '../shared/icons';
import { OrganizationService } from '../shared/organization.service';
import { SharedModule } from '../shared/shared.module';
import { EventDashboardComponent } from './event-dashboard/event-dashboard.component';
import { EventMenuComponent } from './event-menu/event-menu.component';
import { TranslatePaymentProxiesPipe } from './translate-payment-proxies.pipe';

@NgModule({
  declarations: [EventMenuComponent, EventDashboardComponent, TranslatePaymentProxiesPipe],
  imports: [
    TranslateModule.forChild(),
    CommonModule,
    SvgIconComponent,
    SharedModule,
    NgChartsModule,
    NgbCarouselModule,
    NgbNavModule,
    RouterModule.forChild([{ path: '', component: EventDashboardComponent }]),
  ],
  providers: [
    OrganizationService,
    EventService,
    provideSvgIconsConfig(ICON_CONFIG),
  ],
})
export class EventModule {}

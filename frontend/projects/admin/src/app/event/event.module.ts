import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import {
  NgbCarouselModule,
  NgbDropdownModule,
  NgbNavModule,
  NgbTypeaheadModule,
} from '@ng-bootstrap/ng-bootstrap';
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
import { EmailLogComponent } from './email-log/email-log.component';
import { ShowSelectedCategoriesPipe } from './show-selected-categories.pipe';
import { UiCategoryBuilderPipe } from './ui-category-builder.pipe';
import { FormsModule } from '@angular/forms';
import { TicketCategoryDetailComponent } from './ticket-category-detail/ticket-category-detail.component';
import { MailService } from '../../event/mail.service';
import { EmailDetailComponent } from './email-detail/email-detail.component';

@NgModule({
  declarations: [
    EventMenuComponent,
    EventDashboardComponent,
    TranslatePaymentProxiesPipe,
    EmailLogComponent,
    ShowSelectedCategoriesPipe,
    UiCategoryBuilderPipe,
    TicketCategoryDetailComponent,
    EmailDetailComponent,
  ],
  imports: [
    TranslateModule.forChild(),
    CommonModule,
    SvgIconComponent,
    SharedModule,
    NgChartsModule,
    NgbCarouselModule,
    NgbNavModule,
    FormsModule,
    NgbDropdownModule,

    RouterModule.forChild([
      { path: '', component: EventDashboardComponent },
      { path: 'email-log', component: EmailLogComponent },
      { path: 'email-log/email-detail', component: EmailDetailComponent },
    ]),
  ],
  providers: [
    OrganizationService,
    EventService,
    MailService,
    provideSvgIconsConfig(ICON_CONFIG),
  ],
})
export class EventModule {}

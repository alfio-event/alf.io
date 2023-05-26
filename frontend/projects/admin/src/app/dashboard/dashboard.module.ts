import { NgModule } from '@angular/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { DashboardComponent } from './dashboard.component';
import { RouterModule } from '@angular/router';
import { OrganizationService } from '../shared/organization.service';
import { CommonModule } from '@angular/common';
import { EventService } from '../shared/event.service';
import { OrganizationConfigurationComponent } from './organization-configuration/organization-configuration.component';
import { provideSvgIconsConfig, SvgIconComponent } from '@ngneat/svg-icon';
import { TranslateModule } from '@ngx-translate/core';
import { ICON_CONFIG } from '../shared/icons';
import { SubscriptionsComponent } from './subscriptions/subscriptions.component';
import { OrganizationInfoComponent } from './organization-info/organization-info.component';
import { GroupsComponent } from './groups/groups.component';
import { FilterButtonComponent } from '../shared/filter-button/filter-button.component';
import { EventItemComponent } from './event-item/event-item.component';
import { ExportDateSelectorComponent } from './export-date-selector/export-date-selector.component';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';

@NgModule({
  imports: [
    TranslateModule.forChild(),
    CommonModule,
    NgbModule,
    RouterModule.forChild([
      { path: 'event', component: DashboardComponent },
      { path: 'configuration', component: OrganizationConfigurationComponent },
      { path: 'subscriptions', component: SubscriptionsComponent },
      { path: 'organization-info', component: OrganizationInfoComponent },
      { path: 'groups', component: GroupsComponent },
    ]),
    SvgIconComponent,
    FilterButtonComponent,
    ReactiveFormsModule,
    FormsModule,
  ],
  declarations: [
    DashboardComponent,
    OrganizationConfigurationComponent,
    SubscriptionsComponent,
    OrganizationInfoComponent,
    GroupsComponent,
    EventItemComponent,
    ExportDateSelectorComponent,
  ],
  providers: [
    OrganizationService,
    EventService,
    provideSvgIconsConfig(ICON_CONFIG),
  ],
})
export class DashboardModule {}

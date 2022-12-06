import {NgModule} from "@angular/core";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {DashboardComponent} from "./dashboard.component";
import {RouterModule} from "@angular/router";
import {DashboardMenuComponent} from './dashboard-menu/dashboard-menu.component';
import {OrganizationService} from "../shared/organization.service";
import {CommonModule} from "@angular/common";
import {EventService} from "../shared/event.service";
import {OrganizationConfigurationComponent} from './organization-configuration/organization-configuration.component';
import {provideSvgIcons, SvgIconComponent} from '@ngneat/svg-icon';
import { ICONS } from "../shared/icons";

@NgModule({
  imports: [
    CommonModule,
    NgbModule,
    RouterModule.forChild([
      { path: '', component: DashboardComponent},
      { path: '', component: DashboardMenuComponent, outlet: 'sidebar-content'},
      { path: 'configuration', component: OrganizationConfigurationComponent }
    ]),
    SvgIconComponent,
  ],
  declarations: [
    DashboardComponent,
    DashboardMenuComponent,
    OrganizationConfigurationComponent,
  ],
  providers: [
    OrganizationService,
    EventService,
    provideSvgIcons(ICONS),
  ]
})
export class DashboardModule {}

import {NgModule} from "@angular/core";
import {SectionDashboardComponent} from "./section-dashboard/section-dashboard.component";
import {UserNamePipe} from "./user-name.pipe";
import {NgIf} from "@angular/common";
import { UsersFilterOrgPipe } from "./users-filter-org.pipe";

@NgModule({
  declarations: [
    SectionDashboardComponent,
    UserNamePipe,
    UsersFilterOrgPipe,
  ],
  imports: [
    NgIf
  ],
  exports: [
    SectionDashboardComponent,
    UserNamePipe,
    UsersFilterOrgPipe,
  ]
})
export class SharedModule {}

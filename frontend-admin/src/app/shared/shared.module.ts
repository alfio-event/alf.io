import {NgModule} from "@angular/core";
import {SectionDashboardComponent} from "./section-dashboard/section-dashboard.component";
import {UserNamePipe} from "./user-name.pipe";
import {NgIf} from "@angular/common";

@NgModule({
  declarations: [
    SectionDashboardComponent,
    UserNamePipe,
  ],
  imports: [
    NgIf
  ],
  exports: [
    SectionDashboardComponent,
    UserNamePipe,
  ]
})
export class SharedModule {}

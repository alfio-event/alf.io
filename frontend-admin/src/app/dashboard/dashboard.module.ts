import {NgModule} from "@angular/core";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {DashboardComponent} from "./dashboard.component";
import {RouterModule} from "@angular/router";

@NgModule({
  imports: [
    NgbModule,
    RouterModule.forChild([
      { path: '', component: DashboardComponent}
    ])
  ],
  declarations: [
    DashboardComponent
  ]
})
export class DashboardModule {}

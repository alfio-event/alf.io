import {Component, Input} from "@angular/core";

@Component({
  selector: 'app-section-dashboard',
  templateUrl: './section-dashboard.component.html'
})
export class SectionDashboardComponent {

  @Input()
  hasMenu = false;

}




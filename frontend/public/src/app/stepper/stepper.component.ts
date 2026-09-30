import {Component, Input} from '@angular/core';

@Component({
  selector: 'app-stepper',
  templateUrl: './stepper.component.html',
  styleUrls: ['./stepper.component.scss']
})
export class StepperComponent {

  @Input()
  free = true;

  @Input()
  currentStep = 1;

  @Input()
  inProgress = false;

  constructor() { }
}

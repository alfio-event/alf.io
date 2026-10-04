import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';

import {PollRoutingModule} from './poll-routing.module';
import {PollComponent} from './poll.component';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {DisplayPollComponent} from './display-poll/display-poll.component';
import {TranslateModule} from '@ngx-translate/core';
import {SharedModule} from '../shared/shared.module';
import {PollSelectionComponent} from './poll-selection/poll-selection.component';


@NgModule({
  declarations: [PollComponent, DisplayPollComponent, PollSelectionComponent],
  imports: [
    TranslateModule.forChild(),
    CommonModule,
    PollRoutingModule,
    FormsModule,
    ReactiveFormsModule,
    SharedModule
  ]
})
export class PollModule { }

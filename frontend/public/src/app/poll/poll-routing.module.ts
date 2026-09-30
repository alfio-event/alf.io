import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {PollComponent} from './poll.component';
import {PollService} from './shared/poll.service';
import {DisplayPollComponent} from './display-poll/display-poll.component';
import {PollSelectionComponent} from './poll-selection/poll-selection.component';

const routes: Routes = [
  { path: '', component: PollComponent, children: [
    {path: '', component: PollSelectionComponent },
    {path: ':pollId', component: DisplayPollComponent }
  ]}
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [PollService]
})
export class PollRoutingModule { }

import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { SharedModule } from '../shared/shared.module';
import { DisplayPollComponent } from './display-poll/display-poll.component';
import { PollComponent } from './poll.component';
import { PollRoutingModule } from './poll-routing.module';
import { PollSelectionComponent } from './poll-selection/poll-selection.component';

@NgModule({
    declarations: [PollComponent, DisplayPollComponent, PollSelectionComponent],
    imports: [
        TranslateModule.forChild(),
        CommonModule,
        PollRoutingModule,
        FormsModule,
        ReactiveFormsModule,
        SharedModule,
    ],
})
export class PollModule {}

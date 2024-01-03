import {FormControl} from '@angular/forms';

export interface PollVoteForm {
  optionId: FormControl<number>;
}

export interface PollVotePayload {
  pin: string;
  optionId: number;
}

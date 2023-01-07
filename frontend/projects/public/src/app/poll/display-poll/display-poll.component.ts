import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {PollService} from '../shared/poll.service';
import {combineLatest} from 'rxjs';
import {PollWithOptions} from '../model/poll-with-options';
import {TranslateService} from '@ngx-translate/core';
import {FormGroup, UntypedFormBuilder} from '@angular/forms';
import {PollVoteForm} from '../model/poll-vote-form';

@Component({
  selector: 'app-display-poll',
  templateUrl: './display-poll.component.html',
  styleUrls: ['./display-poll.component.scss']
})
export class DisplayPollComponent implements OnInit {


  eventShortName: string;
  pollId: number;
  pin: string;
  poll: PollWithOptions;

  pollForm: FormGroup<PollVoteForm>;
  pollSubmittedWithSuccess: boolean;

  constructor(
    private route: ActivatedRoute,
    public translate: TranslateService,
    private pollService: PollService,
    private fb: UntypedFormBuilder) { }

  ngOnInit(): void {

    this.pollForm = this.fb.group({optionId: null});

    combineLatest([this.route.parent.params, this.route.params, this.route.queryParams]).subscribe(([parentParams, params, query]) => {
      this.eventShortName = parentParams['eventShortName'];
      this.pollId = parseInt(params['pollId']);
      this.pin = query['pin'];
      this.loadPoll()
    });
  }


  loadPoll() {
    this.pollService.getPoll(this.eventShortName, this.pollId, this.pin).subscribe(res => {
      if (res.success) {
        this.poll = res.value;
      }
    })
  }

  submitChoice() {
    this.pollService.registerAnswer(this.eventShortName, this.pollId, {pin: this.pin, optionId: this.pollForm.value.optionId}).subscribe(res => {
      this.pollSubmittedWithSuccess = res.success && res.value;
    })
  }
}

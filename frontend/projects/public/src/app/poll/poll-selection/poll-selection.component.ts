import { Component, type OnInit } from '@angular/core';
import {
    type FormControl,
    type FormGroup,
    UntypedFormBuilder,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { combineLatest } from 'rxjs';
import type { ErrorDescriptor } from '../../model/validated-response';
import { handleServerSideValidationError } from '../../shared/validation-helper';
import type { Poll } from '../model/poll';
import { PollService } from '../shared/poll.service';

@Component({
    selector: 'app-poll-selection',
    templateUrl: './poll-selection.component.html',
    styleUrls: ['./poll-selection.component.scss'],
})
export class PollSelectionComponent implements OnInit {
    pinForm: FormGroup<{
        pin: FormControl<string>;
    }>;
    polls: Poll[];
    eventShortName: string;
    globalErrors: ErrorDescriptor[];
    ready: boolean;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private pollService: PollService,
        public translate: TranslateService,
        private fb: UntypedFormBuilder,
    ) {}

    ngOnInit(): void {
        this.pinForm = this.fb.group({ pin: null });
        combineLatest([this.route.params, this.route.queryParams]).subscribe(
            ([params, query]) => {
                this.eventShortName = params['eventShortName'];
                if (query['pin']) {
                    this.loadPolls(this.eventShortName, query['pin']);
                } else {
                    this.ready = true;
                }
            },
        );
    }

    private loadPolls(eventShortName: string, pin: string) {
        this.ready = false;
        this.pollService.getAllPolls(eventShortName, pin).subscribe(
            (res) => {
                if (res.success) {
                    this.router.navigate([], {
                        queryParamsHandling: 'merge',
                        queryParams: { pin: pin },
                    });
                    this.polls = res.value;
                    if (this.polls.length === 1) {
                        this.router.navigate([this.polls[0].id], {
                            relativeTo: this.route,
                            queryParamsHandling: 'merge',
                            queryParams: { pin: pin },
                        });
                    }
                }
                this.ready = true;
            },
            (err) => {
                this.globalErrors = handleServerSideValidationError(
                    err,
                    this.pinForm,
                );
                this.ready = true;
            },
        );
    }

    confirmPin() {
        const pin = this.pinForm.value.pin;
        this.loadPolls(this.eventShortName, pin);
    }
}

import {Component, Input} from "@angular/core";
import {ChallengeConfiguration} from "../model/info";
import {NgbActiveModal} from "@ng-bootstrap/ng-bootstrap";
import {ChallengeError} from "./challenge.service";

@Component({
    selector: 'app-challenge',
    templateUrl: './challenge.component.html'
})
export class ChallengeComponent {
    @Input()
    challengeConfiguration?: ChallengeConfiguration;

    constructor(public activeModal: NgbActiveModal) {
    }

    get providerId(): string {
        return this.challengeConfiguration?.providerId ?? '';
    }


    tokenResponse(token: string) {
        this.activeModal.close(token);
    }

    onError(err: ChallengeError) {
        this.activeModal.dismiss(err);
    }

    dismiss() {
        this.activeModal.dismiss(new ChallengeError());
    }
}

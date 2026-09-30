import {Injectable} from "@angular/core";
import {HttpErrorResponse} from "@angular/common/http";
import {from, Observable, throwError} from "rxjs";
import {catchError, mergeMap, tap} from "rxjs/operators";
import {ChallengeComponent} from "./challenge.component";
import {InfoService} from "../shared/info.service";
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";

@Injectable({
    providedIn: 'root'
})
export class ChallengeService {

    private challengeToken?: string;

    constructor(private infoService: InfoService,
                private modalService: NgbModal) {
    }

    setChallengeToken(token?: string) {
        this.challengeToken = token;
    }

    getChallengeToken(): string | undefined {
        return this.challengeToken;
    }

    requestChallenge(): Observable<string> {
        return this.infoService.getInfo().pipe(mergeMap(info => {
            const modalRef = this.modalService.open(ChallengeComponent, {centered: true, backdrop: 'static'});
            modalRef.componentInstance.challengeConfiguration = info.challengeConfiguration;
            return from(modalRef.result)
                // notify error to caller
                .pipe(
                    tap(token => this.setChallengeToken(token)),
                    catchError(err => throwError(() => new ChallengeError(err)))
                );
        }));
    }

    public static isChallengeError(err: any): boolean {

        return err instanceof ChallengeError
            || (err instanceof HttpErrorResponse
            && err.status === 403
            && (err.headers.has('Alfio-Verification-Missing') || err.headers.get('cf-mitigated') === 'challenge'));
    }

}

export class ChallengeError implements Error {
    private cause?: Error;

    constructor(cause?: Error) {
        this.cause = cause;
    }

    get name(): string {
        return this.cause?.name ?? 'Challenge Error';
    }

    get message(): string {
        return this.cause?.message ?? 'Error while getting challenge';
    }

}

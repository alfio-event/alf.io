import {HttpEvent, HttpHandler, HttpInterceptor, HttpRequest} from "@angular/common/http";
import {Injectable} from "@angular/core";
import {Observable, throwError} from "rxjs";
import {ChallengeService} from "./challenge.service";
import {catchError, switchMap} from "rxjs/operators";


const ALFIO_VERIFICATION_HEADER = 'Alfio-Verification';

@Injectable()
export class ChallengeCodeInterceptor implements HttpInterceptor {

    constructor(private challengeService: ChallengeService) {
    }

    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        // retrieve token if already present
        const token = this.challengeService.getChallengeToken();
        let request: HttpRequest<any>;
        if (token != null) {
            request = req.clone({
                headers: req.headers.set(ALFIO_VERIFICATION_HEADER, token)
            });
        } else {
            request = req;
        }
        return next.handle(request).pipe(
            catchError(err => {
                if (ChallengeService.isChallengeError(err)) {
                    // in case we recognize a challenge error, we need to get the new code and retry.
                    return this.challengeService.requestChallenge().pipe(switchMap(code => {
                        return next.handle(req.clone({
                            headers: req.headers.set(ALFIO_VERIFICATION_HEADER, code)
                        }));
                    }), catchError(challengeError => {
                        this.challengeService.setChallengeToken(undefined);
                        return throwError(challengeError);
                    }));
                }
                return throwError(err);
            })
        );
    }
}

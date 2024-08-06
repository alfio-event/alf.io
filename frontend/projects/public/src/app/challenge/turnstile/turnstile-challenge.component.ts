import {AfterViewInit, Component, EventEmitter, Input, OnDestroy, Output} from "@angular/core";
import {I18nService} from "../../shared/i18n.service";
import {ChallengeError} from "../challenge.service";

let idCallback = 0;

declare const turnstile: {
    render: (string, any) => any
    remove: (string) => void
};

@Component({
    selector: 'app-turnstile-challenge',
    template: '<div class="cf-turnstile mt-2 mb-2" data-theme="light"></div>'
})
export class TurnstileChallengeComponent implements AfterViewInit, OnDestroy {

    @Input()
    siteKey?: string;
    @Output()
    tokenResult = new EventEmitter<string>();
    @Output()
    tokenError = new EventEmitter<ChallengeError>();
    private widgetId?: string;

    constructor(private i18nService: I18nService) {
    }

    ngAfterViewInit(): void {
        if (!document.getElementById('cf-turnstile-script')) {
            const scriptElem = document.createElement('script');
            idCallback++;
            const callBackName = `onloadTurnstileCallback${idCallback}`;

            scriptElem.src = `https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&onload=${callBackName}&language=${this.i18nService.getCurrentLang()}`;
            scriptElem.id = 'recaptcha-api-script';
            scriptElem.async = true;
            scriptElem.defer = true;

            window[callBackName] = () => {
                this.init();
            };
            document.body.appendChild(scriptElem);
        }
    }

    ngOnDestroy(): void {
        if (this.widgetId != null) {
            turnstile.remove(this.widgetId);
        }
    }

    private init(): void {
        if (this.siteKey != null) {
            const notify = (token: string | undefined) => {
                if (token != null) {
                    this.tokenResult.next(token);
                } else {
                    this.tokenError.next(new ChallengeError());
                }
            };
            this.widgetId = turnstile.render('.cf-turnstile', {
                sitekey: this.siteKey,
                "response-field": false,
                callback: function(token: string, preClearance: boolean) {
                    console.log(`Challenge Success ${token}. Pre-clearance ${preClearance}`);
                    notify(token);
                },
                "expired-callback": function () {
                    notify(undefined);
                }
            });
        } else {
            console.error('cannot initialize turnstile');
        }
    }
}

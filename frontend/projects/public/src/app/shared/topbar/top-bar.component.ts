import {
    type AfterViewInit,
    Component,
    Input,
    type OnDestroy,
    type OnInit,
} from '@angular/core';
import { Router } from '@angular/router';
import type { Subscription } from 'rxjs';
import type { Language } from '../../model/event';
import { ANONYMOUS, type User } from '../../model/user';
import { FeedbackService } from '../feedback/feedback.service';
import { UserService } from '../user.service';
import {
    DELETE_ACCOUNT_CONFIRMATION,
    getFromSessionStorage,
    removeFromSessionStorage,
} from '../util';

@Component({
    selector: 'app-topbar',
    templateUrl: './top-bar.component.html',
})
export class TopBarComponent implements OnInit, OnDestroy, AfterViewInit {
    private authenticationStatusSubscription?: Subscription;
    @Input()
    contentLanguages: Language[];
    @Input()
    displayLoginButton = true;
    user?: User;
    authenticationEnabled = false;
    private root = document.querySelector(':root') as HTMLElement;

    constructor(
        private userService: UserService,
        private router: Router,
        private feedbackService: FeedbackService,
    ) {}

    ngOnInit(): void {
        this.authenticationStatusSubscription =
            this.userService.authenticationStatus.subscribe(
                (authenticationStatus) => {
                    this.authenticationEnabled = authenticationStatus.enabled;
                    if (authenticationStatus.user !== ANONYMOUS) {
                        this.user = authenticationStatus.user;
                    }
                },
            );
    }

    ngAfterViewInit(): void {
        if (getFromSessionStorage(DELETE_ACCOUNT_CONFIRMATION) === 'y') {
            this.feedbackService.showSuccess('my-profile.delete.success');
            removeFromSessionStorage(DELETE_ACCOUNT_CONFIRMATION);
        } else if (this.root?.getAttribute('data-signed-up') != null) {
            this.feedbackService.showSuccess('my-profile.sign-up.success');
            this.root.removeAttribute('data-signed-up');
        }
    }

    ngOnDestroy(): void {
        this.authenticationStatusSubscription?.unsubscribe();
    }

    get anonymous(): boolean {
        return this.user === ANONYMOUS;
    }

    logout(): void {
        this.userService.logout().subscribe((response) => {
            this.user = undefined;
            if (!response.empty) {
                window.location.href = response.targetUrl;
            }
        });
    }

    myOrders(): void {
        this.router.navigate(['my-orders']);
    }

    myProfile(): void {
        this.router.navigate(['my-profile']);
    }
}

import {AfterViewInit, Component, Input, OnDestroy, OnInit} from '@angular/core';
import {UserService} from '../user.service';
import {ANONYMOUS, User} from '../../model/user';
import {Language} from '../../model/event';
import {Subscription} from 'rxjs';
import {Router} from '@angular/router';
import {FeedbackService} from '../feedback/feedback.service';
import {DELETE_ACCOUNT_CONFIRMATION, getFromSessionStorage, removeFromSessionStorage} from '../util';

@Component({
  selector: 'app-topbar',
  templateUrl: './top-bar.component.html'
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

  constructor(private userService: UserService,
              private router: Router,
              private feedbackService: FeedbackService) {
  }

  ngOnInit(): void {
    this.authenticationStatusSubscription = this.userService.authenticationStatus.subscribe(authenticationStatus => {
      this.authenticationEnabled = authenticationStatus.enabled;
      if (authenticationStatus.user !== ANONYMOUS) {
        this.user = authenticationStatus.user;
      }
    });
  }

  ngAfterViewInit(): void {
    if (getFromSessionStorage(DELETE_ACCOUNT_CONFIRMATION) === 'y') {
      this.feedbackService.showSuccess('my-profile.delete.success');
      removeFromSessionStorage(DELETE_ACCOUNT_CONFIRMATION);
    } else if (this.root != null && this.root.getAttribute('data-signed-up') != null) {
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
    this.userService.logout().subscribe(response => {
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

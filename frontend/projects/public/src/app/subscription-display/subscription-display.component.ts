import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {SubscriptionInfo} from '../model/subscription';
import {AnalyticsService} from '../shared/analytics.service';
import {InfoService} from '../shared/info.service';
import {SubscriptionService} from '../shared/subscription.service';
import {zip} from 'rxjs';
import {TranslateService} from '@ngx-translate/core';
import {I18nService} from '../shared/i18n.service';
import {getErrorObject} from '../shared/validation-helper';
import {ErrorDescriptor} from '../model/validated-response';
import {FeedbackService} from '../shared/feedback/feedback.service';

@Component({
  selector: 'app-subscription-display',
  templateUrl: './subscription-display.component.html',
  styleUrls: ['./subscription-display.component.scss']
})
export class SubscriptionDisplayComponent implements OnInit {


  submitError: ErrorDescriptor;
  subscription: SubscriptionInfo;
  subscriptionId: string;
  submitInProgress: boolean = false;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private subscriptionService: SubscriptionService,
              private info: InfoService,
              private analytics: AnalyticsService,
              public translateService: TranslateService,
              private i18nService: I18nService,
              private feedbackService: FeedbackService) { }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.subscriptionId = params['id'];
      zip(this.subscriptionService.getSubscriptionById(this.subscriptionId), this.info.getInfo()).subscribe(([subscription, info]) => {
        this.subscription = subscription;
        this.i18nService.setPageTitle('show-subscription.header.title', subscription);
        this.analytics.pageView(info.analyticsConfiguration);
      });
    });
  }


  submitForm() {
      if (this.submitInProgress) {
          // ignoring click, as there is a pending request
          return;
      }
      this.submitInProgress = true;
      this.subscriptionService.reserve(this.subscriptionId).subscribe({
          next: res => {
              this.submitInProgress = false;
              this.router.navigate(['subscription', this.subscriptionId, 'reservation', res.value, 'book']);
          },
          error: err => {
              this.submitInProgress = false;
              const errorObject = getErrorObject(err);
              let errorCode: string;
              if (errorObject != null) {
                  errorCode = errorObject.validationErrors[0].code;
              } else {
                  errorCode = 'reservation-page-error-status.header.title';
              }
              this.feedbackService.showError(errorCode);
          }
      });
  }

}

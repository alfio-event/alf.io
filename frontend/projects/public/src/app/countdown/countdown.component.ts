import {Component, EventEmitter, Input, OnDestroy, OnInit, Output} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import * as countdown from './countdown'; // see issue: https://stackoverflow.com/a/70506557 + https://github.com/mckamey/countdownjs/issues/39
import {Subscription} from 'rxjs';

@Component({
  selector: 'app-countdown',
  templateUrl: './countdown.component.html',
  styleUrls: ['./countdown.scss']
})
export class CountdownComponent implements OnInit, OnDestroy {

  @Input()
  validity: number;

  @Output()
  expired: EventEmitter<boolean> = new EventEmitter<boolean>();

  timerId: number;

  message: string;
  isExpired: boolean;
  alertType = 'info';
  displaySticky = false;

  private langChangeSub: Subscription;

  constructor(private translateService: TranslateService) { }

  ngOnInit() {
    this.setupCountdown();

    this.langChangeSub = this.translateService.onLangChange.subscribe(e => {
      this.setupCountdown();
    });
  }

  ngOnDestroy() {
    if (this.langChangeSub) {
      this.langChangeSub.unsubscribe();
    }
    clearInterval(this.timerId);
  }

  private setupCountdown(): void {

    clearInterval(this.timerId);

    const msg = this.translateService.instant('reservation-page.time-for-completion');
    const singular = this.translateService.instant('reservation-page.time-for-completion.labels.singular');
    const plural = this.translateService.instant('reservation-page.time-for-completion.labels.plural');
    const and = this.translateService.instant('reservation-page.time-for-completion.labels.and');
    const oneSecond   = 1000;
    const oneMinute   = 60 * oneSecond;
    const fiveMinutes = 5 * oneMinute;

    countdown.setLabels(singular, plural, ' ' + and + ' ', ', ');
    this.timerId = countdown(new Date(this.validity), (ts) => {
      const absDifference = Math.abs(ts.value);
      if (ts.value < 0 && absDifference >= oneSecond) {
        if (absDifference < fiveMinutes) {
          this.alertType = absDifference < oneMinute ? 'danger' : 'warning';
          this.displaySticky = true;
        }
        this.message = msg.replace('##time##', ts.toHTML('strong'));
      } else {
        this.isExpired = true;
        this.alertType = 'danger';
        clearInterval(this.timerId);
        this.expired.emit(true);
      }
    // tslint:disable-next-line: no-bitwise
    }, countdown.MONTHS | countdown.WEEKS | countdown.DAYS | countdown.HOURS | countdown.MINUTES | countdown.SECONDS) as number;
  }

}

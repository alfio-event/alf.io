import {AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {Subscription} from 'rxjs';


declare const grecaptcha: any;

let idCallback = 0;

@Component({
  selector: 'app-recaptcha',
  template: '<div #targetElement class="c-container"></div>'
})
export class RecaptchaComponent implements OnDestroy, AfterViewInit {

  @Input()
  apiKey: string;

  private langSub: Subscription;

  @ViewChild('targetElement', { static: true })
  private targetElement: ElementRef<HTMLDivElement>;

  @Output()
  recaptchaResponse: EventEmitter<string> = new EventEmitter<string>();


  private widgetId: any = null;

  constructor(private translate: TranslateService) { }

  ngAfterViewInit() {
    if (!document.getElementById('recaptcha-api-script')) {
      const scriptElem = document.createElement('script');

      idCallback++;

      const callBackName = `onloadRecaptchaCallback${idCallback}`;

      scriptElem.src = `https://www.google.com/recaptcha/api.js?onload=${callBackName}&render=explicit`;
      scriptElem.id = 'recaptcha-api-script';
      scriptElem.async = true;
      scriptElem.defer = true;

      window[callBackName] = () => {
        this.enableRecaptcha();
      };
      document.body.appendChild(scriptElem);
    } else if (!window['grecaptcha']) {
      // wait until it's available
      const waiter = () => {
        if (window['grecaptcha']) {
          this.enableRecaptcha();
        } else {
          setTimeout(waiter, 100);
        }
      };
      setTimeout(waiter, 100);
    } else {
      this.enableRecaptcha();
    }

    this.langSub = this.translate.onLangChange.subscribe(change => {
      this.enableRecaptcha();
    });
  }

  ngOnDestroy() {
    if (this.langSub) {
      this.langSub.unsubscribe();
    }
    if (window['grecaptcha'] && this.widgetId !== null) {
      grecaptcha.reset(this.widgetId);
    }
  }

  enableRecaptcha() {

    // delete container if present
    const range = document.createRange();
    range.selectNodeContents(this.targetElement.nativeElement);
    range.deleteContents();
    //

    const container = document.createElement('div');
    this.targetElement.nativeElement.appendChild(container);
    //


    if (window['grecaptcha']) {
      this.widgetId = grecaptcha.render(container, {
        sitekey: this.apiKey,
        hl: this.translate.currentLang,
        callback: (res) => {
          this.recaptchaResponse.emit(res);
        }
      });
    }
  }

}

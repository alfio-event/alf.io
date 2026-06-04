import {
  Component,
  EventEmitter,
  Input,
  type OnChanges,
  Output,
  type SimpleChanges,
} from "@angular/core";
import type { UntypedFormGroup } from "@angular/forms";
import type { PaymentMethodId, PaymentProxy } from "../../model/event";
import {
  type PaymentProvider,
  SimplePaymentProvider,
} from "../payment-provider";

@Component({
  selector: "app-onsite-payment-proxy",
  templateUrl: "./onsite-payment-proxy.component.html",
})
export class OnsitePaymentProxyComponent implements OnChanges {
  @Input()
  method: PaymentMethodId;

  @Input()
  proxy: PaymentProxy;

  @Input()
  parameters: { [key: string]: any };

  @Input()
  overviewForm: UntypedFormGroup;

  @Output()
  paymentProvider: EventEmitter<PaymentProvider> =
    new EventEmitter<PaymentProvider>();

  constructor() {}

  ngOnChanges(changes: SimpleChanges): void {
    if (this.matchProxyAndMethod && changes.method) {
      this.paymentProvider.emit(new SimplePaymentProvider());
    }
  }

  public get matchProxyAndMethod(): boolean {
    return this.method === "ON_SITE" && this.proxy === "ON_SITE";
  }

  handleRecaptchaResponse(recaptchaValue: string) {
    this.overviewForm.get("captcha").setValue(recaptchaValue);
  }
}

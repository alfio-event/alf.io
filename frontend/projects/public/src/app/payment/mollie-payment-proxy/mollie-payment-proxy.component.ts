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
import type { PaymentProvider } from "../payment-provider";
import { MolliePaymentProvider } from "./mollie-payment-provider";

@Component({
  selector: "app-mollie-payment-proxy",
  templateUrl: "./mollie-payment-proxy.component.html",
})
export class MolliePaymentProxyComponent implements OnChanges {
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

  private compatibleMethods: PaymentMethodId[] = [
    "CREDIT_CARD",
    "IDEAL",
    "APPLE_PAY",
    "BANCONTACT",
    "BELFIUS",
    "ING_HOME_PAY",
    "KBC",
    "PRZELEWY_24",
  ];

  constructor() {}

  ngOnChanges(changes: SimpleChanges): void {
    if (this.matchProxyAndMethod && changes.method) {
      this.paymentProvider.emit(new MolliePaymentProvider());
    }
  }

  public get matchProxyAndMethod(): boolean {
    return (
      this.compatibleMethods.includes(this.method) && this.proxy === "MOLLIE"
    );
  }
}

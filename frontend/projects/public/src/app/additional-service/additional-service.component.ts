import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {AdditionalService} from '../model/additional-service';
import {TranslateService} from '@ngx-translate/core';
import {UntypedFormArray, UntypedFormBuilder, UntypedFormGroup} from '@angular/forms';
import {Subscription} from 'rxjs';
import {Event} from '../model/event';

@Component({
  selector: 'app-additional-service',
  templateUrl: './additional-service.component.html',
  styleUrls: ['./additional-service.component.scss']
})
export class AdditionalServiceComponent implements OnInit, OnDestroy {

  @Input()
  additionalService: AdditionalService;

  @Input()
  form: UntypedFormGroup;

  additionalServiceFormGroup: UntypedFormGroup;

  @Input()
  event: Event;

  validSelectionValues: number[] = [];

  availableForSale = true;

  private formSub: Subscription;

  constructor(public translate: TranslateService, private formBuilder: UntypedFormBuilder) { }

  public ngOnInit(): void {

    const fa = this.form.get('additionalService') as UntypedFormArray;

    if (this.additionalService.fixPrice) {
      this.additionalServiceFormGroup = this.formBuilder.group({additionalServiceId: this.additionalService.id, quantity: null});
    } else {
      this.additionalServiceFormGroup = this.formBuilder.group({additionalServiceId: this.additionalService.id, amount: null});
    }
    fa.push(this.additionalServiceFormGroup);

    // we only need to recalculate the select box choice in this specific supplement policy!
    const availableQuantity = this.additionalService.availableQuantity ?? 999;
    if (availableQuantity === 0 || this.additionalService.saleInFuture || this.additionalService.expired) {
      this.availableForSale = false;
    } else if (this.additionalService.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_TICKET') {
      this.formSub = this.form.get('reservation').valueChanges.subscribe(valueChange => {
        const selectedTicketCount = (valueChange as {amount: string}[]).map(a => parseInt(a.amount, 10)).reduce((sum, n) => sum + n, 0);
        const maxPerOrder = selectedTicketCount * this.additionalService.maxQtyPerOrder;
        const rangeEnd = availableQuantity >= 0 ? Math.min(maxPerOrder, availableQuantity) : maxPerOrder;
        const res = [];
        for (let i = 0; i <= rangeEnd; i++) {
          res.push(i);
        }
        this.validSelectionValues = res;
      });
    } else if (this.additionalService.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_RESERVATION' ||
                this.additionalService.supplementPolicy === null) {
      const res = [];
      const maxPerOrder = this.additionalService.maxQtyPerOrder;
      for (let i = 0; i <= Math.min(maxPerOrder, availableQuantity); i++) {
        res.push(i);
      }
      this.validSelectionValues = res;
    }
  }

  public ngOnDestroy(): void {
    if (this.formSub) {
      this.formSub.unsubscribe();
    }
  }

}

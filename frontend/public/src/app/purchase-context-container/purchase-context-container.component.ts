import {Component, Input} from '@angular/core';
import {PurchaseContext} from '../model/purchase-context';

@Component({
  selector: 'app-purchase-context-container',
  templateUrl: './purchase-context-container.component.html'
})
export class PurchaseContextContainerComponent {
  @Input()
  purchaseContext: PurchaseContext;

  @Input()
  displayFooterLinks = true;
}

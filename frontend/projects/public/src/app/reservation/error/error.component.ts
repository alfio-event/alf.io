import {Component, OnInit} from '@angular/core';
import {I18nService} from '../../shared/i18n.service';
import {ActivatedRoute} from '@angular/router';
import {PurchaseContext} from '../../model/purchase-context';
import {zip} from 'rxjs';
import {PurchaseContextService} from '../../shared/purchase-context.service';

@Component({
  selector: 'app-error',
  templateUrl: './error.component.html'
})
export class ErrorComponent implements OnInit {


  reservationId: string;
  purchaseContext: PurchaseContext;

  constructor(
    private route: ActivatedRoute,
    private purchaseContextService: PurchaseContextService,
    private i18nService: I18nService) { }

  ngOnInit() {
    zip(this.route.data, this.route.params).subscribe(([data, params]) => {
      const publicIdentifier = params[data.publicIdentifierParameter];
      this.reservationId = params['reservationId'];
      const purchaseContextType = data.type;
      this.purchaseContextService.getContext(purchaseContextType, publicIdentifier).subscribe(ev => {
        this.purchaseContext = ev;
        this.i18nService.setPageTitle('reservation-page-not-found.header.title', ev);
      });
    });
  }

}

import {Component, OnInit} from '@angular/core';
import {I18nService} from '../../shared/i18n.service';
import {ActivatedRoute, Router, UrlSerializer} from '@angular/router';
import {zip} from 'rxjs';
import {PurchaseContextService, PurchaseContextType} from '../../shared/purchase-context.service';

@Component({
  selector: 'app-not-found',
  templateUrl: './not-found.component.html'
})
export class NotFoundComponent implements OnInit {

  private purchaseContextType: PurchaseContextType;
  private publicIdentifier: string;
  reservationId: string;
  purchaseContextUrl: string;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private serializer: UrlSerializer,
    private purchaseContextService: PurchaseContextService,
    private i18nService: I18nService) { }

  ngOnInit() {
    zip(this.route.data, this.route.params).subscribe(([data, params]) => {
      this.purchaseContextType = data.type;
      this.publicIdentifier = params[data.publicIdentifierParameter];
      this.reservationId = params['reservationId'];
      this.purchaseContextUrl = this.serializer.serialize(this.router.createUrlTree([this.purchaseContextType, this.publicIdentifier]));
      this.purchaseContextService.getContext(this.purchaseContextType, this.publicIdentifier).subscribe(ev => {
        this.i18nService.setPageTitle('reservation-page-not-found.header.title', ev);
      });
    });
  }
}

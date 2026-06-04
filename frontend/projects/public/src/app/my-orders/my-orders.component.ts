import { Component, type OnInit } from "@angular/core";
import type { IconProp } from "@fortawesome/fontawesome-svg-core";
import type { TranslateService } from "@ngx-translate/core";
import type { Language } from "../model/event";
import type {
  PurchaseContextWithReservation,
  ReservationHeader,
} from "../model/user";
import type { I18nService } from "../shared/i18n.service";
import { getLocalizedContent } from "../shared/subscription.service";
import type { UserService } from "../shared/user.service";

@Component({
  selector: "app-my-orders",
  templateUrl: "./my-orders.component.html",
})
export class MyOrdersComponent implements OnInit {
  orders: Array<PurchaseContextWithReservation> = [];
  languages: Language[];

  constructor(
    private userService: UserService,
    private translateService: TranslateService,
    private i18nService: I18nService,
  ) {}

  ngOnInit(): void {
    this.userService.getOrders().subscribe((array) => (this.orders = array));
    this.i18nService.getAvailableLanguages().subscribe((res) => {
      this.languages = res;
    });
    this.i18nService.setPageTitle("user.menu.my-orders", null);
  }

  localizedTitle(p: PurchaseContextWithReservation): string {
    const lang = this.translateService.currentLang;
    return getLocalizedContent(p.title, lang);
  }

  getStatusIcon(reservation: ReservationHeader): IconProp {
    if (reservation.status === "COMPLETE") {
      return ["fas", "check"];
    }
    return ["far", "clock"];
  }

  getTextClass(reservation: ReservationHeader): string {
    if (reservation.status === "COMPLETE") {
      return "text-success";
    }
    return "text-warning";
  }

  getStatusDescription(reservation: ReservationHeader): string {
    if (reservation.status === "COMPLETE") {
      return "my-orders.status.complete";
    }
    return "my-orders.status.pending";
  }

  getReservationCost(reservation: ReservationHeader): string {
    if (reservation.finalPrice > 0) {
      return reservation.currencyCode + " " + reservation.finalPrice;
    }
    return this.translateService.instant("common.free");
  }
}

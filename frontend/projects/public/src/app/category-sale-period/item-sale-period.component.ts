import { Component, Input } from "@angular/core";
import type { AdditionalService } from "../model/additional-service";
import type { TicketCategory } from "../model/ticket-category";

@Component({
  selector: "app-item-sale-period",
  templateUrl: "./item-sale-period.html",
})
export class ItemSalePeriodComponent {
  @Input()
  item: TicketCategory | AdditionalService;
  @Input()
  currentLang: string;
}

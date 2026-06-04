import { Component, Input } from '@angular/core';
import type { UntypedFormGroup } from '@angular/forms';
import type { AdditionalService } from '../model/additional-service';
import type { BasicEventInfo } from '../model/basic-event-info';
import type { TicketCategory } from '../model/ticket-category';

@Component({
    selector: 'app-item-card',
    templateUrl: './item-card.html',
    styleUrls: ['./item-card.scss'],
})
export class ItemCardComponent {
    @Input()
    parentFormGroup: UntypedFormGroup;

    @Input()
    item: TicketCategory | AdditionalService;

    @Input()
    event: BasicEventInfo;

    @Input()
    additionalClass = '';

    @Input()
    currentLang: string;
}

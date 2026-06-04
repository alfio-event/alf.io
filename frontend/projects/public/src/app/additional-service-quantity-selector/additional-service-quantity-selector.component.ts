import { Component, Input } from '@angular/core';
import type { UntypedFormGroup } from '@angular/forms';
import type { AdditionalService } from '../model/additional-service';
import type { Event } from '../model/event';

@Component({
    selector: 'app-additional-service-quantity-selector',
    templateUrl: './additional-service-quantity-selector.html',
})
export class AdditionalServiceQuantitySelectorComponent {
    @Input()
    additionalService: AdditionalService;

    @Input()
    validSelectionValues: number[];

    @Input()
    event: Event;

    @Input()
    parentGroup: UntypedFormGroup;
}

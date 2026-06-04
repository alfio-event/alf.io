import { Component, Input } from '@angular/core';
import type { Params } from '@angular/router';
import type { TranslateService } from '@ngx-translate/core';
import type { SubscriptionOwner } from '../model/reservation-info';
import type { BasicSubscriptionInfo } from '../model/subscription';
import type { AdditionalField } from '../model/ticket';
import { getLocalizedContent } from '../shared/subscription.service';

@Component({
    selector: 'app-basic-subscription-info',
    templateUrl: './basic-subscription-info.component.html',
    styleUrls: ['./basic-subscription-info.component.scss'],
})
export class BasicSubscriptionInfoComponent {
    @Input()
    subscription: BasicSubscriptionInfo;
    @Input()
    owner?: SubscriptionOwner;
    @Input()
    fieldConfigurationBeforeStandard: AdditionalField[] = [];
    @Input()
    fieldConfigurationAfterStandard: AdditionalField[] = [];
    @Input()
    params: Params;

    @Input()
    cardLayout = true;

    constructor(private translateService: TranslateService) {}

    get title(): string {
        return getLocalizedContent(
            this.subscription.title,
            this.translateService.currentLang,
        );
    }

    get description(): string {
        return getLocalizedContent(
            this.subscription.description,
            this.translateService.currentLang,
        );
    }
}

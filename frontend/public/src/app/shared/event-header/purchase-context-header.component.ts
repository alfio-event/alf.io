import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {I18nService} from '../i18n.service';
import {removeDOMNode} from '../event.service';
import {PurchaseContext} from '../../model/purchase-context';
import {Event} from '../../model/event';
import {PurchaseContextType} from '../purchase-context.service';
import {TranslateService} from '@ngx-translate/core';

@Component({
  selector: 'app-purchase-context-header',
  templateUrl: './purchase-context-header.component.html',
  styleUrls: ['./purchase-context-header.component.scss']
})
export class PurchaseContextHeaderComponent implements OnInit, OnDestroy {

  @Input()
  purchaseContext: PurchaseContext;

  @Input()
  type: PurchaseContextType;

  @Input()
  displayTopLoginButton = true;

  schemaElem: HTMLScriptElement;

  constructor(private i18nService: I18nService, private translateService: TranslateService) {
  }

  ngOnInit() {
    // https://developers.google.com/search/docs/data-types/event
    // https://search.google.com/test/rich-results?utm_campaign=devsite&utm_medium=jsonld&utm_source=event&id=tqxuXf4XIb4xolzr7EiE2Q

    if (this.type === 'event') {

      const ev = this.purchaseContext as Event;
      const start = new Date(ev.datesWithOffset.startDateTime);
      const end = new Date(ev.datesWithOffset.endDateTime);
      const descriptionHolder = document.createElement('div');
      descriptionHolder.innerHTML = ev.description[this.i18nService.getCurrentLang()];

      const jsonSchema = {
        '@context': 'https://schema.org',
        '@type': 'Event',
        'name' : ev.title[this.i18nService.getCurrentLang()],
        'startDate': start.toISOString(),
        'endDate': end.toISOString(),
        'description': descriptionHolder.innerText.trim(),
        'location': {
          '@type': 'Place',
          'address': ev.location
        },
        'organizer': {
          '@type': 'Organization',
          'name': ev.organizationName,
          'email': ev.organizationEmail
        },
        'image': '/file/' + ev.fileBlobId
      };

      this.schemaElem = document.createElement('script');
      this.schemaElem.text = JSON.stringify(jsonSchema);
      this.schemaElem.type = 'application/ld+json';
      document.head.appendChild(this.schemaElem);
    }
  }

  get title(): string {
    return this.purchaseContext.title[this.translateService.currentLang];
  }

  get isEvent(): boolean {
    return this.type === 'event';
  }

  ngOnDestroy() {
    if (this.type === 'event') {
      removeDOMNode(this.schemaElem);
    }
  }
}

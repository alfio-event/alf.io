import {Component, Input} from '@angular/core';
import {TermsPrivacyLinksContainer} from '../model/event';

@Component({
    selector: 'app-footer-links',
    templateUrl: './footer-links.component.html'
})
export class FooterLinksComponent {
    @Input()
    marginClass = 'mb-5';

    @Input()
    linksContainer?: TermsPrivacyLinksContainer;
}

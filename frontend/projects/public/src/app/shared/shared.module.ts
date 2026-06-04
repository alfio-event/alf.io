import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbDropdownModule, NgbToastModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { ClipboardCopyDirective } from './clipboard-copy/clipboard-copy.directive';
import { PurchaseContextHeaderComponent } from './event-header/purchase-context-header.component';
import { FeedbackComponent } from './feedback/feedback.component';
import { InvalidFeedbackDirective } from './invalid-feedback.directive';
import { LanguageSelectorComponent } from './language-selector/language-selector.component';
import { TopBarComponent } from './topbar/top-bar.component';
import { WarningModalComponent } from './warning-modal/warning-modal.component';

@NgModule({
    declarations: [
        LanguageSelectorComponent,
        PurchaseContextHeaderComponent,
        InvalidFeedbackDirective,
        ClipboardCopyDirective,
        FeedbackComponent,
        WarningModalComponent,
        TopBarComponent,
    ],
    imports: [
        CommonModule,
        TranslateModule.forChild(),
        FontAwesomeModule,
        NgbDropdownModule,
        NgbToastModule,
    ],
    exports: [
        LanguageSelectorComponent,
        PurchaseContextHeaderComponent,
        InvalidFeedbackDirective,
        ClipboardCopyDirective,
        FeedbackComponent,
        WarningModalComponent,
        TopBarComponent,
    ],
})
export class SharedModule {}

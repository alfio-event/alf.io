import {Pipe, PipeTransform} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {getLocalizedContent} from './subscription.service';

@Pipe({
  name: 'translateDescription'
})
export class TranslateDescriptionPipe implements PipeTransform {

  constructor(private translate: TranslateService) {}

  transform(value?: {[k: string]: any}): any {
    if (value != null) {
      const lang = this.translate.currentLang;
      return getLocalizedContent(value, lang);
    }
    return null;
  }
}

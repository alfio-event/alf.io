import {Injectable} from '@angular/core';
import {FormBuilder, FormGroup} from '@angular/forms';
import {AdditionalField} from '../model/ticket';
import {UserAdditionalData} from '../model/user';

@Injectable({
  providedIn: 'root'
})
export class AdditionalFieldService {
  constructor(private formBuilder: FormBuilder) {}

  private static getUserDataLabelValue(name: string, index: number, userLanguage?: string, additionalData?: UserAdditionalData): {l: string, v: string} | null {
    if (additionalData != null && additionalData[name] && additionalData[name].values.length > index) {
      const val = additionalData[name];
      return { l: val.label[userLanguage] || val.label[0], v: val.values[index] };
    }
    return { l: null, v: null };
  }

  public buildAdditionalFields(before: AdditionalField[], after: AdditionalField[], userLanguage: string, userData?: UserAdditionalData): FormGroup {
    const additional = {};
    if (before) {
      this.buildSingleAdditionalField(before, additional, userLanguage, userData);
    }
    if (after) {
      this.buildSingleAdditionalField(after, additional, userLanguage, userData);
    }
    return this.formBuilder.group(additional);
  }

  private buildSingleAdditionalField(a: AdditionalField[], additional: {}, userLanguage: string, userData?: UserAdditionalData): void {
    a.forEach(f => {
      const arr = [];

      if (f.type === 'checkbox') { // pre-fill with empty values for the checkbox cases, as we can have multiple values!
        for (let i = 0; i < f.restrictedValues.length; i++) {
          arr.push(this.formBuilder.control(null));
        }
      }

      f.fields.forEach(field => {
        arr[field.fieldIndex] = this.formBuilder.control(field.fieldValue || AdditionalFieldService.getUserDataLabelValue(f.name, field.fieldIndex, userLanguage, userData).v);
      });
      additional[f.name] = this.formBuilder.array(arr);
    });
  }
}

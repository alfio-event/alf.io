import {Directive, ElementRef, Input, OnDestroy, OnInit, Optional} from '@angular/core';
import {AbstractControl, FormControlName, UntypedFormGroup, ValidationErrors} from '@angular/forms';
import {TranslateService} from '@ngx-translate/core';
import {Subscription} from 'rxjs';
import {ErrorDescriptor} from '../model/validated-response';
import {removeDOMNode} from './event.service';

@Directive({
  selector: '[appInvalidFeedback]'
})
export class InvalidFeedbackDirective implements OnInit, OnDestroy {

  private subs: Subscription[] = [];

  @Input()
  invalidFeedbackInLabel: boolean;

  @Input()
  invalidFeedbackFieldName: string;

  @Input()
  invalidFeedbackForm: UntypedFormGroup;

  private targetControl: AbstractControl;

  constructor(private element: ElementRef, @Optional() private control: FormControlName, private translation: TranslateService) {
  }

  ngOnInit(): void {

    if (this.control == null && this.invalidFeedbackForm != null && this.invalidFeedbackFieldName != null) {
      this.targetControl = this.invalidFeedbackForm.get(this.invalidFeedbackFieldName);
      this.targetControl.statusChanges.subscribe(e => {
        this.checkValidation();
      });
    }

    if (this.control) {
      this.control.statusChanges.subscribe(e => {
        this.checkValidation();
      });
    }
  }

  private clearSubs(): void {
    this.subs.forEach(s => {
      if (s) {
        s.unsubscribe();
      }
    });
    this.subs = [];
  }

  ngOnDestroy(): void {
    this.clearSubs();
  }

  private get errors(): ValidationErrors {
    return this.targetControl ? this.targetControl.errors : this.control.errors;
  }

  private checkValidation(): void {

    let errorContainerElement: HTMLElement;

    if (this.invalidFeedbackInLabel) {
      errorContainerElement = this.element.nativeElement.parentElement.nextElementSibling;
    } else {
      errorContainerElement = this.element.nativeElement.nextElementSibling;
    }

    this.clearSubs();
    if (this.errors && this.errors.serverError && this.errors.serverError.length > 0) {
      this.element.nativeElement.classList.add('is-invalid');
      if (isInvalidFeedbackContainer(errorContainerElement)) {
        // remove messages that are already presents
        const rangeObj = new Range();
        rangeObj.selectNodeContents(errorContainerElement);
        rangeObj.deleteContents();
        this.addErrorMessages(errorContainerElement);
        //
      } else {
        const container = document.createElement('div');
        container.classList.add('invalid-feedback');
        this.addErrorMessages(container);
        if (this.invalidFeedbackInLabel) {
          container.classList.add('force-display');
          this.element.nativeElement.parentNode.insertAdjacentElement('afterEnd', container);
        } else {
          this.element.nativeElement.insertAdjacentElement('afterEnd', container);
        }
      }
    } else {
      this.element.nativeElement.classList.remove('is-invalid');
      if (isInvalidFeedbackContainer(errorContainerElement)) {
        removeDOMNode(errorContainerElement);
      }
    }
  }

  private addErrorMessages(container: HTMLElement): void {
    this.errors.serverError.forEach((e: ErrorDescriptor) => {
      const msg = document.createElement('div');
      this.subs.push(this.translation.stream(e.code, e.arguments).subscribe(text => {
        msg.textContent = text;
      }));
      container.appendChild(msg);
    });
  }

}

function isInvalidFeedbackContainer(container: HTMLElement): boolean {
  return container && container.classList.contains('invalid-feedback');
}



// <div class="invalid-feedback" *ngIf="contactAndTicketsForm.get('firstName').errors?.serverError">
//   <div *ngFor="let err of contactAndTicketsForm.get('firstName').errors.serverError" [translate]="err"></div>
// </div>


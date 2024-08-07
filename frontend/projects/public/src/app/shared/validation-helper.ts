import {AbstractControl} from '@angular/forms';
import {ErrorDescriptor, ValidatedResponse} from '../model/validated-response';
import {HttpErrorResponse} from '@angular/common/http';
import {ChallengeService} from "../challenge/challenge.service";

function applyValidationErrors(form: AbstractControl,
                               response: ValidatedResponse<any>,
                               keyNotFoundHandler?: (key: string) => string): ErrorDescriptor[] {

    if (response.errorCount === 0) {
        return [];
    }

    const globalErrors: ErrorDescriptor[] = [];

    response.validationErrors.forEach(err => {

        // form.get('tickets[207f224c-1df6-4994-9cb2-fa12eb36882d].email') -> not ok
        // form.get('tickets.207f224c-1df6-4994-9cb2-fa12eb36882d.email')  -> ok
        const transformedFieldName = err.fieldName.replace(/\[/g, '.').replace(/]/g, '');

        let formControl = form.get(transformedFieldName);

        if (!formControl && keyNotFoundHandler) {
          const backupKey = keyNotFoundHandler(transformedFieldName);
          formControl = form.get(backupKey);
        }

        if (formControl) {
            const formControlErr = formControl.getError('serverError');
            if (formControlErr) {
                const errors = (formControlErr as ErrorDescriptor[]);
                if (!containsWithKey(errors, err.code)) {
                    errors.push(err);
                }
            } else {
                formControl.setErrors({serverError: [err]});
            }
            formControl.markAsTouched();
        } else {
            globalErrors.push(err);
        }
    });

    // TODO: find better way -> this focus and scroll on the first invalid form input
    setTimeout(() => {
        // meh, should find a better way
        const found = document.querySelectorAll('[appinvalidfeedback].ng-invalid, [appinvalidfeedback].is-invalid');
        if (found && found.length > 0) {
            const elem = found[0] as HTMLElement;
            window.scroll({
                behavior: 'smooth',
                left: 0,
                top: window.scrollY + elem.getBoundingClientRect().top - 100
            });
            elem.focus({ preventScroll: true });
        }
    }, 10);
    return globalErrors;
}

function containsWithKey(errors: ErrorDescriptor[], key: string) {
    for (let i = 0; i < errors.length; i++) {
        if (errors[i].code === key) {
            return true;
        }
    }
    return false;
}

export function isServerError(err: any): boolean {
    return err instanceof HttpErrorResponse && err.status > 499;
}

export function handleServerSideValidationError(err: any,
                                                form: AbstractControl,
                                                keyNotFoundHandler?: (key: string) => string): ErrorDescriptor[] {
  if (isServerError(err)) {
      return [{
          code: 'error.STEP_2_PAYMENT_REQUEST_CREATION',
          fieldName: '',
          arguments: []
      }];
  }
  if (ChallengeService.isChallengeError(err)) {
      return [{
          code: 'error.STEP_2_CAPTCHA_VALIDATION_FAILED',
          fieldName: '',
          arguments: []
      }];
  }
  const errorObject = getErrorObject(err);
  if (errorObject != null) {
    return applyValidationErrors(form, errorObject, keyNotFoundHandler);
  }
  return [];
}

export function getErrorObject(err: any): ValidatedResponse<any> | null {
  if (err instanceof ValidatedResponse) {
    return err as ValidatedResponse<any>;
  } else if (err instanceof HttpErrorResponse && err.status === 422) {
    return err.error;
  }
  return null;
}

import {Component, OnInit} from '@angular/core';
import {UserService} from '../shared/user.service';
import {User, UserAdditionalData} from '../model/user';
import {TranslateService} from '@ngx-translate/core';
import {I18nService} from '../shared/i18n.service';
import {InvoicingConfiguration, Language} from '../model/event';
import {zip} from 'rxjs';
import {UntypedFormBuilder, UntypedFormGroup, Validators} from '@angular/forms';
import {BookingComponent} from '../reservation/booking/booking.component';
import {handleServerSideValidationError} from '../shared/validation-helper';
import {ErrorDescriptor} from '../model/validated-response';
import {InfoService} from '../shared/info.service';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {MyProfileDeleteWarningComponent} from './my-profile-delete-warning.component';
import {FeedbackService} from '../shared/feedback/feedback.service';
import {DELETE_ACCOUNT_CONFIRMATION, writeToSessionStorage} from '../shared/util';

@Component({
  selector: 'app-my-profile',
  templateUrl: './my-profile.component.html',
  styleUrls: ['./my-profile.component.scss']
})
export class MyProfileComponent implements OnInit {

  user?: User;
  languages: Language[];
  userForm?: UntypedFormGroup;
  invoicingConfiguration?: InvoicingConfiguration;
  globalErrors: ErrorDescriptor[];
  additionalData?: UserAdditionalData;

  constructor(private userService: UserService,
              private translateService: TranslateService,
              private i18nService: I18nService,
              private formBuilder: UntypedFormBuilder,
              private infoService: InfoService,
              private modalService: NgbModal,
              private feedbackService: FeedbackService) {
    this.userForm = this.formBuilder.group({
      firstName: this.formBuilder.control(null, Validators.required),
      lastName: this.formBuilder.control(null, Validators.required),
      addCompanyBillingDetails: this.formBuilder.control(false),
      billingAddressCompany: this.formBuilder.control(null),
      billingAddressLine1: this.formBuilder.control(null),
      billingAddressLine2: this.formBuilder.control(null),
      billingAddressZip: this.formBuilder.control(null),
      billingAddressCity: this.formBuilder.control(null),
      billingAddressState: this.formBuilder.control(null),
      vatCountryCode: this.formBuilder.control(null),
      skipVatNr: this.formBuilder.control(false),
      vatNr: this.formBuilder.control(null),
      italyEInvoicingFiscalCode: this.formBuilder.control(null),
      italyEInvoicingReferenceType: this.formBuilder.control(null),
      italyEInvoicingReferenceAddresseeCode: this.formBuilder.control(null),
      italyEInvoicingReferencePEC: this.formBuilder.control(null),
      italyEInvoicingSplitPayment: this.formBuilder.control(null),
      additionalInfo: this.formBuilder.group({})
    });
  }

  ngOnInit(): void {
    zip(this.userService.getUserIdentity(), this.i18nService.getAvailableLanguages(), this.infoService.getInfo())
      .subscribe(([user, languages, info]) => {
        this.languages = languages;
        this.i18nService.setPageTitle('user.menu.my-profile', null);
        this.invoicingConfiguration = info.invoicingConfiguration;
        let values: {[p: string]: any} = {
          firstName: user.firstName,
          lastName: user.lastName
        };
        this.user = user;
        const userBillingDetails = user.profile?.billingDetails;
        if (userBillingDetails != null) {
          values = {
            ...values,
            billingAddressCompany: userBillingDetails.companyName,
            billingAddressLine1: userBillingDetails.addressLine1,
            billingAddressLine2: userBillingDetails.addressLine2,
            billingAddressZip: userBillingDetails.zip,
            billingAddressCity: userBillingDetails.city,
            billingAddressState: userBillingDetails.state,
            vatCountryCode: userBillingDetails.country,
            vatNr: userBillingDetails.taxId,
            italyEInvoicingFiscalCode: BookingComponent.optionalGet(userBillingDetails, (i) => i.fiscalCode),
            italyEInvoicingReferenceType: BookingComponent.optionalGet(userBillingDetails, (i) => i.referenceType),
            italyEInvoicingReferenceAddresseeCode: BookingComponent.optionalGet(userBillingDetails, (i) => i.addresseeCode),
            italyEInvoicingReferencePEC: BookingComponent.optionalGet(userBillingDetails, (i) => i.pec),
            italyEInvoicingSplitPayment: BookingComponent.optionalGet(userBillingDetails, (i) => i.splitPayment)
          };
        }
        const additionalData = user.profile?.additionalData;
        this.additionalData = additionalData;
        if (additionalData != null) {
          const additionalInfoGroup = this.userForm.get('additionalInfo') as UntypedFormGroup;
          for (const additionalOptionKey of Object.keys(additionalData)) {
            additionalInfoGroup.addControl(additionalOptionKey, this.formBuilder.control(additionalData[additionalOptionKey].values[0]));
          }
        }
        this.userForm.patchValue(values);
      });
  }


  save(): void {
    this.userService.updateUser(this.userForm.value)
      .subscribe(res => {
        if (res.success) {
          this.feedbackService.showSuccess(this.translateService.instant('my-profile.update.success'));
          this.user = res.value;
        } else {
          handleServerSideValidationError(res.validationErrors, this.userForm);
        }
      }, err => this.globalErrors = handleServerSideValidationError(err, this.userForm));
  }

  deleteProfile(): void {
    const modalRef = this.modalService.open(MyProfileDeleteWarningComponent, {centered: true, backdrop: 'static'});
    modalRef.result.then(() => {
      this.userService.deleteProfile().subscribe(response => {
        if (!response.empty) {
          writeToSessionStorage(DELETE_ACCOUNT_CONFIRMATION, 'y');
          window.location.href = response.targetUrl;
        }
      }, err => console.log('something went wrong', err));
    });
  }

  get hasAdditionalData(): boolean {
    return this.additionalData != null && Object.keys(this.additionalData).length > 0;
  }
}

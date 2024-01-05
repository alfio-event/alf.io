import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ReservationService} from '../../shared/reservation.service';
import {ActivatedRoute, Router} from '@angular/router';
import {FormArray, UntypedFormBuilder, UntypedFormGroup, Validators} from '@angular/forms';
import {TicketService} from '../../shared/ticket.service';
import {
  AdditionalServiceWithData,
  BillingDetails,
  ItalianEInvoicing,
  ReservationInfo,
  ReservationSubscriptionInfo,
  TicketsByTicketCategory
} from '../../model/reservation-info';
import {Observable, of, Subject, zip} from 'rxjs';
import {getErrorObject, handleServerSideValidationError} from '../../shared/validation-helper';
import {I18nService} from '../../shared/i18n.service';
import {MoveAdditionalServiceRequest, Ticket, TicketIdentifier} from '../../model/ticket';
import {TranslateService} from '@ngx-translate/core';
import {AnalyticsService} from '../../shared/analytics.service';
import {ErrorDescriptor} from '../../model/validated-response';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {ReservationExpiredComponent} from '../expired-notification/reservation-expired.component';
import {CancelReservationComponent} from '../cancel-reservation/cancel-reservation.component';
import {PurchaseContextService, PurchaseContextType} from '../../shared/purchase-context.service';
import {PurchaseContext} from '../../model/purchase-context';
import {WarningModalComponent} from '../../shared/warning-modal/warning-modal.component';
import {SearchParams} from '../../model/search-params';
import {UserService} from '../../shared/user.service';
import {ANONYMOUS, User} from '../../model/user';
import {first} from 'rxjs/operators';
import {FeedbackService} from '../../shared/feedback/feedback.service';
import {ReservationStatusChanged} from '../../model/embedding-configuration';
import {embedded} from '../../shared/util';
import {AdditionalFieldService} from '../../shared/additional-field.service';

@Component({
  selector: 'app-booking',
  templateUrl: './booking.component.html'
})
export class BookingComponent implements OnInit, AfterViewInit {

  reservationInfo: ReservationInfo;
  purchaseContext: PurchaseContext;
  contactAndTicketsForm: UntypedFormGroup;
  private publicIdentifier: string;
  reservationId: string;
  expired: boolean;
  globalErrors: ErrorDescriptor[];
  @ViewChild('invoiceAnchor')
  private invoiceElement: ElementRef<HTMLAnchorElement>;
  private doScroll = new Subject<boolean>();
  purchaseContextType: PurchaseContextType;

  ticketCounts: number;

  enableAttendeeAutocomplete: boolean;
  displayLoginSuggestion: boolean;

  private additionalServicesWithData: {[uuid: string]: AdditionalServiceWithData[]} = {};
  additionalServicesCount = 0;

  public static optionalGet<T>(billingDetails: BillingDetails, consumer: (b: ItalianEInvoicing) => T, userBillingDetails?: BillingDetails): T | null {
    const italianEInvoicing = billingDetails.invoicingAdditionalInfo.italianEInvoicing;
    if (italianEInvoicing != null) {
      return consumer(italianEInvoicing);
    }
    const userItalianEInvoicing = userBillingDetails?.invoicingAdditionalInfo?.italianEInvoicing;
    if (userItalianEInvoicing != null) {
      return consumer(userItalianEInvoicing);
    }
    return null;
  }

  private static isUUID(v: string): boolean {
    const r = /^[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}$/i;
    return v.match(r) !== null;
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private reservationService: ReservationService,
    private ticketService: TicketService,
    private purchaseContextService: PurchaseContextService,
    private formBuilder: UntypedFormBuilder,
    private i18nService: I18nService,
    private translate: TranslateService,
    private analytics: AnalyticsService,
    private modalService: NgbModal,
    private userService: UserService,
    private feedbackService: FeedbackService,
    private additionalFieldService: AdditionalFieldService) { }

  public ngOnInit(): void {
    zip(this.route.data, this.route.params, this.userService.authenticationStatus.pipe(first())).subscribe(([data, params, authStatus]) => {

      const user: User | undefined = authStatus.user;
      this.publicIdentifier = params[data.publicIdentifierParameter];
      this.reservationId = params['reservationId'];
      this.purchaseContextType = data.type;
      this.displayLoginSuggestion = authStatus.enabled && authStatus.user === ANONYMOUS;

      zip(
        this.purchaseContextService.getContext(this.purchaseContextType, this.publicIdentifier),
        this.reservationService.getReservationInfo(this.reservationId)
      ).subscribe(([purchaseContext, reservationInfo]) => {
        this.purchaseContext = purchaseContext;
        this.reservationInfo = reservationInfo;

        this.i18nService.setPageTitle('reservation-page.header.title', purchaseContext);

        const invoiceRequested = purchaseContext.invoicingConfiguration.onlyInvoice ? true : reservationInfo.invoiceRequested;

        //
        this.ticketCounts = 0;
        this.reservationInfo.ticketsByCategory.forEach(t => {
          this.ticketCounts += t.tickets.length;
        });


        // auto complete (copy by default first/lastname + email to ticket) is enabled only if we have only
        // one ticket
        if (this.ticketCounts === 1 && this.purchaseContext.assignmentConfiguration.enableAttendeeAutocomplete) {
          this.enableAttendeeAutocomplete = true;
        }
        //

        //

        const billingDetails = this.reservationInfo.billingDetails;
        const userBillingDetails = user?.profile?.billingDetails;

        const additionalServices = reservationInfo.additionalServiceWithData ?? [];
        this.contactAndTicketsForm = this.formBuilder.group({
          firstName: this.formBuilder.control(this.reservationInfo.firstName || user?.firstName, [Validators.required, Validators.maxLength(255)]),
          lastName: this.formBuilder.control(this.reservationInfo.lastName || user?.lastName, [Validators.required, Validators.maxLength(255)]),
          email: this.formBuilder.control(this.reservationInfo.email || user?.emailAddress, [Validators.required, Validators.maxLength(255)]),
          tickets: this.buildTicketsFormGroup(this.reservationInfo.ticketsByCategory, user),
          invoiceRequested: invoiceRequested,
          addCompanyBillingDetails: this.reservationInfo.addCompanyBillingDetails || userBillingDetails?.companyName != null,
          billingAddressCompany: billingDetails.companyName || userBillingDetails?.companyName,
          billingAddressLine1: billingDetails.addressLine1 || userBillingDetails?.addressLine1,
          billingAddressLine2: billingDetails.addressLine2 || userBillingDetails?.addressLine2,
          billingAddressZip: billingDetails.zip || userBillingDetails?.zip,
          billingAddressCity: billingDetails.city || userBillingDetails?.city,
          billingAddressState: billingDetails.state || userBillingDetails?.state,
          vatCountryCode: billingDetails.country || userBillingDetails?.country,
          customerReference: this.reservationInfo.customerReference,
          vatNr: billingDetails.taxId || userBillingDetails?.taxId,
          skipVatNr: this.reservationInfo.skipVatNr,
          italyEInvoicingFiscalCode: BookingComponent.optionalGet(billingDetails, (i) => i.fiscalCode, userBillingDetails),
          italyEInvoicingReferenceType: BookingComponent.optionalGet(billingDetails, (i) => i.referenceType, userBillingDetails),
          italyEInvoicingReferenceAddresseeCode: BookingComponent.optionalGet(billingDetails, (i) => i.addresseeCode, userBillingDetails),
          italyEInvoicingReferencePEC: BookingComponent.optionalGet(billingDetails, (i) => i.pec, userBillingDetails),
          italyEInvoicingSplitPayment: BookingComponent.optionalGet(billingDetails, (i) => i.splitPayment, userBillingDetails),
          postponeAssignment: false,
          differentSubscriptionOwner: this.isDifferentOwnerDefined(),
          subscriptionOwner: this.buildSubscriptionOwnerFormGroup(this.reservationInfo.subscriptionInfos, user),
          additionalServices: this.ticketService.buildAdditionalServicesFormGroup(additionalServices, this.i18nService.getCurrentLang())
        });

        additionalServices.forEach(asd => {
          if (this.additionalServicesWithData[asd.ticketUUID] != null) {
            this.additionalServicesWithData[asd.ticketUUID].push(asd);
          } else {
            this.additionalServicesWithData[asd.ticketUUID] = [asd];
          }
        });
        this.additionalServicesCount = additionalServices.length;

        setTimeout(() => this.doScroll.next(this.invoiceElement != null));

        this.analytics.pageView(purchaseContext.analyticsConfiguration);
      });
    });
  }

  ngAfterViewInit(): void {
    zip(this.route.queryParams, this.doScroll.asObservable())
      .subscribe(results => {
        const requestInvoice: boolean = !!results[0].requestInvoice;
        if (requestInvoice && results[1]) {
          this.contactAndTicketsForm.get('invoiceRequested').setValue(true);
          this.invoiceElement.nativeElement.scrollIntoView(true);
        }
      });
  }

  private buildSubscriptionOwnerFormGroup(subscriptionInfos: Array<ReservationSubscriptionInfo> | undefined, user?: User): UntypedFormGroup {
    if (subscriptionInfos != null) {
      const subscriptionInfo = subscriptionInfos[0];
      const email = (subscriptionInfo.owner?.email ?? (this.emailEditForbidden ? this.reservationInfo.email : null)) ?? user?.emailAddress;
      return this.formBuilder.group({
        firstName: subscriptionInfo.owner?.firstName ?? user?.firstName,
        lastName: subscriptionInfo.owner?.lastName ?? user?.lastName,
        email,
        additional: this.additionalFieldService.buildAdditionalFields(subscriptionInfo.fieldConfigurationBeforeStandard,
          subscriptionInfo.fieldConfigurationAfterStandard,
          this.translate.currentLang,
          user?.profile?.additionalData)
      });
    } else {
      return null;
    }
  }

  private buildTicketsFormGroup(ticketsByCategory: TicketsByTicketCategory[], user: User): UntypedFormGroup {
    const tickets = {};
    ticketsByCategory.forEach(t => {
      t.tickets.forEach((ticket, idx) => {
        tickets[ticket.uuid] = this.ticketService.buildFormGroupForTicket(ticket, idx === 0 ? user : undefined);
      });
    });
    return this.formBuilder.group(tickets);
  }

  private removeUnnecessaryFields(): void {
    // check invoice data, remove company data if private invoice has been chosen
    if (this.contactAndTicketsForm.get('invoiceRequested').value && !this.contactAndTicketsForm.get('addCompanyBillingDetails').value) {
      ['billingAddressCompany', 'vatNr', 'skipVatNr'].forEach(n => this.contactAndTicketsForm.get(n).setValue(null));
    }
  }

  submitForm(): void {
    this.removeUnnecessaryFields();
    this.validateToOverview(false);
  }

  private validateToOverview(ignoreWarnings: boolean): void {
    this.reservationService.validateToOverview(this.reservationId, this.contactAndTicketsForm.value, this.translate.currentLang, ignoreWarnings).subscribe(res => {
      if (res.success && (!res.warnings || res.warnings.length === 0 || ignoreWarnings)) {
        let o: Observable<unknown> = of(true);
        if (this.route.snapshot.queryParamMap.has('subscription') && BookingComponent.isUUID(this.route.snapshot.queryParamMap.get('subscription'))) {
          // try to apply the subscription
          const subscriptionCode = this.route.snapshot.queryParamMap.get('subscription');
          o = this.reservationService.applySubscriptionCode(this.reservationId, subscriptionCode, this.reservationInfo.email);
        }
        o.subscribe(
          _ => this.proceedToOverview(),
          // if there is an error, we proceed anyway
          () => this.proceedToOverview()
        );
      } else if (res.success) {
        // display warnings
        const modalRef = this.modalService.open(WarningModalComponent, {centered: true, backdrop: 'static'});
        const firstWarning = res.warnings[0];
        modalRef.componentInstance.message = firstWarning.code;
        const params: {[key: string]: string} = {};
        firstWarning.params.forEach((v, i) => params['' + i] = v);
        modalRef.componentInstance.parameters = params;
        modalRef.result.then(() => this.validateToOverview(true));
      }
    }, (err) => {
      this.globalErrors = handleServerSideValidationError(err, this.contactAndTicketsForm, key => {
        const additionalRegex = /tickets\.([a-f0-9-]+)\.additional\.([^.]+)\.(\d+)/;
        const result = additionalRegex.exec(key);
        if (result != null) {
          const uuid = result[1];
          const fieldName = result[2];
          const links = this.contactAndTicketsForm.value.additionalServices.links[uuid];
          const index = links.findIndex(link => link.fields[fieldName] != null);
          return `additionalServices.links.${uuid}.${index}.fields.${fieldName}.${result[3]}`;
        }
        return key;
      });
    });
  }

  private proceedToOverview(): Promise<boolean> {
    return this.router.navigate([this.purchaseContextType, this.publicIdentifier, 'reservation', this.reservationId, 'overview'], {
      queryParams: SearchParams.transformParams(this.route.snapshot.queryParams, this.route.snapshot.params)
    });
  }

  cancelPendingReservation() {
    this.modalService.open(CancelReservationComponent, {centered: true}).result.then(res => {
      if (res === 'yes') {
        this.reservationService.cancelPendingReservation(this.reservationId).subscribe(() => {
          this.handleCancelOrExpired();
        });
      }
    }, () => {});
  }

  handleExpired(expired: boolean): void {
    setTimeout(() => {
      if (!this.expired) {
        this.expired = expired;
        this.modalService.open(ReservationExpiredComponent, {centered: true, backdrop: 'static'})
            .result.then(() => this.handleCancelOrExpired());
      }
    });
  }

  private handleCancelOrExpired(): void {
    if (embedded && this.purchaseContext.embeddingConfiguration.enabled) {
      window.parent.postMessage(
        new ReservationStatusChanged('CANCELLED', this.reservationId),
        this.purchaseContext.embeddingConfiguration.notificationOrigin
      );
    } else {
      this.router.navigate([this.purchaseContextType, this.publicIdentifier], {replaceUrl: true});
    }
  }

  handleInvoiceRequestedChange() {
    // set addCompanyBillingDetails to false if it's null
    if (this.contactAndTicketsForm.value.addCompanyBillingDetails === null) {
      this.contactAndTicketsForm.get('addCompanyBillingDetails').setValue(false);
    }
  }

  handleAutocomplete(fieldName: string, value: string) {
    if (this.enableAttendeeAutocomplete) {
      const ticketUUID = Object.keys(this.contactAndTicketsForm.get('tickets').value)[0];
      const targetControl = this.contactAndTicketsForm.get(`tickets.${ticketUUID}.${fieldName}`);
      if (targetControl.pristine && (targetControl.value == null || targetControl.value === '')) {
        targetControl.setValue(value);
      }
    }
  }

  getTicketForm(ticket: Ticket): UntypedFormGroup {
    return this.contactAndTicketsForm.get('tickets.' + ticket.uuid) as UntypedFormGroup;
  }

  getSubscriptionForm(): UntypedFormGroup {
    return this.contactAndTicketsForm.get('subscriptionOwner') as UntypedFormGroup;
  }

  copyContactInfoTo(ticket: Ticket) {
    ['firstName', 'lastName', 'email'].forEach(field => {
      const val = this.contactAndTicketsForm.get(field).value;
      this.contactAndTicketsForm.get(`tickets.${ticket.uuid}.${field}`).setValue(val);
    });
  }

  login(): void {
    // save reservation status
    const redirectToLogin = () => {
      window.location.href = `/openid/authentication?reservation=${this.reservationId}&contextType=${this.purchaseContextType}&id=${this.publicIdentifier}`;
    };
    this.reservationService.validateToOverview(this.reservationId, this.contactAndTicketsForm.value, this.translate.currentLang, false)
      .subscribe(() => {
        // reservation is now saved. We can proceed to login
        redirectToLogin();
      }, error => {
        const errorObj = getErrorObject(error);
        if (errorObj != null) {
          // reservation is not in a valid state. Proceed anyway
          redirectToLogin();
        } else {
          this.feedbackService.showError(this.translate.instant('reservation-page.cannot-login.error'));
        }
      });
  }

  get showContactData(): boolean {
    return !embedded || !this.reservationInfo.metadata.hideContactData;
  }

  get emailEditForbidden(): boolean {
    return this.reservationInfo.metadata.lockEmailEdit;
  }

  getAdditionalDataForm(ticket: Ticket): FormArray | null {
    const linksGroup = <UntypedFormGroup>(<UntypedFormGroup>this.contactAndTicketsForm.get('additionalServices'));
    return linksGroup.contains(ticket.uuid) ? <FormArray>linksGroup.get(ticket.uuid) : null;
  }

  getAdditionalData(ticket: Ticket): AdditionalServiceWithData[] {
    return this.additionalServicesWithData[ticket.uuid] ?? [];
  }

  getOtherTickets(ticket: Ticket): TicketIdentifier[] | null {
    if (this.ticketCounts === 1) {
      return null;
    }
    const result: TicketIdentifier[] = [];
    let index = 1;
    this.reservationInfo.ticketsByCategory.forEach(twc => {
      twc.tickets.forEach(t => {
        if (t.uuid !== ticket.uuid) {
          result.push({
            index,
            firstName: t.firstName,
            lastName: t.lastName,
            uuid: t.uuid,
            categoryName: twc.name
          });
        }
        index++;
      });
    });
    return result;
  }

  get subscriptionInfo(): ReservationSubscriptionInfo {
    return this.reservationInfo.subscriptionInfos[0];
  }

  get subscriptionAdditionalForm(): UntypedFormGroup {
    return this.contactAndTicketsForm.get('subscriptionOwner').get('additional') as UntypedFormGroup;
  }

  moveAdditionalService($event: MoveAdditionalServiceRequest) {
    const element = Object.assign({}, this.additionalServicesWithData[$event.currentTicketUuid][$event.index], {ticketUUID: $event.newTicketUuid});
    const additionalFormGroup = (this.contactAndTicketsForm.get('additionalServices') as UntypedFormGroup);
    const services = (additionalFormGroup.get($event.currentTicketUuid) as FormArray);
    if (services.length === 1) {
      // if there is only one service, we remove the complete item
      additionalFormGroup.removeControl($event.currentTicketUuid);
    } else {
      services.removeAt($event.index);
    }

    if (additionalFormGroup.get($event.newTicketUuid) == null) {
      additionalFormGroup.addControl($event.newTicketUuid, this.formBuilder.array([this.ticketService.buildAdditionalServiceGroup(element, this.i18nService.getCurrentLang())]));
    } else {
      const formArray = (<FormArray>additionalFormGroup.get($event.newTicketUuid));
      formArray.push(this.ticketService.buildAdditionalServiceGroup(element, this.i18nService.getCurrentLang()));
      additionalFormGroup.setControl($event.newTicketUuid, formArray);
    }

    this.additionalServicesWithData[$event.currentTicketUuid] = this.additionalServicesWithData[$event.currentTicketUuid]
      .filter(a => a.itemId !== $event.itemId);
    if (this.additionalServicesWithData[$event.newTicketUuid] == null) {
      this.additionalServicesWithData[$event.newTicketUuid] = [];
    }
    const currentArray = this.additionalServicesWithData[$event.newTicketUuid];
    currentArray.push(element);
    this.additionalServicesWithData[$event.newTicketUuid] = currentArray;
  }

  private isDifferentOwnerDefined(): boolean {
    return this.reservationInfo.subscriptionInfos?.findIndex(s => s.owner != null
      && (s.owner.firstName != null || s.owner.lastName != null)) > -1;
  }
}

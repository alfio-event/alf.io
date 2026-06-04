import {
  HTTP_INTERCEPTORS,
  HttpClient,
  HttpClientModule,
  HttpClientXsrfModule,
  HttpXsrfTokenExtractor,
} from "@angular/common/http";
import { APP_INITIALIZER, NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { BrowserModule } from "@angular/platform-browser";
import {
  type FaIconLibrary,
  FontAwesomeModule,
} from "@fortawesome/angular-fontawesome";
import {
  faApplePay,
  faIdeal,
  faPaypal,
  faStripe,
} from "@fortawesome/free-brands-svg-icons";
import {
  faBuilding,
  faCalendarAlt,
  faCalendarPlus,
  faCheckCircle,
  faClock,
  faClone,
  faCompass,
  faCopy,
  faEdit,
  faEnvelope,
  faHandshake,
} from "@fortawesome/free-regular-svg-icons";
import {
  faAddressCard,
  faAngleDown,
  faAngleUp,
  faCheck,
  faCircle,
  faCircleNotch,
  faCog,
  faCreditCard,
  faDownload,
  faEraser,
  faExchangeAlt,
  faExclamationCircle,
  faExclamationTriangle,
  faExternalLinkAlt,
  faFileAlt,
  faFileInvoice,
  faFilePdf,
  faGift,
  faGlobe,
  faInfoCircle,
  faMapMarkerAlt,
  faMoneyBill,
  faMoneyCheckAlt,
  faRedoAlt,
  faSearchPlus,
  faSignInAlt,
  faSignOutAlt,
  faThumbsUp,
  faTicketAlt,
  faTimes,
  faTrash,
  faUserAstronaut,
  faWifi,
} from "@fortawesome/free-solid-svg-icons";
import {
  NgbDropdownModule,
  NgbModalModule,
  NgbTooltipModule,
} from "@ng-bootstrap/ng-bootstrap";
import { NgSelectModule } from "@ng-select/ng-select";
import { TranslateLoader, TranslateModule } from "@ngx-translate/core";
import { CustomOfflinePaymentProxyComponent } from "projects/public/src/app/payment/custom-offline-payment-proxy/custom-offline-payment-proxy.component";
import { AdditionalFieldComponent } from "./additional-field/additional-field.component";
import { AdditionalServiceComponent } from "./additional-service/additional-service.component";
import { AdditionalServiceFormComponent } from "./additional-service-form/additional-service-form.component";
import { AdditionalServiceQuantitySelectorComponent } from "./additional-service-quantity-selector/additional-service-quantity-selector.component";
import { AppComponent } from "./app.component";
import { AppRoutingModule } from "./app-routing.module";
import { BannerCheckComponent } from "./banner-check/banner-check.component";
import { BasicEventInfoComponent } from "./basic-event-info/basic-event-info.component";
import { BasicSubscriptionInfoComponent } from "./basic-subscription-info/basic-subscription-info.component";
import { ItemSalePeriodComponent } from "./category-sale-period/item-sale-period.component";
import { ChallengeComponent } from "./challenge/challenge.component";
import { ChallengeCodeInterceptor } from "./challenge/challenge.interceptor";
import { TurnstileChallengeComponent } from "./challenge/turnstile/turnstile-challenge.component";
import { CountdownComponent } from "./countdown/countdown.component";
import { EventDatesComponent } from "./event-dates/event-dates.component";
import { EventDisplayComponent } from "./event-display/event-display.component";
import { FooterLinksComponent } from "./event-footer-links/footer-links.component";
import { EventListAllComponent } from "./event-list-all/event-list-all.component";
import { EventSummaryComponent } from "./event-summary/event-summary.component";
import { HeaderInformationRetriever } from "./header-information-retriever";
import { HomeComponent } from "./home/home.component";
import { ItemCardComponent } from "./item-card/item-card.component";
import { MyOrdersComponent } from "./my-orders/my-orders.component";
import { MyProfileComponent } from "./my-profile/my-profile.component";
import { MyProfileDeleteWarningComponent } from "./my-profile/my-profile-delete-warning.component";
import { MolliePaymentProxyComponent } from "./payment/mollie-payment-proxy/mollie-payment-proxy.component";
import { OfflinePaymentProxyComponent } from "./payment/offline-payment-proxy/offline-payment-proxy.component";
import { OnsitePaymentProxyComponent } from "./payment/onsite-payment-proxy/onsite-payment-proxy.component";
import { PaypalPaymentProxyComponent } from "./payment/paypal-payment-proxy/paypal-payment-proxy.component";
import { SaferpayPaymentProxyComponent } from "./payment/saferpay-payment-proxy/saferpay-payment-proxy.component";
import { StripePaymentProxyComponent } from "./payment/stripe-payment-proxy/stripe-payment-proxy.component";
import { PriceTagComponent } from "./price-tag/price-tag.component";
import { PurchaseContextContainerComponent } from "./purchase-context-container/purchase-context-container.component";
import { RecaptchaComponent } from "./recaptcha/recaptcha.component";
import { AnimatedDotsComponent } from "./reservation/animated-dots/animated-dots.component";
import { BookingComponent } from "./reservation/booking/booking.component";
import { CancelReservationComponent } from "./reservation/cancel-reservation/cancel-reservation.component";
import { CustomOfflinePaymentComponent } from "./reservation/custom-offline-payment/custom-offline-payment.component";
import { DeferredOfflinePaymentComponent } from "./reservation/deferred-offline-payment/deferred-offline-payment.component";
import { DownloadTicketComponent } from "./reservation/download-ticket/download-ticket.component";
import { ErrorComponent } from "./reservation/error/error.component";
import { ReservationExpiredComponent } from "./reservation/expired-notification/reservation-expired.component";
import { InvoiceFormComponent } from "./reservation/invoice-form/invoice-form.component";
import { ModalRemoveSubscriptionComponent } from "./reservation/modal-remove-subscription/modal-remove-subscription.component";
import { NotFoundComponent } from "./reservation/not-found/not-found.component";
import { OfflinePaymentComponent } from "./reservation/offline-payment/offline-payment.component";
import { OverviewComponent } from "./reservation/overview/overview.component";
import { PaymentMethodSelectorComponent } from "./reservation/payment-method-selector/payment-method-selector.component";
import { ProcessingPaymentComponent } from "./reservation/processing-payment/processing-payment.component";
import { ReleaseTicketComponent } from "./reservation/release-ticket/release-ticket.component";
import { ReservationComponent } from "./reservation/reservation.component";
import { SuccessComponent } from "./reservation/success/success.component";
import { SuccessSubscriptionComponent } from "./reservation/success-subscription/success-subscription.component";
import { SummaryTableComponent } from "./reservation/summary-table/summary-table.component";
import { TicketFormComponent } from "./reservation/ticket-form/ticket-form.component";
import { CustomLoader } from "./shared/i18n.service";
import { SharedModule } from "./shared/shared.module";
import { TranslateDescriptionPipe } from "./shared/translate-description.pipe";
import { UserService } from "./shared/user.service";
import { StepperComponent } from "./stepper/stepper.component";
import { SubscriptionDisplayComponent } from "./subscription-display/subscription-display.component";
import { SubscriptionListAllComponent } from "./subscription-list-all/subscription-list-all.component";
import { SubscriptionSummaryComponent } from "./subscription-summary/subscription-summary.component";
import { TicketQuantitySelectorComponent } from "./ticket-quantity-selector/ticket-quantity-selector.component";
import { UpdateTicketComponent } from "./update-ticket/update-ticket.component";
import { ViewTicketComponent } from "./view-ticket/view-ticket.component";
import { WaitingRoomComponent } from "./waiting-room/waiting-room.component";
import {
  AuthTokenInterceptor,
  DOMGidExtractor,
  DOMXsrfTokenExtractor,
} from "./xsrf";

// AoT requires an exported function for factories
export function HttpLoaderFactory(http: HttpClient) {
  return new CustomLoader(http);
}

export function InitUserService(
  userService: UserService,
): () => Promise<boolean> {
  return () => userService.initAuthenticationStatus();
}

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    EventDisplayComponent,
    BookingComponent,
    OverviewComponent,
    SuccessComponent,
    ReservationComponent,
    StepperComponent,
    AdditionalFieldComponent,
    ViewTicketComponent,
    UpdateTicketComponent,
    EventSummaryComponent,
    TicketFormComponent,
    DownloadTicketComponent,
    CountdownComponent,
    BannerCheckComponent,
    OfflinePaymentComponent,
    CustomOfflinePaymentComponent,
    OfflinePaymentProxyComponent,
    OnsitePaymentProxyComponent,
    PaypalPaymentProxyComponent,
    StripePaymentProxyComponent,
    SaferpayPaymentProxyComponent,
    CustomOfflinePaymentProxyComponent,
    ProcessingPaymentComponent,
    SummaryTableComponent,
    InvoiceFormComponent,
    AdditionalServiceComponent,
    RecaptchaComponent,
    PriceTagComponent,
    NotFoundComponent,
    TicketQuantitySelectorComponent,
    ItemSalePeriodComponent,
    ItemCardComponent,
    AdditionalServiceQuantitySelectorComponent,
    ReservationExpiredComponent,
    ReleaseTicketComponent,
    CancelReservationComponent,
    FooterLinksComponent,
    ErrorComponent,
    DeferredOfflinePaymentComponent,
    MolliePaymentProxyComponent,
    PaymentMethodSelectorComponent,
    AnimatedDotsComponent,
    EventDatesComponent,
    EventListAllComponent,
    BasicEventInfoComponent,
    BasicSubscriptionInfoComponent,
    SubscriptionListAllComponent,
    SubscriptionDisplayComponent,
    SuccessSubscriptionComponent,
    SubscriptionSummaryComponent,
    PurchaseContextContainerComponent,
    ModalRemoveSubscriptionComponent,
    MyOrdersComponent,
    MyProfileComponent,
    WaitingRoomComponent,
    MyProfileDeleteWarningComponent,
    TranslateDescriptionPipe,
    AdditionalServiceFormComponent,
    ChallengeComponent,
    TurnstileChallengeComponent,
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    HttpClientXsrfModule.withOptions({
      cookieName: "XSRF-TOKEN",
      headerName: "X-CSRF-TOKEN",
    }),
    FontAwesomeModule,
    FormsModule,
    ReactiveFormsModule,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient],
      },
    }),
    NgbTooltipModule,
    NgSelectModule,
    NgbModalModule,
    NgbDropdownModule,
    SharedModule,
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: InitUserService,
      deps: [UserService],
      multi: true,
    },
    { provide: HTTP_INTERCEPTORS, useClass: AuthTokenInterceptor, multi: true },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: HeaderInformationRetriever,
      multi: true,
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: ChallengeCodeInterceptor,
      multi: true,
    },
    { provide: HttpXsrfTokenExtractor, useClass: DOMXsrfTokenExtractor },
    DOMGidExtractor,
    DOMXsrfTokenExtractor,
    AuthTokenInterceptor,
  ],
  bootstrap: [AppComponent],
})
export class AppModule {
  constructor(library: FaIconLibrary) {
    library.addIcons(
      faInfoCircle,
      faGift,
      faTicketAlt,
      faCheck,
      faAddressCard,
      faFileAlt,
      faThumbsUp,
      faMoneyBill,
      faDownload,
      faSearchPlus,
      faExchangeAlt,
      faExclamationTriangle,
      faCreditCard,
      faCog,
      faEraser,
      faTimes,
      faFileInvoice,
      faGlobe,
      faAngleDown,
      faAngleUp,
      faCircle,
      faCheckCircle,
      faMoneyCheckAlt,
      faWifi,
      faTrash,
      faCopy,
      faExclamationCircle,
      faUserAstronaut,
      faSignInAlt,
      faSignOutAlt,
      faExternalLinkAlt,
      faMapMarkerAlt,
      faRedoAlt,
      faCircleNotch,
    );
    library.addIcons(
      faCalendarAlt,
      faCalendarPlus,
      faCompass,
      faClock,
      faEnvelope,
      faEdit,
      faClone,
      faHandshake,
      faBuilding,
    );
    library.addIcons(faPaypal, faStripe, faIdeal, faApplePay, faFilePdf);
  }
}

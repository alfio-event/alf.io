import {inject, NgModule} from '@angular/core';
import {CanActivateFn, RouterModule, Routes} from '@angular/router';
import {HomeComponent} from './home/home.component';
import {EventDisplayComponent} from './event-display/event-display.component';
import {BookingComponent} from './reservation/booking/booking.component';
import {OverviewComponent} from './reservation/overview/overview.component';
import {SuccessComponent} from './reservation/success/success.component';
import {OfflinePaymentComponent} from './reservation/offline-payment/offline-payment.component';
import {ViewTicketComponent} from './view-ticket/view-ticket.component';
import {ReservationGuard} from './reservation/reservation.guard';
import {ProcessingPaymentComponent} from './reservation/processing-payment/processing-payment.component';
import {LanguageGuard} from './language.guard';
import {NotFoundComponent} from './reservation/not-found/not-found.component';
import {EventGuard} from './event.guard';
import {ErrorComponent} from './reservation/error/error.component';
import {
  DeferredOfflinePaymentComponent
} from './reservation/deferred-offline-payment/deferred-offline-payment.component';
import {UpdateTicketComponent} from './update-ticket/update-ticket.component';
import {EventListAllComponent} from './event-list-all/event-list-all.component';
import {SubscriptionListAllComponent} from './subscription-list-all/subscription-list-all.component';
import {removeEventCss} from './remove-event-css.guard';
import {SubscriptionDisplayComponent} from './subscription-display/subscription-display.component';
import {SuccessSubscriptionComponent} from './reservation/success-subscription/success-subscription.component';
import {MyOrdersComponent} from './my-orders/my-orders.component';
import {MyProfileComponent} from './my-profile/my-profile.component';
import {UserLoggedInGuard} from './user-logged-in.guard';
import {WaitingRoomComponent} from './waiting-room/waiting-room.component';

const detectLanguage: CanActivateFn = (route, state) => {
  return inject(LanguageGuard).canActivate(route, state);
};

const userLoggedIn: CanActivateFn = (route, state) => {
  return inject(UserLoggedInGuard).canActivate(route, state);
};

const fetchEvent: CanActivateFn = (route, state) => {
  return inject(EventGuard).canActivate(route, state);
};

const checkReservationStatus: CanActivateFn = (route, state) => {
  return inject(ReservationGuard).canActivate(route, state);
};


const eventReservationsGuard = [fetchEvent, detectLanguage, checkReservationStatus];
const eventData = {type: 'event', publicIdentifierParameter: 'eventShortName'};
const subscriptionReservationsGuard = [detectLanguage, checkReservationStatus];
const subscriptionData = {type: 'subscription', publicIdentifierParameter: 'id'};

const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [removeEventCss, detectLanguage] },
  { path: 'o/:organizerSlug', component: HomeComponent, canActivate: [removeEventCss, detectLanguage] },
  { path: 'o/:organizerSlug/events-all', component: EventListAllComponent, canActivate: [removeEventCss, detectLanguage] },
  { path: 'events-all', component: EventListAllComponent, canActivate: [removeEventCss, detectLanguage] },
  { path: 'subscriptions-all', component: SubscriptionListAllComponent, canActivate: [removeEventCss, detectLanguage]},
  { path: 'o/:organizerSlug/subscriptions-all', component: SubscriptionListAllComponent, canActivate: [removeEventCss, detectLanguage] },
  { path: 'subscription/:id', component: SubscriptionDisplayComponent, canActivate: [removeEventCss, detectLanguage], data: subscriptionData},
  { path: 'subscription/:id/reservation/:reservationId', data: subscriptionData, children: [
    { path: 'book', component: BookingComponent, canActivate: subscriptionReservationsGuard },
    { path: 'overview', component: OverviewComponent, canActivate: subscriptionReservationsGuard },
    { path: 'waiting-payment', component: OfflinePaymentComponent, canActivate: subscriptionReservationsGuard },
    { path: 'deferred-payment', component: DeferredOfflinePaymentComponent, canActivate: subscriptionReservationsGuard },
    { path: 'processing-payment', component: ProcessingPaymentComponent, canActivate: subscriptionReservationsGuard },
    { path: 'success', component: SuccessSubscriptionComponent, canActivate: subscriptionReservationsGuard },
    { path: 'not-found', component: NotFoundComponent, canActivate: subscriptionReservationsGuard },
    { path: 'error', component: ErrorComponent, canActivate: subscriptionReservationsGuard },
  ]},
  { path: 'event/:eventShortName', component: EventDisplayComponent, canActivate: [fetchEvent, detectLanguage], data: eventData},
  { path: 'event/:eventShortName/poll', loadChildren: () => import('./poll/poll.module').then(m => m.PollModule), canActivate: [fetchEvent, detectLanguage], data: eventData},
  { path: 'event/:eventShortName/reservation/:reservationId', data: eventData, children: [
    { path: 'book', component: BookingComponent, canActivate: eventReservationsGuard },
    { path: 'overview', component: OverviewComponent, canActivate: eventReservationsGuard },
    { path: 'waitingPayment', redirectTo: 'waiting-payment'},
    { path: 'waiting-payment', component: OfflinePaymentComponent, canActivate: eventReservationsGuard },
    { path: 'deferred-payment', component: DeferredOfflinePaymentComponent, canActivate: eventReservationsGuard },
    { path: 'processing-payment', component: ProcessingPaymentComponent, canActivate: eventReservationsGuard },
    { path: 'success', component: SuccessComponent, canActivate: eventReservationsGuard },
    { path: 'not-found', component: NotFoundComponent, canActivate: eventReservationsGuard },
    { path: 'error', component: ErrorComponent, canActivate: eventReservationsGuard },
  ]},
  { path: 'event/:eventShortName/ticket/:ticketId', data: eventData, children: [
    { path: 'view', component: ViewTicketComponent, canActivate: [fetchEvent, detectLanguage] },
    { path: 'update', component: UpdateTicketComponent, canActivate: [fetchEvent, detectLanguage] },
    { path: 'check-in/:ticketCodeHash/waiting-room', component: WaitingRoomComponent, canActivate: [fetchEvent, detectLanguage] }
  ]},
  { path: 'my-orders', component: MyOrdersComponent, canActivate: [userLoggedIn, removeEventCss, detectLanguage] },
  { path: 'my-profile', component: MyProfileComponent, canActivate: [userLoggedIn, removeEventCss, detectLanguage] }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }

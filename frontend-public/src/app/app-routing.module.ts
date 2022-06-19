import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { EventDisplayComponent } from './event-display/event-display.component';
import { BookingComponent } from './reservation/booking/booking.component';
import { OverviewComponent } from './reservation/overview/overview.component';
import { SuccessComponent } from './reservation/success/success.component';
import { OfflinePaymentComponent } from './reservation/offline-payment/offline-payment.component';
import { ViewTicketComponent } from './view-ticket/view-ticket.component';
import { ReservationGuard } from './reservation/reservation.guard';
import { ProcessingPaymentComponent } from './reservation/processing-payment/processing-payment.component';
import { LanguageGuard } from './language.guard';
import { NotFoundComponent } from './reservation/not-found/not-found.component';
import { EventGuard } from './event.guard';
import { ErrorComponent } from './reservation/error/error.component';
import { DeferredOfflinePaymentComponent } from './reservation/deferred-offline-payment/deferred-offline-payment.component';
import { UpdateTicketComponent } from './update-ticket/update-ticket.component';
import { EventListAllComponent } from './event-list-all/event-list-all.component';
import { SubscriptionListAllComponent } from './subscription-list-all/subscription-list-all.component';
import { RemoveEventCssGuard } from './remove-event-css.guard';
import { SubscriptionDisplayComponent } from './subscription-display/subscription-display.component';
import { SuccessSubscriptionComponent } from './reservation/success-subscription/success-subscription.component';
import { MyOrdersComponent } from './my-orders/my-orders.component';
import { MyProfileComponent } from './my-profile/my-profile.component';
import {UserLoggedInGuard} from './user-logged-in.guard';
import {WaitingRoomComponent} from './waiting-room/waiting-room.component';

const eventReservationsGuard = [EventGuard, LanguageGuard, ReservationGuard];
const eventData = {type: 'event', publicIdentifierParameter: 'eventShortName'};
const subscriptionReservationsGuard = [LanguageGuard, ReservationGuard];
const subscriptionData = {type: 'subscription', publicIdentifierParameter: 'id'};

const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [RemoveEventCssGuard, LanguageGuard] },
  { path: 'o/:organizerSlug', component: HomeComponent, canActivate: [RemoveEventCssGuard, LanguageGuard] },
  { path: 'o/:organizerSlug/events-all', component: EventListAllComponent, canActivate: [RemoveEventCssGuard, LanguageGuard] },
  { path: 'events-all', component: EventListAllComponent, canActivate: [RemoveEventCssGuard, LanguageGuard] },
  { path: 'subscriptions-all', component: SubscriptionListAllComponent, canActivate: [RemoveEventCssGuard, LanguageGuard]},
  { path: 'o/:organizerSlug/subscriptions-all', component: SubscriptionListAllComponent, canActivate: [RemoveEventCssGuard, LanguageGuard] },
  { path: 'subscription/:id', component: SubscriptionDisplayComponent, canActivate: [RemoveEventCssGuard, LanguageGuard], data: subscriptionData},
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
  { path: 'event/:eventShortName', component: EventDisplayComponent, canActivate: [EventGuard, LanguageGuard], data: eventData},
  { path: 'event/:eventShortName/poll', loadChildren: () => import('./poll/poll.module').then(m => m.PollModule), canActivate: [EventGuard, LanguageGuard], data: eventData},
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
    { path: 'view', component: ViewTicketComponent, canActivate: [EventGuard, LanguageGuard] },
    { path: 'update', component: UpdateTicketComponent, canActivate: [EventGuard, LanguageGuard] },
    { path: 'check-in/:ticketCodeHash/waiting-room', component: WaitingRoomComponent, canActivate: [EventGuard, LanguageGuard] }
  ]},
  { path: 'my-orders', component: MyOrdersComponent, canActivate: [UserLoggedInGuard, RemoveEventCssGuard, LanguageGuard] },
  { path: 'my-profile', component: MyProfileComponent, canActivate: [UserLoggedInGuard, RemoveEventCssGuard, LanguageGuard] }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }

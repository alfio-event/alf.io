/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager.payment;

import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.TicketReservation;
import alfio.model.transaction.*;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;

import static alfio.manager.TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID;
import static alfio.model.TicketReservation.TicketReservationStatus.DEFERRED_OFFLINE_PAYMENT;
import static java.time.ZoneOffset.UTC;

@Component
@Order(0)
@Log4j2
public class DeferredBankTransferManager implements PaymentProvider {

    private final TicketReservationRepository ticketReservationRepository;
    private final BankTransferManager bankTransferManager;

    public DeferredBankTransferManager(TicketReservationRepository ticketReservationRepository,
                                       BankTransferManager bankTransferManager) {
        this.ticketReservationRepository = ticketReservationRepository;
        this.bankTransferManager = bankTransferManager;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext paymentContext) {
        var options = bankTransferManager.options(paymentContext);
        return paymentContext.getEvent() != null
            && bankTransferManager.bankTransferEnabled(paymentMethod, paymentContext, options)
            && bankTransferManager.isPaymentDeferredEnabled(options);
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        bankTransferManager.postponePayment(spec, DEFERRED_OFFLINE_PAYMENT, Objects.requireNonNull(spec.getEvent()).getBegin());
        bankTransferManager.overrideExistingTransactions(spec);
        return PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
    }

    @Override
    public Map<String, ?> getModelOptions(PaymentContext context) {
        return null;
    }

    @Override
    public boolean accept(Transaction transaction) {
        return PaymentProxy.OFFLINE == transaction.getPaymentProxy() && isReservationStatusCompatible(transaction);
    }

    private Boolean isReservationStatusCompatible(Transaction transaction) {
        return ticketReservationRepository.findOptionalStatusAndValidationById(transaction.getReservationId())
            .map(sv -> sv.getStatus() == DEFERRED_OFFLINE_PAYMENT)
            .orElse(false);
    }
}

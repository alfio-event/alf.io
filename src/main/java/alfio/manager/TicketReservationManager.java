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
package alfio.manager;

import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.manager.support.*;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.model.SpecialPrice.Status;
import alfio.model.Ticket.TicketStatus;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.modification.TicketWithStatistic;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.MonetaryUtil;
import alfio.util.TemplateManager;
import alfio.util.TemplateManager.TemplateOutput;
import com.lowagie.text.DocumentException;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static alfio.model.TicketReservation.TicketReservationStatus.IN_PAYMENT;
import static alfio.model.TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.MonetaryUtil.formatCents;
import static alfio.util.OptionalWrapper.optionally;
import static org.apache.commons.lang3.time.DateUtils.addHours;
import static org.apache.commons.lang3.time.DateUtils.truncate;

@Component
@Transactional
@Log4j2
public class TicketReservationManager {
	
	public static final int RESERVATION_MINUTE = 25;
    public static final String STUCK_TICKETS_MSG = "there are stuck tickets for the event %s. Please check admin area.";
    public static final String STUCK_TICKETS_SUBJECT = "warning: stuck tickets found";

    private final EventRepository eventRepository;
	private final OrganizationRepository organizationRepository;
    private final TicketRepository ticketRepository;
	private final TicketReservationRepository ticketReservationRepository;
	private final TicketCategoryRepository ticketCategoryRepository;
	private final ConfigurationManager configurationManager;
    private final PaymentManager paymentManager;
    private final PromoCodeDiscountRepository promoCodeDiscountRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final TransactionRepository transactionRepository;
	private final NotificationManager notificationManager;
	private final MessageSource messageSource;
	private final TemplateManager templateManager;
	private final TransactionTemplate requiresNewTransactionTemplate;


    public static class NotEnoughTicketsException extends RuntimeException {

	}

	public static class MissingSpecialPriceTokenException extends RuntimeException {
	}

	public static class InvalidSpecialPriceTokenException extends RuntimeException {

	}

	@Data
	public static class TotalPrice {
		private final int priceWithVAT;
		private final int VAT;
		private final int discount;
		private final int discountAppliedCount;
	}

	@Autowired
	public TicketReservationManager(EventRepository eventRepository,
									OrganizationRepository organizationRepository,
									TicketRepository ticketRepository,
									TicketReservationRepository ticketReservationRepository,
									TicketCategoryRepository ticketCategoryRepository,
									ConfigurationManager configurationManager,
									PaymentManager paymentManager,
									PromoCodeDiscountRepository promoCodeDiscountRepository,
									SpecialPriceRepository specialPriceRepository,
									TransactionRepository transactionRepository,
									NotificationManager notificationManager,
									MessageSource messageSource,
									TemplateManager templateManager,
									PlatformTransactionManager transactionManager) {
		this.eventRepository = eventRepository;
        this.organizationRepository = organizationRepository;
        this.ticketRepository = ticketRepository;
		this.ticketReservationRepository = ticketReservationRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
		this.configurationManager = configurationManager;
        this.paymentManager = paymentManager;
        this.promoCodeDiscountRepository = promoCodeDiscountRepository;
        this.specialPriceRepository = specialPriceRepository;
        this.transactionRepository = transactionRepository;
		this.notificationManager = notificationManager;
		this.messageSource = messageSource;
		this.templateManager = templateManager;
		this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
	}
	
    /**
     * Create a ticket reservation. It will create a reservation _only_ if it can find enough tickets. Note that it will not do date/validity validation. This must be ensured by the
     * caller.
     * 
     * @param eventId
     * @param list
     * @param reservationExpiration
     * @return
     */
    public String createTicketReservation(int eventId, List<TicketReservationWithOptionalCodeModification> list, Date reservationExpiration, 
    		Optional<String> specialPriceSessionId, 
    		Optional<String> promotionCodeDiscount, 
    		Locale locale) throws NotEnoughTicketsException, MissingSpecialPriceTokenException, InvalidSpecialPriceTokenException {
        String transactionId = UUID.randomUUID().toString();
        
        Optional<PromoCodeDiscount> discount = promotionCodeDiscount.flatMap((promoCodeDiscount) -> optionally(() -> promoCodeDiscountRepository.findPromoCodeInEvent(eventId, promoCodeDiscount)));
        
        ticketReservationRepository.createNewReservation(transactionId, reservationExpiration, discount.map(PromoCodeDiscount::getId).orElse(null));
		list.forEach(t -> reserveTicketsForCategory(eventId, specialPriceSessionId, transactionId, t, locale));
    	return transactionId;
    }

	private void reserveTicketsForCategory(int eventId, Optional<String> specialPriceSessionId, String transactionId, TicketReservationWithOptionalCodeModification ticketReservation, Locale locale) {
		//first check if there is another pending special price token bound to the current sessionId
		Optional<SpecialPrice> specialPrice = fixToken(ticketReservation.getSpecialPrice(), ticketReservation.getTicketCategoryId(), eventId, specialPriceSessionId, ticketReservation);

		List<Integer> reservedForUpdate = ticketRepository.selectTicketInCategoryForUpdate(eventId, ticketReservation.getTicketCategoryId(), ticketReservation.getAmount());
		int requested = ticketReservation.getAmount();
		if (reservedForUpdate.size() != requested) {
            throw new NotEnoughTicketsException();
        }

		if (specialPrice.isPresent()) {
			if(reservedForUpdate.size() != 1) {
				throw new NotEnoughTicketsException();
			}
            SpecialPrice sp = specialPrice.get();
            ticketRepository.reserveTicket(transactionId, reservedForUpdate.stream().findFirst().orElseThrow(IllegalStateException::new),sp.getId(), locale.getLanguage());
            specialPriceRepository.updateStatus(sp.getId(), Status.PENDING.toString(), sp.getSessionIdentifier());
        } else {
            ticketRepository.reserveTickets(transactionId, reservedForUpdate, locale.getLanguage());
        }
	}

	private Optional<SpecialPrice> fixToken(Optional<SpecialPrice> token, int ticketCategoryId, int eventId, Optional<String> specialPriceSessionId, TicketReservationWithOptionalCodeModification ticketReservation) {

		TicketCategory ticketCategory = ticketCategoryRepository.getById(ticketCategoryId, eventId);
		if(!ticketCategory.isAccessRestricted()) {
			return Optional.empty();
		}

		Optional<SpecialPrice> specialPrice = renewSpecialPrice(token, specialPriceSessionId);

		if(token.isPresent() && !specialPrice.isPresent()) {
			//there is a special price in the request but this isn't valid anymore
			throw new InvalidSpecialPriceTokenException();
		}

		boolean canAccessRestrictedCategory = specialPrice.isPresent()
				&& specialPrice.get().getStatus() == SpecialPrice.Status.FREE
				&& specialPrice.get().getTicketCategoryId() == ticketCategoryId;


		if (canAccessRestrictedCategory && ticketReservation.getAmount() > 1) {
			throw new NotEnoughTicketsException();
		}

		if (!canAccessRestrictedCategory && ticketCategory.isAccessRestricted()) {
			throw new MissingSpecialPriceTokenException();
		}

		return specialPrice;
	}

    public PaymentResult confirm(String gatewayToken, Event event, String reservationId,
								 String email, String fullName, String billingAddress,
								 TotalPrice reservationCost, Optional<String> specialPriceSessionId, Optional<PaymentProxy> method) {
		PaymentProxy paymentProxy = method.orElse(PaymentProxy.STRIPE);
		if(!reservationReadyForPayment(reservationCost, paymentProxy, reservationId, email, fullName, billingAddress)) {
			return PaymentResult.unsuccessful("error.STEP2_UNABLE_TO_TRANSITION");
		}
        try {
            PaymentResult paymentResult;
			ticketReservationRepository.lockReservationForUpdate(reservationId);
			if(reservationCost.getPriceWithVAT() > 0) {
				switch(paymentProxy) {
					case STRIPE:
						paymentResult = paymentManager.processPayment(reservationId, gatewayToken, reservationCost.getPriceWithVAT(), event, email, fullName, billingAddress);
						if(!paymentResult.isSuccessful()) {
							reTransitionToPending(reservationId);
							return paymentResult;
						}
						break;
					case OFFLINE:
						transitionToOfflinePayment(event, reservationId, email, fullName, billingAddress);
						return PaymentResult.successful("not-paid");
					case ON_SITE:
						paymentResult = PaymentResult.successful("not-paid");
						break;
					default:
						throw new IllegalArgumentException("Payment proxy "+paymentProxy+ " not recognized");
				}
			} else {
                paymentResult = PaymentResult.successful("not-paid");
            }
            completeReservation(reservationId, email, fullName, billingAddress, specialPriceSessionId, paymentProxy);
            return paymentResult;
        } catch(Exception ex) {
            //it is guaranteed that in this case we're dealing with "local" error (e.g. database failure),
            //thus it is safer to not rollback the reservation status
            log.error("unexpected error during payment confirmation", ex);
            return PaymentResult.unsuccessful("error.STEP2_STRIPE_unexpected");
        }

    }

	private boolean reservationReadyForPayment(TotalPrice reservationCost, PaymentProxy paymentProxy, String reservationId, String email, String fullName, String billingAddress) {
		if(reservationCost.getPriceWithVAT() > 0 && paymentProxy == PaymentProxy.STRIPE) {
			try {
				transitionToInPayment(reservationId, email, fullName, billingAddress);
			} catch (Exception e) {
				//unable to do the transition. Exiting.
				log.debug(String.format("unable to flag the reservation %s as IN_PAYMENT", reservationId), e);
				return false;
			}
		}
		return true;
	}

	public void confirmOfflinePayment(Event event, String reservationId) {
		TicketReservation ticketReservation = findById(reservationId).orElseThrow(IllegalArgumentException::new);
		ticketReservationRepository.lockReservationForUpdate(reservationId);
		Validate.isTrue(ticketReservation.getPaymentMethod() == PaymentProxy.OFFLINE, "invalid payment method");
		Validate.isTrue(ticketReservation.getStatus() == TicketReservationStatus.OFFLINE_PAYMENT, "invalid status");
		ticketReservationRepository.updateTicketReservationStatus(reservationId, TicketReservationStatus.COMPLETE.name());
		acquireTickets(TicketStatus.ACQUIRED, PaymentProxy.OFFLINE, reservationId, ticketReservation.getEmail(), ticketReservation.getFullName(), ticketReservation.getBillingAddress());
		Locale language = findReservationLanguage(reservationId);
		notificationManager.sendSimpleEmail(event, ticketReservation.getEmail(), messageSource.getMessage("reservation-email-subject",
				new Object[]{ getShortReservationID(reservationId), event.getShortName()}, language), () -> templateManager.renderClassPathResource("/alfio/templates/confirmation-email-txt.ms", prepareModelForReservationEmail(event, ticketReservation), language, TemplateOutput.TEXT));
	}

    private Locale findReservationLanguage(String reservationId) {
        return ticketRepository.findTicketsInReservation(reservationId).stream().findFirst().flatMap(t -> Optional.ofNullable(t.getUserLanguage()).map(Locale::forLanguageTag)).orElse(Locale.ENGLISH);
    }

    public void deleteOfflinePayment(Event event, String reservationId) {
		TicketReservation reservation = findById(reservationId).orElseThrow(IllegalArgumentException::new);
		Validate.isTrue(reservation.getStatus() == OFFLINE_PAYMENT, "Invalid reservation status");
        Map<String, Object> emailModel = prepareModelForReservationEmail(event, reservation);
        Locale reservationLanguage = findReservationLanguage(reservationId);
        String subject = messageSource.getMessage("reservation-email-expired-subject", new Object[]{getShortReservationID(reservationId), event.getShortName()}, reservationLanguage);
		cancelReservation(reservationId);
        notificationManager.sendSimpleEmail(event, reservation.getEmail(), subject, () -> {
            return templateManager.renderClassPathResource("/alfio/templates/offline-reservation-expired-email-txt.ms", emailModel, reservationLanguage, TemplateOutput.TEXT);
        });
	}

	@Transactional(readOnly = true)
	public Map<String, Object> prepareModelForReservationEmail(Event event, TicketReservation reservation) {
		Map<String, Object> model = new HashMap<>();
		model.put("organization", organizationRepository.getById(event.getOrganizationId()));
		model.put("event", event);
		model.put("ticketReservation", reservation);

		Optional<String> vat = getVAT();

		model.put("hasVat", vat.isPresent());
		model.put("vatNr", vat.orElse(""));

		OrderSummary orderSummary = orderSummaryForReservationId(reservation.getId(), event);
		model.put("tickets", findTicketsInReservation(reservation.getId()));
		model.put("orderSummary", orderSummary);
		model.put("reservationUrl", reservationUrl(reservation.getId()));
		return model;
	}

    private void transitionToInPayment(String reservationId, String email, String fullName, String billingAddress) {
		requiresNewTransactionTemplate.execute(status -> {
            int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, IN_PAYMENT.toString(), email, fullName, billingAddress, null, PaymentProxy.STRIPE.toString());
            Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got "+updatedReservation);
            return null;
        });
    }

	private void transitionToOfflinePayment(Event event, String reservationId, String email, String fullName, String billingAddress) {
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        Date newExpiration = Date.from(now.plusDays(configurationManager.getIntConfigValue(OFFLINE_PAYMENT_DAYS, 5)).truncatedTo(ChronoUnit.HALF_DAYS).toInstant());
    	int updatedReservation = ticketReservationRepository.postponePayment(reservationId, newExpiration, email, fullName, billingAddress);
		Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got "+updatedReservation);
    }

	private void reTransitionToPending(String reservationId) {
		int updatedReservation = ticketReservationRepository.updateTicketStatus(reservationId, TicketReservationStatus.PENDING.toString());
		Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got "+updatedReservation);
	}
    
	//check internal consistency between the 3 values
    public Optional<Triple<Event, TicketReservation, Ticket>> from(String eventName, String reservationId, String ticketIdentifier) {
    	return optionally(() -> Triple.of(eventRepository.findByShortName(eventName), 
				ticketReservationRepository.findReservationById(reservationId), 
				ticketRepository.findByUUID(ticketIdentifier))).flatMap((x) -> {
					
					Ticket t = x.getRight();
					Event e = x.getLeft();
					TicketReservation tr = x.getMiddle();
					
					if(tr.getId().equals(t.getTicketsReservationId()) && e.getId() == t.getEventId()) {
						return Optional.of(x);
					} else {
						return Optional.empty();
					}
					
				});
    }

    /**
     * Set the tickets attached to the reservation to the ACQUIRED state and the ticket reservation to the COMPLETE state. Additionally it will save email/fullName/billingaddress.
     *  @param reservationId
     * @param email
	 * @param fullName
	 * @param billingAddress
	 * @param specialPriceSessionId
	 */
	private void completeReservation(String reservationId, String email, String fullName, String billingAddress, Optional<String> specialPriceSessionId, PaymentProxy paymentProxy) {
		if(paymentProxy != PaymentProxy.OFFLINE) {
			TicketStatus ticketStatus = paymentProxy == PaymentProxy.STRIPE ? TicketStatus.ACQUIRED : TicketStatus.TO_BE_PAID;
			acquireTickets(ticketStatus, paymentProxy, reservationId, email, fullName, billingAddress);
		}
		//cleanup unused special price codes...
		specialPriceSessionId.ifPresent(specialPriceRepository::unbindFromSession);
	}

	private void acquireTickets(TicketStatus ticketStatus, PaymentProxy paymentProxy, String reservationId, String email, String fullName, String billingAddress) {
		int updatedTickets = ticketRepository.updateTicketsStatusWithReservationId(reservationId, ticketStatus.toString());
		Validate.isTrue(updatedTickets > 0, "no tickets have been updated");
		specialPriceRepository.updateStatusForReservation(Collections.singletonList(reservationId), Status.TAKEN.toString());
		ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
		int updatedReservation = ticketReservationRepository.updateTicketReservation(reservationId, TicketReservationStatus.COMPLETE.toString(), email, fullName, billingAddress, timestamp, paymentProxy.toString());
		Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
	}

	void cleanupExpiredReservations(Date expirationDate) {
		List<String> expiredReservationIds = ticketReservationRepository.findExpiredReservation(expirationDate);
		if(expiredReservationIds.isEmpty()) {
			return;
		}
		
		specialPriceRepository.updateStatusForReservation(expiredReservationIds, Status.FREE.toString());
		ticketRepository.freeFromReservation(expiredReservationIds);
		ticketReservationRepository.remove(expiredReservationIds);
	}

    void cleanupExpiredOfflineReservations(Date expirationDate) {
        ticketReservationRepository.findExpiredOfflineReservations(expirationDate).forEach(this::cleanupOfflinePayment);
    }

    private void cleanupOfflinePayment(String reservationId) {
        try {
            requiresNewTransactionTemplate.execute((tc) -> {
                deleteOfflinePayment(eventRepository.findByReservationId(reservationId), reservationId);
                return null;
            });
        } catch (Exception e) {
            log.error("error during reservation cleanup (id "+reservationId+")", e);
        }
    }

    /**
     * Finds all the reservations that are "stuck" in payment status.
     * This could happen when there is an internal error after a successful credit card charge.
     *
     * @param expirationDate expiration date
     */
    public void markExpiredInPaymentReservationAsStuck(Date expirationDate) {
        final List<String> stuckReservations = ticketReservationRepository.findStuckReservations(expirationDate);
        stuckReservations.forEach(reservationId -> ticketReservationRepository.updateTicketStatus(reservationId, TicketReservationStatus.STUCK.name()));
        stuckReservations.stream()
                .map(id -> ticketRepository.findTicketsInReservation(id).stream().findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToInt(Ticket::getEventId)
                .distinct()
                .mapToObj(eventRepository::findById)
                .map(e -> Pair.of(e, organizationRepository.getById(e.getOrganizationId())))
                .forEach(pair -> notificationManager.sendSimpleEmail(pair.getLeft(), pair.getRight().getEmail(),
								STUCK_TICKETS_SUBJECT,
								() -> String.format(STUCK_TICKETS_MSG, pair.getLeft().getShortName()))
                );
    }

    public List<TicketWithStatistic> loadModifiedTickets(int eventId, int categoryId) {
        Event event = eventRepository.findById(eventId);
        return ticketRepository.findAllModifiedTickets(eventId, categoryId).stream()
                .map(t -> new TicketWithStatistic(t, ticketReservationRepository.findReservationById(t.getTicketsReservationId()),
						event.getZoneId(), optionally(() -> transactionRepository.loadByReservationId(t.getTicketsReservationId()))))
				.sorted()
                .collect(Collectors.toList());
    }

	private int totalFrom(List<Ticket> tickets, Event event) {
		return tickets.stream().mapToInt(Ticket::getPaidPriceInCents).sum();
    }

	/**
	 * Get the total cost with VAT if it's not included in the ticket price.
	 * 
	 * @param reservationId
	 * @return
	 */
    public TotalPrice totalReservationCostWithVAT(String reservationId) {
    	TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
    	
    	Optional<PromoCodeDiscount> promoCodeDiscount = Optional.ofNullable(reservation.getPromoCodeDiscountId()).map(promoCodeDiscountRepository::findById);
    	
    	Event event = eventRepository.findByReservationId(reservationId);
		List<Ticket> tickets = ticketRepository.findTicketsInReservation(reservationId);
		int net = totalFrom(tickets, event);
		int vat = totalVat(tickets, event.getVat());
		final int amountToBeDiscounted;
		final int discountAppliedCount;
		
		
		//TODO cleanup/refactor
		//take in account the discount code
		if (promoCodeDiscount.isPresent()) {
			PromoCodeDiscount discount = promoCodeDiscount.get();
			
			if(discount.getDiscountType() == DiscountType.FIXED_AMOUNT) {
				//we apply the fixed discount for each paid ticket
				discountAppliedCount = ((int) tickets.stream().filter(t -> t.getPaidPriceInCents() > 0).count());
				amountToBeDiscounted =  discountAppliedCount * discount.getDiscountAmount();
			} else {
				amountToBeDiscounted = MonetaryUtil.calcPercentage(event.isVatIncluded() ? net + vat : net, new BigDecimal(discount.getDiscountAmount()));
				discountAppliedCount = 1;
			}
			
			// recalc the net and vat
			if(event.isVatIncluded()) {
				int finalPrice = Math.max(net + vat - amountToBeDiscounted, 0);
				net = MonetaryUtil.removeVAT(finalPrice, event.getVat());
				vat = finalPrice - net;
			} else {
				net = Math.max(net - amountToBeDiscounted, 0);
				vat = MonetaryUtil.calcVat(net, event.getVat());
			}
		} else {
			amountToBeDiscounted = 0;
			discountAppliedCount = 0;
		}
		
		return new TotalPrice(net + vat, vat, -amountToBeDiscounted, discountAppliedCount);
    }
    

	private int totalVat(List<Ticket> tickets, BigDecimal vat) {
		return tickets.stream().mapToInt(Ticket::getPaidPriceInCents).map(p -> MonetaryUtil.calcVat(p, vat)).sum();
	}
    
    public OrderSummary orderSummaryForReservationId(String reservationId, Event event) {
		TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
		TotalPrice reservationCost = totalReservationCostWithVAT(reservationId);
		
    	List<SummaryRow> summary = extractSummary(reservationId, event);
    	
    	//
		boolean free = reservationCost.getPriceWithVAT() == 0;
		
		Optional<PromoCodeDiscount> promoCodeDiscount = Optional.ofNullable(reservation.getPromoCodeDiscountId()).map(promoCodeDiscountRepository::findById);
		
		//add the discount summary row only if it has an effect
		if(reservationCost.getDiscount() != 0) {
			promoCodeDiscount.ifPresent((promo) -> {
				String formattedSingleAmount = "-" + (promo.getDiscountType() == DiscountType.FIXED_AMOUNT ? formatCents(promo.getDiscountAmount()) : (promo.getDiscountAmount()+"%"));
				summary.add(new SummaryRow(promo.getPromoCode(), 
						formattedSingleAmount, 
						reservationCost.discountAppliedCount, 
						formatCents(reservationCost.discount), reservationCost.discount));
			});
		}
				
		
		return new OrderSummary(reservationCost,
				summary, free,
				formatCents(reservationCost.getPriceWithVAT()), formatCents(reservationCost.getVAT()),
				reservation.getStatus() == TicketReservationStatus.OFFLINE_PAYMENT,
				reservation.getPaymentMethod() == PaymentProxy.ON_SITE);
    }
    
    private List<SummaryRow> extractSummary(String reservationId, Event event) {
    	List<SummaryRow> summary = new ArrayList<>();
    	List<Ticket> tickets = ticketRepository.findTicketsInReservation(reservationId);
    	tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).forEach((categoryId, ticketsByCategory) -> {
            int paidPriceInCents = ticketsByCategory.get(0).getPaidPriceInCents();
            if(event.isVatIncluded()) {
                paidPriceInCents = MonetaryUtil.addVAT(paidPriceInCents, event.getVat());
            }
    		String categoryName = ticketCategoryRepository.getById(categoryId, event.getId()).getName();
            final int subTotal = paidPriceInCents * ticketsByCategory.size();
            summary.add(new SummaryRow(categoryName, formatCents(paidPriceInCents), ticketsByCategory.size(), formatCents(subTotal), subTotal));
    	});
    	return summary;
    } 
    
    public String reservationUrl(String reservationId) {
    	Event event = eventRepository.findByReservationId(reservationId);
		return StringUtils.removeEnd(configurationManager.getRequiredValue(ConfigurationKeys.BASE_URL), "/")
				+ "/event/" + event.getShortName() + "/reservation/" + reservationId;
    }


	public int maxAmountOfTickets() {
        return configurationManager.getIntConfigValue(ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, 5);
	}
	
	public Optional<TicketReservation> findById(String reservationId) {
		return optionally(() -> ticketReservationRepository.findReservationById(reservationId));
	}

    private Optional<TicketReservation> findByIdForNotification(String reservationId, ZoneId eventZoneId, int quietPeriod) {
        return findById(reservationId).filter(notificationNotSent(eventZoneId, quietPeriod));
    }

    private static Predicate<TicketReservation> notificationNotSent(ZoneId eventZoneId, int quietPeriod) {
        return r -> r.latestNotificationTimestamp(eventZoneId)
                .map(t -> t.truncatedTo(ChronoUnit.DAYS).plusDays(quietPeriod).isBefore(ZonedDateTime.now(eventZoneId).truncatedTo(ChronoUnit.DAYS)))
                .orElse(true);
    }

	public void cancelPendingReservation(String reservationId) {
		Validate.isTrue(ticketReservationRepository.findReservationById(reservationId).getStatus() == TicketReservationStatus.PENDING, "status is not PENDING");
		cancelReservation(reservationId);
	}

	private void cancelReservation(String reservationId) {
		List<String> reservationIdsToRemove = Collections.singletonList(reservationId);
		specialPriceRepository.updateStatusForReservation(reservationIdsToRemove, Status.FREE.toString());
		int updatedTickets = ticketRepository.freeFromReservation(reservationIdsToRemove);
		Validate.isTrue(updatedTickets > 0, "no tickets have been updated");
		int removedReservation = ticketReservationRepository.remove(reservationIdsToRemove);
		Validate.isTrue(removedReservation == 1, "expected exactly one removed reservation, got "+removedReservation);
	}

	public SpecialPrice getSpecialPriceByCode(String code) {
		return specialPriceRepository.getByCode(code);
	}

	public Optional<SpecialPrice> renewSpecialPrice(Optional<SpecialPrice> specialPrice, Optional<String> specialPriceSessionId) {
		Validate.isTrue(specialPrice.isPresent(), "special price is not present");

		SpecialPrice price = specialPrice.get();

		if(!specialPriceSessionId.isPresent()) {
			log.warn("cannot renew special price {}: session identifier not found or not matching", price.getCode());
			return Optional.empty();
		}

		if(price.getStatus() == Status.PENDING && !StringUtils.equals(price.getSessionIdentifier(), specialPriceSessionId.get())) {
			log.warn("cannot renew special price {}: session identifier not found or not matching", price.getCode());
			return Optional.empty();
		}

		if(price.getStatus() == Status.FREE) {
			specialPriceRepository.bindToSession(price.getId(), specialPriceSessionId.get());
			return Optional.of(getSpecialPriceByCode(price.getCode()));
		} else if(price.getStatus() == Status.PENDING) {
			Optional<Ticket> optionalTicket = optionally(() -> ticketRepository.findBySpecialPriceId(price.getId()));
			if(optionalTicket.isPresent()) {
				cancelPendingReservation(optionalTicket.get().getTicketsReservationId());
				return Optional.of(getSpecialPriceByCode(price.getCode()));
			}
		}

		return specialPrice;
	}

	public List<Ticket> findTicketsInReservation(String reservationId) {
		return ticketRepository.findTicketsInReservation(reservationId);
	}

	public int countUnsoldTicket(int eventId, int categoryId) {
		return ticketRepository.countNotSoldTickets(eventId, categoryId);
	}
	
	public Optional<String> getVAT() {
		return configurationManager.getStringConfigValue(ConfigurationKeys.VAT_NR);
	}

	public void updateTicketOwner(Ticket ticket,
                                  Locale locale,
                                  Event event,
                                  UpdateTicketOwnerForm updateTicketOwner,
                                  PartialTicketTextGenerator confirmationTextBuilder,
                                  PartialTicketTextGenerator ownerChangeTextBuilder,
                                  PartialTicketPDFGenerator pdfTemplateGenerator,
                                  Optional<UserDetails> userDetails) {

		String newEmail = updateTicketOwner.getEmail().trim();
		String newFullName = updateTicketOwner.getFullName().trim();
		ticketRepository.updateTicketOwner(ticket.getUuid(), newEmail, newFullName);
		//
		ticketRepository.updateOptionalTicketInfo(ticket.getUuid(), updateTicketOwner.getJobTitle(),
				updateTicketOwner.getCompany(),
				updateTicketOwner.getPhoneNumber(),
				updateTicketOwner.getAddress(),
				updateTicketOwner.getCountry(),
				updateTicketOwner.getTShirtSize(),
				updateTicketOwner.getNotes(),
				locale.getLanguage());

		Ticket newTicket = ticketRepository.findByUUID(ticket.getUuid());
		if (!StringUtils.equalsIgnoreCase(newEmail, ticket.getEmail()) || !StringUtils.equalsIgnoreCase(newFullName, ticket.getFullName())) {
			sendTicketByEmail(newTicket, locale, event, confirmationTextBuilder, pdfTemplateGenerator);
		}

        boolean admin = isAdmin(userDetails);

		if (!admin && StringUtils.isNotBlank(ticket.getEmail()) && !StringUtils.equalsIgnoreCase(newEmail, ticket.getEmail())) {
			String subject = messageSource.getMessage("ticket-has-changed-owner-subject", new Object[] {event.getShortName()}, locale);
			notificationManager.sendSimpleEmail(event, ticket.getEmail(), subject, ownerChangeTextBuilder.generate(newTicket));
		}

        if(admin) {
            TicketReservation reservation = findById(ticket.getTicketsReservationId()).orElseThrow(IllegalStateException::new);
            //if the current user is admin, then it would be good to update also the name of the Reservation Owner (not the e-mail, since
            String username = userDetails.get().getUsername();
            log.warn("Reservation {}: forced assignee replacement old: {} new: {}", reservation.getId(), reservation.getFullName(), username);
            ticketReservationRepository.updateAssignee(reservation.getId(), username);
        }
	}

    private boolean isAdmin(Optional<UserDetails> userDetails) {
        return userDetails.flatMap(u -> u.getAuthorities().stream().filter(a -> AuthorityRepository.ROLE_ADMIN.equals(a.getAuthority())).findFirst()).isPresent();
    }

	private void sendTicketByEmail(Ticket ticket, Locale locale, Event event, PartialTicketTextGenerator confirmationTextBuilder, PartialTicketPDFGenerator pdfTemplateGenerator) {
		try {
            notificationManager.sendTicketByEmail(ticket, event, locale, confirmationTextBuilder, pdfTemplateGenerator);
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        }
	}

	public Optional<Triple<Event, TicketReservation, Ticket>> fetchComplete(String eventName, String reservationId, String ticketIdentifier) {
		return from(eventName, reservationId, ticketIdentifier).flatMap((t) -> {
			if(t.getMiddle().getStatus() == TicketReservationStatus.COMPLETE) {
				return Optional.of(t);
			} else {
				return Optional.empty();
			}
		});
	}

	/**
	 * Return a fully present triple only if the values are present (obviously) and the the reservation has a COMPLETE status and the ticket is considered assigned.
	 *
	 * @param eventName
	 * @param reservationId
	 * @param ticketIdentifier
	 * @return
	 */
	public Optional<Triple<Event, TicketReservation, Ticket>> fetchCompleteAndAssigned(String eventName, String reservationId, String ticketIdentifier) {
		return fetchComplete(eventName, reservationId, ticketIdentifier).flatMap((t) -> {
			if(t.getRight().getAssigned()) {
				return Optional.of(t);
			} else {
				return Optional.empty();
			}
		});
	}

	public List<Pair<TicketReservation, OrderSummary>> fetchWaitingForPayment(List<String> reservationIds, Event event) {
		return ticketReservationRepository.findAllReservationsWaitingForPayment().stream()
					.filter(reservationIds::contains)
					.map(id -> Pair.of(ticketReservationRepository.findReservationById(id), orderSummaryForReservationId(id, event)))
					.collect(Collectors.toList());
	}

	void sendReminderForOfflinePayments() {
		Date expiration = truncate(addHours(new Date(), configurationManager.getIntConfigValue(OFFLINE_REMINDER_HOURS, 24)), Calendar.DATE);
		ticketReservationRepository.findAllOfflinePaymentReservationForNotification(expiration).stream()
				.map(reservation -> {
					Optional<Ticket> ticket = ticketRepository.findTicketsInReservation(reservation.getId()).stream().findFirst();
					Optional<Event> event = ticket.map(t -> eventRepository.findById(t.getEventId()));
					Optional<Locale> locale = ticket.map(t -> Locale.forLanguageTag(t.getUserLanguage()));
					return Triple.of(reservation, event, locale);
				})
				.filter(p -> p.getMiddle().isPresent())
				.map(p -> Triple.of(p.getLeft(), p.getMiddle().get(), p.getRight().get()))
				.forEach(p -> {
					TicketReservation reservation = p.getLeft();
					Event event = p.getMiddle();
					Map<String, Object> model = prepareModelForReservationEmail(event, reservation);
					model.put("expirationDate", ZonedDateTime.ofInstant(reservation.getValidity().toInstant(), event.getZoneId()));
					Locale locale = p.getRight();
					ticketReservationRepository.flagAsOfflinePaymentReminderSent(reservation.getId());
					notificationManager.sendSimpleEmail(event, reservation.getEmail(), messageSource.getMessage("reservation.reminder.mail.subject", new Object[]{ getShortReservationID(reservation.getId()) }, locale), () -> templateManager.renderClassPathResource("/alfio/templates/reminder-email-txt.ms", model, locale, TemplateOutput.TEXT));
				});
	}

    void sendReminderForTicketAssignment() {
        int daysBeforeStart = configurationManager.getIntConfigValue(ASSIGNMENT_REMINDER_START, 10);
        eventRepository.findAll().stream()
                .filter(e -> ZonedDateTime.now(e.getZoneId()).truncatedTo(ChronoUnit.DAYS).plusDays(daysBeforeStart).isAfter(e.getBegin().truncatedTo(ChronoUnit.DAYS)))
                .map(e -> Pair.of(e, ticketRepository.findAllReservationsConfirmedButNotAssigned(e.getId())))
                .filter(p -> !p.getRight().isEmpty())
                .forEach(this::sendAssignmentReminder);
    }

    private void sendAssignmentReminder(Pair<Event, List<String>> p) {
        try {
            requiresNewTransactionTemplate.execute(status -> {
                Event event = p.getLeft();
                ZoneId eventZoneId = event.getZoneId();
                int quietPeriod = configurationManager.getIntConfigValue(ConfigurationKeys.ASSIGNMENT_REMINDER_INTERVAL, 3);
                p.getRight().stream()
                        .map(id -> findByIdForNotification(id, eventZoneId, quietPeriod))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .forEach(reservation -> {
                            Map<String, Object> model = prepareModelForReservationEmail(event, reservation);
                            model.put("reservationShortID", getShortReservationID(reservation.getId()));
                            ticketReservationRepository.updateLatestReminderTimestamp(reservation.getId(), ZonedDateTime.now(eventZoneId));
                            Locale locale = findReservationLanguage(reservation.getId());
                            notificationManager.sendSimpleEmail(event, reservation.getEmail(), messageSource.getMessage("reminder.ticket-not-assigned.subject", new Object[]{event.getShortName()}, locale), () -> templateManager.renderClassPathResource("/alfio/templates/reminder-tickets-assignment-email-txt.ms", model, locale, TemplateOutput.TEXT));
                        });
                return null;
            });
        } catch (Exception ex) {
            log.warn("cannot send reminder message", ex);
        }
    }

    public TicketReservation findByPartialID(String reservationId) {
		Validate.notBlank(reservationId, "invalid reservationId");
		Validate.matchesPattern(reservationId, "^[^%]*$", "invalid character found");
		List<TicketReservation> results = ticketReservationRepository.findByPartialID(StringUtils.trimToEmpty(reservationId).toLowerCase() + "%");
		Validate.isTrue(results.size() > 0, "reservation not found");
		Validate.isTrue(results.size() == 1, "multiple results found. Try handling this reservation manually.");
		return results.get(0);
	}

	public String getShortReservationID(String reservationId) {
		return StringUtils.substring(reservationId, 0, configurationManager.getIntConfigValue(PARTIAL_RESERVATION_ID_LENGTH, 8)).toUpperCase();
	}
}

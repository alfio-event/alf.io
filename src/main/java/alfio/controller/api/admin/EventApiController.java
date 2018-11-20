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
package alfio.controller.api.admin;

import alfio.controller.api.support.DescriptionsLoader;
import alfio.controller.api.support.EventListItem;
import alfio.controller.api.support.PageAndContent;
import alfio.controller.api.support.TicketHelper;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.*;
import alfio.manager.i18n.I18nManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.*;
import alfio.model.result.ValidationResult;
import alfio.model.transaction.Transaction;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.DynamicFieldTemplateRepository;
import alfio.repository.SponsorScanRepository;
import alfio.repository.TicketCategoryDescriptionRepository;
import alfio.repository.TicketFieldRepository;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import alfio.util.TemplateManager;
import alfio.util.Validator;
import ch.digitalfondue.basicxlsx.*;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.security.Principal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static alfio.util.OptionalWrapper.optionally;
import static alfio.util.Validator.*;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.springframework.web.bind.annotation.RequestMethod.*;

@RestController
@RequestMapping("/admin/api")
@Log4j2
@AllArgsConstructor
public class EventApiController {

    private static final String OK = "OK";
    private static final String CUSTOM_FIELDS_PREFIX = "custom:";
    private final EventManager eventManager;
    private final EventStatisticsManager eventStatisticsManager;
    private final I18nManager i18nManager;
    private final TicketReservationManager ticketReservationManager;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final TicketFieldRepository ticketFieldRepository;
    private final DescriptionsLoader descriptionsLoader;
    private final TicketHelper ticketHelper;
    private final DynamicFieldTemplateRepository dynamicFieldTemplateRepository;
    private final UserManager userManager;
    private final SponsorScanRepository sponsorScanRepository;
    private final PaymentManager paymentManager;
    private final TemplateManager templateManager;
    private final FileUploadManager fileUploadManager;


    @ExceptionHandler(DataAccessException.class)
    public String exception(DataAccessException e) {
        log.warn("unhandled exception", e);
        return "unexpected error. More info in the application log";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unhandledException(Exception e) {
        if(!(e instanceof IllegalArgumentException)) {
            log.warn("unhandled exception", e);
        }
        return e.getMessage();
    }


    @RequestMapping(value = "/paymentProxies/{organizationId}", method = GET)
    @ResponseStatus(HttpStatus.OK)
    public List<PaymentManager.PaymentMethodDTO> getPaymentProxies( @PathVariable("organizationId") int organizationId, Principal principal) {
        return userManager.findUserOrganizations(principal.getName())
            .stream()
            .filter(o -> o.getId() == organizationId)
            .findFirst()
            .map(o -> paymentManager.getPaymentMethods(o.getId()))
            .orElse(Collections.emptyList());
    }

    @RequestMapping(value = "/events", method = GET, headers = "Authorization")
    public List<EventListItem> getAllEventsForExternal(Principal principal, HttpServletRequest request) {
        List<Integer> userOrganizations = userManager.findUserOrganizations(principal.getName()).stream().map(Organization::getId).collect(toList());
        return eventManager.getActiveEvents().stream()
            .filter(e -> userOrganizations.contains(e.getOrganizationId()))
            .sorted(Comparator.comparing(e -> e.getBegin().withZoneSameInstant(ZoneId.systemDefault())))
            .map(s -> new EventListItem(s, request.getContextPath(), descriptionsLoader.eventDescriptions()))
            .collect(toList());
    }

    @RequestMapping(value = "/events", method = GET)
    public List<EventStatistic> getAllEvents(Principal principal) {
        return eventStatisticsManager.getAllEventsWithStatistics(principal.getName());
    }

    @RequestMapping(value = "/active-events", method = GET)
    public List<EventStatistic> getAllActiveEvents(Principal principal) {
        return eventStatisticsManager.getAllEventsWithStatisticsFilteredBy(principal.getName(), event -> !event.expiredSince(14));
    }

    @RequestMapping(value = "/expired-events", method = GET)
    public List<EventStatistic> getAllExpiredEvents(Principal principal) {
        return eventStatisticsManager.getAllEventsWithStatisticsFilteredBy(principal.getName(), event -> event.expiredSince(14));
    }


    @AllArgsConstructor
    @Getter
    public static class EventAndOrganization {
        private final EventWithAdditionalInfo event;
        private final Organization organization;
    }


    @RequestMapping(value = "/events/{name}", method = GET)
    public ResponseEntity<EventAndOrganization> getSingleEvent(@PathVariable("name") String eventName, Principal principal) {
        final String username = principal.getName();
        return optionally(() -> eventStatisticsManager.getEventWithAdditionalInfo(eventName, username))
            .map(event -> {
                EventAndOrganization out = new EventAndOrganization(event, eventManager.loadOrganizer(event.getEvent(), username));
                return ResponseEntity.ok(out);
            }).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
    
    @RequestMapping(value ="/events/{eventId}", method = DELETE)
    public void deleteEvent(@PathVariable("eventId") int eventId, Principal principal) {
    	eventManager.deleteEvent(eventId, principal.getName());
    }

    @RequestMapping(value = "/events/id/{eventId}", method = GET)
    public Event getSingleEventById(@PathVariable("eventId") int eventId, Principal principal) {
        return eventManager.getSingleEventById(eventId, principal.getName());
    }

    @RequestMapping(value = "/events/check", method = POST)
    public ValidationResult validateEventRequest(@RequestBody EventModification eventModification, Errors errors) {
        return validateEvent(eventModification,errors);
    }

    public static ValidationResult validateEvent(EventModification eventModification, Errors errors) {
        ValidationResult base = validateEventHeader(Optional.empty(), eventModification, errors)
            .or(validateEventDates(eventModification, errors))
            .or(validateTicketCategories(eventModification, errors))
            .or(validateEventPrices(Optional.empty(), eventModification, errors))
            .or(eventModification.getAdditionalServices().stream().map(as -> validateAdditionalService(as, eventModification, errors)).reduce(ValidationResult::or).orElse(ValidationResult.success()));
        AtomicInteger counter = new AtomicInteger();
        return base.or(eventModification.getTicketCategories().stream()
                .map(c -> validateCategory(c, errors, "ticketCategories[" + counter.getAndIncrement() + "].", eventModification))
                .reduce(ValidationResult::or)
                .orElse(ValidationResult.success()))
            .or(validateAdditionalTicketFields(eventModification.getTicketFields(), errors));
    }


    private static ValidationResult validateAdditionalTicketFields(List<EventModification.AdditionalField> ticketFields, Errors errors) {
        //meh
        AtomicInteger cnt = new AtomicInteger();
        return Optional.ofNullable(ticketFields).orElseGet(Collections::emptyList).stream().map(field -> {
            String prefix = "ticketFields["+cnt.getAndIncrement()+"]";
            if (StringUtils.isBlank(field.getName())) {
                errors.rejectValue(prefix + ".name", "error.required");
            }
            //TODO: check label value is present for all the locales
            //TODO: for select check option value+label

            return Validator.evaluateValidationResult(errors);
        }).reduce(ValidationResult::or).orElseGet(ValidationResult::success);
    }

    @RequestMapping(value = "/events/new", method = POST)
    public String insertEvent(@RequestBody EventModification eventModification) {
        eventManager.createEvent(eventModification);
        return OK;
    }

    @RequestMapping(value = "/events/{id}/status", method = PUT)
    public String activateEvent(@PathVariable("id") int id, @RequestParam("active") boolean active, Principal principal) {
        eventManager.toggleActiveFlag(id, principal.getName(), active);
        return OK;
    }

    @RequestMapping(value = "/events/{id}/header/update", method = POST)
    public ValidationResult updateHeader(@PathVariable("id") int id, @RequestBody EventModification eventModification, Errors errors,  Principal principal) {
        Event event = eventManager.getSingleEventById(id, principal.getName());
        return validateEventHeader(Optional.of(event), eventModification, errors).ifSuccess(() -> eventManager.updateEventHeader(event, eventModification, principal.getName()));
    }

    @RequestMapping(value = "/events/{id}/prices/update", method = POST)
    public ValidationResult updatePrices(@PathVariable("id") int id, @RequestBody EventModification eventModification, Errors errors,  Principal principal) {
        Event event = eventManager.getSingleEventById(id, principal.getName());
        return validateEventPrices(Optional.of(event), eventModification, errors).ifSuccess(() -> eventManager.updateEventPrices(event, eventModification, principal.getName()));
    }

    @RequestMapping(value = "/events/{eventId}/categories/{categoryId}/update", method = POST)
    public ValidationResult updateExistingCategory(@PathVariable("eventId") int eventId, @PathVariable("categoryId") int categoryId, @RequestBody TicketCategoryModification category, Errors errors, Principal principal) {
        return validateCategory(category, errors).ifSuccess(() -> eventManager.updateCategory(categoryId, eventId, category, principal.getName()));
    }

    @RequestMapping(value = "/events/{eventId}/categories/new", method = POST)
    public ValidationResult createCategory(@PathVariable("eventId") int eventId, @RequestBody TicketCategoryModification category, Errors errors, Principal principal) {
        return validateCategory(category, errors).ifSuccess(() -> eventManager.insertCategory(eventId, category, principal.getName()));
    }
    
    @RequestMapping(value = "/events/reallocate", method = PUT)
    public String reallocateTickets(@RequestBody TicketAllocationModification form) {
        eventManager.reallocateTickets(form.getSrcCategoryId(), form.getTargetCategoryId(), form.getEventId());
        return OK;
    }

    @RequestMapping(value = "/events/{eventName}/category/{categoryId}/unbind-tickets", method = PUT)
    public String unbindTickets(@PathVariable("eventName") String eventName, @PathVariable("categoryId") int categoryId, Principal principal) {
        eventManager.unbindTickets(eventName, categoryId, principal.getName());
        return OK;
    }

    private static final List<String> FIXED_FIELDS = Arrays.asList("ID", "Creation", "Category", "Event", "Status", "OriginalPrice", "PaidPrice", "Discount", "VAT", "ReservationID", "Full Name", "First Name", "Last Name", "E-Mail", "Locked", "Language", "Confirmation", "Billing Address", "Payment ID", "Payment Method");
    private static final int[] BOM_MARKERS = new int[] {0xEF, 0xBB, 0xBF};

    @RequestMapping("/events/{eventName}/export")
    public void downloadAllTicketsCSV(@PathVariable("eventName") String eventName, @RequestParam(name = "format", defaultValue = "excel") String format, HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        List<String> fields = Arrays.asList(Optional.ofNullable(request.getParameterValues("fields")).orElse(new String[] {}));
        Event event = loadEvent(eventName, principal);
        Map<Integer, TicketCategory> categoriesMap = eventManager.loadTicketCategories(event).stream().collect(Collectors.toMap(TicketCategory::getId, Function.identity()));
        ZoneId eventZoneId = event.getZoneId();

        if ("excel".equals(format)) {
            exportTicketExcel(eventName, response, principal, fields, categoriesMap, eventZoneId);
        } else {
            exportTicketCSV(eventName, response, principal, fields, categoriesMap, eventZoneId);
        }
    }

    private void exportTicketExcel(String eventName, HttpServletResponse response, Principal principal, List<String> fields, Map<Integer,TicketCategory> categoriesMap, ZoneId eventZoneId) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + eventName + "-export.xlsx");

        try (ServletOutputStream out = response.getOutputStream()) {
            exportExcel(eventName + " export", exportHeader(fields), exportLines(eventName, principal, fields, categoriesMap, eventZoneId).collect(Collectors.toList()), out);
        }
    }

    private void exportExcel(String sheetName, String[] header, List<String[]> data, OutputStream out) throws IOException {
        try (StreamingWorkbook workbook = new StreamingWorkbook(out)) {
            Style boldFont = workbook.defineStyle().font().bold(true).build();

            StreamingWorkbook.Row headerRow = StreamingWorkbook.row(Arrays.stream(header)
                .map(v -> Cell.cell(v).withStyle(boldFont))
                .collect(Collectors.toList()));

            Stream<StreamingWorkbook.Row> dataStream = data.stream()
                .map(rowData -> Arrays.stream(rowData).map(Cell::cell).collect(Collectors.toList()))
                .map(StreamingWorkbook::row);

            workbook.withSheet(sheetName, Stream.concat(Stream.of(headerRow), dataStream));
        }
    }

    private void exportTicketCSV(String eventName, HttpServletResponse response,
                           Principal principal, List<String> fields,
                           Map<Integer, TicketCategory> categoriesMap,
                           ZoneId eventZoneId) throws IOException {

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + eventName + "-export.csv");

        try(ServletOutputStream out = response.getOutputStream(); CSVWriter writer = new CSVWriter(new OutputStreamWriter(out))) {
            for (int marker : BOM_MARKERS) {//UGLY-MODE_ON: specify that the file is written in UTF-8 with BOM, thanks to alexr http://stackoverflow.com/a/4192897
                out.write(marker);
            }
            writer.writeNext(exportHeader(fields));
            exportLines(eventName, principal, fields, categoriesMap, eventZoneId).forEachOrdered(writer::writeNext);
            writer.flush();
            out.flush();
        }
    }

    private String[] exportHeader(List<String> fields) {
        return fields.stream().map(f -> {
            if(f.startsWith(CUSTOM_FIELDS_PREFIX)) {
                return f.substring(CUSTOM_FIELDS_PREFIX.length());
            }
            return f;
        }).toArray(String[]::new);
    }

    private Stream<String[]> exportLines(String eventName, Principal principal, List<String> fields, Map<Integer, TicketCategory> categoriesMap, ZoneId eventZoneId) {
        return eventManager.findAllConfirmedTicketsForCSV(eventName, principal.getName()).stream().map(trs -> {
            Ticket t = trs.getTicket();
            TicketReservation reservation = trs.getTicketReservation();
            List<String> line = new ArrayList<>();
            if(fields.contains("ID")) {line.add(t.getUuid());}
            if(fields.contains("Creation")) {line.add(t.getCreation().withZoneSameInstant(eventZoneId).toString());}
            if(fields.contains("Category")) {line.add(categoriesMap.get(t.getCategoryId()).getName());}
            if(fields.contains("Event")) {line.add(eventName);}
            if(fields.contains("Status")) {line.add(t.getStatus().toString());}
            if(fields.contains("OriginalPrice")) {line.add(MonetaryUtil.centsToUnit(t.getSrcPriceCts()).toString());}
            if(fields.contains("PaidPrice")) {line.add(MonetaryUtil.centsToUnit(t.getFinalPriceCts()).toString());}
            if(fields.contains("Discount")) {line.add(MonetaryUtil.centsToUnit(t.getDiscountCts()).toString());}
            if(fields.contains("VAT")) {line.add(MonetaryUtil.centsToUnit(t.getVatCts()).toString());}
            if(fields.contains("ReservationID")) {line.add(t.getTicketsReservationId());}
            if(fields.contains("Full Name")) {line.add(t.getFullName());}
            if(fields.contains("First Name")) {line.add(t.getFirstName());}
            if(fields.contains("Last Name")) {line.add(t.getLastName());}
            if(fields.contains("E-Mail")) {line.add(t.getEmail());}
            if(fields.contains("Locked")) {line.add(String.valueOf(t.getLockedAssignment()));}
            if(fields.contains("Language")) {line.add(String.valueOf(t.getUserLanguage()));}
            if(fields.contains("Confirmation")) {line.add(reservation.getConfirmationTimestamp().withZoneSameInstant(eventZoneId).toString());}
            if(fields.contains("Billing Address")) {line.add(reservation.getBillingAddress());}
            boolean paymentIdRequested = fields.contains("Payment ID");
            boolean paymentGatewayRequested = fields.contains("Payment Method");
            if((paymentIdRequested || paymentGatewayRequested)) {
                Optional<Transaction> transaction = trs.getTransaction();
                if(paymentIdRequested) { line.add(defaultString(transaction.map(Transaction::getPaymentId).orElse(null), transaction.map(Transaction::getTransactionId).orElse(""))); }
                if(paymentGatewayRequested) { line.add(transaction.map(tr -> tr.getPaymentProxy().name()).orElse("")); }
            }

            //obviously not optimized
            Map<String, String> additionalValues = ticketFieldRepository.findAllValuesForTicketId(t.getId());

            Predicate<String> contains = FIXED_FIELDS::contains;

            fields.stream().filter(contains.negate()).filter(f -> f.startsWith(CUSTOM_FIELDS_PREFIX)).forEachOrdered(field -> {
                String customFieldName = field.substring(CUSTOM_FIELDS_PREFIX.length());
                line.add(additionalValues.getOrDefault(customFieldName, "").replaceAll("\"", ""));
            });

            return line.toArray(new String[0]);
        });
    }

    @RequestMapping("/events/{eventName}/sponsor-scan/export")
    public void downloadSponsorScanExport(@PathVariable("eventName") String eventName, @RequestParam(name = "format", defaultValue = "excel") String format, HttpServletResponse response, Principal principal) throws IOException {
        Event event = loadEvent(eventName, principal);
        List<TicketFieldConfiguration> fields = ticketFieldRepository.findAdditionalFieldsForEvent(event.getId());

        List<String> header = new ArrayList<>();
        header.add("Username/Api Key");
        header.add("Description");
        header.add("Timestamp");
        header.add("Full name");
        header.add("Email");
        header.addAll(fields.stream().map(TicketFieldConfiguration::getName).collect(toList()));

        Stream<String[]> sponsorScans = userManager.findAllEnabledUsers(principal.getName()).stream()
            .map(u -> Pair.of(u, userManager.getUserRole(u)))
            .filter(p -> p.getRight() == Role.SPONSOR)
            .flatMap(p -> sponsorScanRepository.loadSponsorData(event.getId(), p.getKey().getId(), SponsorScanRepository.DEFAULT_TIMESTAMP)
                .stream()
                .map(v -> Pair.of(v, ticketFieldRepository.findAllValuesForTicketId(v.getTicket().getId()))))
            .map(p -> {
                DetailedScanData data = p.getLeft();
                Map<String, String> descriptions = p.getRight();
                return Pair.of(data, fields.stream().map(x -> descriptions.getOrDefault(x.getName(), "")).collect(toList()));
            }).map(p -> {
            List<String> line = new ArrayList<>();
            Ticket ticket = p.getLeft().getTicket();
            SponsorScan sponsorScan = p.getLeft().getSponsorScan();
            User user = userManager.findUser(sponsorScan.getUserId());
            line.add(user.getUsername());
            line.add(user.getDescription());
            line.add(sponsorScan.getTimestamp().toString());
            line.add(ticket.getFullName());
            line.add(ticket.getEmail());
            line.addAll(p.getRight());
            return line.toArray(new String[0]);
        });

        if ("excel".equals(format)) {
            exportSponsorScanExcel(eventName, header, sponsorScans, response);
        } else {
            exportSponsorScanCSV(eventName, header, sponsorScans, response);
        }
    }

    private void exportSponsorScanExcel(String eventName, List<String> header, Stream<String[]> sponsorScans,
                                        HttpServletResponse response) throws IOException {

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + eventName + "-sponsor-scan.xlsx");
        try (OutputStream os = response.getOutputStream()) {
            exportExcel(eventName + " sponsor scan", header.toArray(new String[header.size()]), sponsorScans.collect(toList()), os);
        }
    }

    private void exportSponsorScanCSV(String eventName, List<String> header, Stream<String[]> sponsorScans,
                                      HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + eventName + "-sponsor-scan.csv");
        try (ServletOutputStream out = response.getOutputStream(); CSVWriter writer = new CSVWriter(new OutputStreamWriter(out))) {
            for (int marker : BOM_MARKERS) {
                out.write(marker);
            }
            writer.writeNext(header.toArray(new String[header.size()]));
            sponsorScans.forEachOrdered(writer::writeNext);
            writer.flush();
            out.flush();
        }
    }

    @RequestMapping("/events/{eventName}/fields")
    public List<SerializablePair<String, String>> getAllFields(@PathVariable("eventName") String eventName) {
        List<SerializablePair<String, String>> fields = new ArrayList<>();
        fields.addAll(FIXED_FIELDS.stream().map(f -> SerializablePair.of(f, f)).collect(toList()));
        fields.addAll(ticketFieldRepository.findFieldsForEvent(eventName).stream().map(f -> SerializablePair.of(CUSTOM_FIELDS_PREFIX + f, f)).collect(toList()));
        return fields;
    }

    @RequestMapping("/events/{eventName}/additional-field")
    public List<TicketFieldConfigurationAndAllDescriptions> getAllAdditionalField(@PathVariable("eventName") String eventName) {
        final Map<Integer, List<TicketFieldDescription>> descById = ticketFieldRepository.findDescriptions(eventName).stream().collect(Collectors.groupingBy(TicketFieldDescription::getTicketFieldConfigurationId));
        return ticketFieldRepository.findAdditionalFieldsForEvent(eventName).stream()
            .map(field -> new TicketFieldConfigurationAndAllDescriptions(field, descById.getOrDefault(field.getId(), Collections.emptyList())))
            .collect(toList());
    }

    @RequestMapping("/events/{eventName}/additional-field/{id}/stats")
    public List<RestrictedValueStats> getStats(@PathVariable("eventName") String eventName, @PathVariable("id") Integer id, Principal principal) {
        if(!eventManager.getOptionalByName(eventName, principal.getName()).filter(event -> ticketFieldRepository.findById(id).getEventId() == event.getId()).isPresent()) {
            return Collections.emptyList();
        }
        return ticketFieldRepository.retrieveStats(id);
    }

    @RequestMapping(value = "/event/additional-field/templates", method = GET)
    public List<DynamicFieldTemplate> loadTemplates() {
        return dynamicFieldTemplateRepository.loadAll();
    }

    @RequestMapping(value = "/events/{eventName}/additional-field/descriptions", method = POST)
    public void saveAdditionalFieldDescriptions(@RequestBody Map<String, TicketFieldDescriptionModification> descriptions) {
        eventManager.updateTicketFieldDescriptions(descriptions);
    }
    
    @RequestMapping(value = "/events/{eventName}/additional-field/new", method = POST)
    public ValidationResult addAdditionalField(@PathVariable("eventName") String eventName, @RequestBody EventModification.AdditionalField field, Principal principal, Errors errors) {
        Event event = eventManager.getSingleEvent(eventName, principal.getName());
        List<TicketFieldConfiguration> fields = ticketFieldRepository.findAdditionalFieldsForEvent(event.getId());
        return validateAdditionalFields(fields, field, errors).ifSuccess(() -> eventManager.addAdditionalField(event, field));
    }
    
    @RequestMapping(value = "/events/{eventName}/additional-field/swap-position/{id1}/{id2}", method = POST)
    public void swapAdditionalFieldPosition(@PathVariable("eventName") String eventName, @PathVariable("id1") int id1, @PathVariable("id2") int id2, Principal principal) {
    	Event event = eventManager.getSingleEvent(eventName, principal.getName());
    	eventManager.swapAdditionalFieldPosition(event.getId(), id1, id2);
    }

    @PostMapping("/events/{eventName}/additional-field/set-position/{id}")
    public void setAdditionalFieldPosition(@PathVariable("eventName") String eventName,
                                           @PathVariable("id") int id,
                                           @RequestParam("newPosition") int newPosition,
                                           Principal principal) {
        Event event = eventManager.getSingleEvent(eventName, principal.getName());
        eventManager.setAdditionalFieldPosition(event.getId(), id, newPosition);
    }
    
    @RequestMapping(value = "/events/{eventName}/additional-field/{id}", method = DELETE)
    public void deleteAdditionalField(@PathVariable("eventName") String eventName, @PathVariable("id") int id, Principal principal) {
        eventManager.getSingleEvent(eventName, principal.getName());
    	eventManager.deleteAdditionalField(id);
    }

    @RequestMapping(value = "/events/{eventName}/additional-field/{id}", method = POST)
    public void updateAdditionalField(@PathVariable("eventName") String eventName, @PathVariable("id") int id, @RequestBody EventModification.UpdateAdditionalField field, Principal principal) {
        eventManager.getSingleEvent(eventName, principal.getName());
        eventManager.updateAdditionalField(id, field);
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments")
    public List<SerializablePair<TicketReservation, OrderSummary>> getPendingPayments(@PathVariable("eventName") String eventName, Principal principal) {
        Event event = eventManager.getSingleEvent(eventName, principal.getName());
        return ticketReservationManager.getPendingPayments(event).stream()
                .map(SerializablePair::fromPair).collect(toList());
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments-count")
    public Integer getPendingPaymentsCount(@PathVariable("eventName") String eventName, Principal principal) {
        Optional<Event> maybeEvent = eventManager.getOptionalByName(eventName, principal.getName());
        if(maybeEvent.isPresent()) {
            return ticketReservationManager.getPendingPaymentsCount(maybeEvent.get().getId());
        } else {
            return 0;
        }
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments/{reservationId}/confirm", method = POST)
    public String confirmPayment(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal,
                                 Model model, HttpServletRequest request) {
        ticketReservationManager.confirmOfflinePayment(loadEvent(eventName, principal), reservationId, principal.getName());
        ticketReservationManager.findById(reservationId)
            .filter(TicketReservation::isDirectAssignmentRequested)
            .ifPresent(reservation -> ticketHelper.directTicketAssignment(eventName, reservationId, reservation.getEmail(), reservation.getFullName(), reservation.getFirstName(), reservation.getLastName(), reservation.getUserLanguage(), Optional.empty(), request, model));
        return OK;
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments/{reservationId}", method = DELETE)
    public String deletePendingPayment(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        ticketReservationManager.deleteOfflinePayment(loadEvent(eventName, principal), reservationId, false);
        return OK;
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments/bulk-confirmation", method = POST)
    public List<Triple<Boolean, String, String>> bulkConfirmation(@PathVariable("eventName") String eventName,
                                                                  Principal principal,
                                                                  @RequestBody UploadBase64FileModification file) throws IOException {

        try(InputStreamReader isr = new InputStreamReader(file.getInputStream()); CSVReader reader = new CSVReader(isr)) {
            Event event = loadEvent(eventName, principal);
            return reader.readAll().stream()
                    .map(line -> {
                        String reservationID = null;
                        try {
                            Validate.isTrue(line.length >= 2);
                            reservationID = line[0];
                            ticketReservationManager.validateAndConfirmOfflinePayment(reservationID, event, new BigDecimal(line[1]), principal.getName());
                            return Triple.of(Boolean.TRUE, reservationID, "");
                        } catch (Exception e) {
                            return Triple.of(Boolean.FALSE, Optional.ofNullable(reservationID).orElse(""), e.getMessage());
                        }
                    })
                    .collect(toList());
        }
    }

    @RequestMapping(value = "/events/{eventName}/categories/{categoryId}/tickets/{ticketId}/toggle-locking", method = PUT)
    public boolean toggleTicketLocking(@PathVariable("eventName") String eventName,
                                       @PathVariable("categoryId") int categoryId,
                                       @PathVariable("ticketId") int ticketId,
                                       Principal principal) {
        return eventManager.toggleTicketLocking(eventName, categoryId, ticketId, principal.getName());
    }

    @RequestMapping(value = "/events/{eventName}/languages", method = GET)
    public List<ContentLanguage> getAvailableLocales(@PathVariable("eventName") String eventName) {
        return i18nManager.getEventLanguages(eventName);
    }

    @RequestMapping(value = "/events/{eventName}/invoices/count", method = GET)
    public Integer countInvoicesForEvent(@PathVariable("eventName") String eventName, Principal principal) {
        return optionally(() -> eventManager.getSingleEvent(eventName, principal.getName()))
            .map(e -> ticketReservationManager.countInvoices(e.getId()))
            .orElse(0);
    }

    @RequestMapping(value = "/events/{eventName}/all-invoices", method = GET)
    public void getAllInvoices(@PathVariable("eventName") String eventName, HttpServletResponse response, Principal principal) throws  IOException {
        Event event = loadEvent(eventName, principal);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=" + eventName + "-invoices.zip");

        try(OutputStream os = response.getOutputStream(); ZipOutputStream zipOS = new ZipOutputStream(os)) {
            for (TicketReservation reservation : ticketReservationManager.findAllInvoices(event.getId())) {
                OrderSummary orderSummary = Json.fromJson(reservation.getInvoiceModel(), OrderSummary.class);
                Optional<String> vat = Optional.ofNullable(orderSummary.getVatPercentage());
                Map<String, Object> reservationModel = ticketReservationManager.prepareModelForReservationEmail(event, reservation, vat, orderSummary);
                Optional<byte[]> pdf = TemplateProcessor.buildInvoicePdf(event, fileUploadManager, new Locale(reservation.getUserLanguage()), templateManager, reservationModel);

                if(pdf.isPresent()) {
                    zipOS.putNextEntry(new ZipEntry("invoice-" + eventName + "-id-" + reservation.getId() + "-invoice-nr-" + reservation.getInvoiceNumber() + ".pdf"));
                    StreamUtils.copy(pdf.get(), zipOS);
                }
            }
        }
    }

    @RequestMapping(value = "/events-all-languages", method = GET)
    public List<ContentLanguage> getAllLanguages() {
        return i18nManager.getAvailableLanguages();
    }

    @RequestMapping(value = "/events-supported-languages", method = GET)
    public List<ContentLanguage> getSupportedLanguages() {
        return i18nManager.getSupportedLanguages();
    }

    @RequestMapping(value = "/events/{eventName}/category/{categoryId}/ticket", method = GET)
    public PageAndContent<List<TicketWithStatistic>> getTicketsInCategory(@PathVariable("eventName") String eventName, @PathVariable("categoryId") int categoryId,
                                                                          @RequestParam(value = "page", required = false) Integer page,
                                                                          @RequestParam(value = "search", required = false) String search,
                                                                          Principal principal) {
        Event event = loadEvent(eventName, principal);
        return new PageAndContent<>(eventStatisticsManager.loadModifiedTickets(event.getId(), categoryId, page == null ? 0 : page, search), eventStatisticsManager.countModifiedTicket(event.getId(), categoryId, search));
    }

    @RequestMapping(value = "/events/{eventName}/ticket-sold-statistics", method = GET)
    public TicketsStatistics getTicketsStatistics(@PathVariable("eventName") String eventName, @RequestParam(value = "from", required = false) String f, @RequestParam(value = "to", required = false) String t, Principal principal) throws ParseException {
        Event event = loadEvent(eventName, principal);
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        //TODO: cleanup
        Date from = DateUtils.truncate(f == null ? new Date(0) : format.parse(f), Calendar.HOUR);
        Date to = DateUtils.addMilliseconds(DateUtils.ceiling(t == null ? new Date() : format.parse(t), Calendar.DATE), -1);
        //

        int eventId = event.getId();
        return new TicketsStatistics(eventStatisticsManager.getTicketSoldStatistics(eventId, from, to), eventStatisticsManager.getTicketReservedStatistics(eventId, from, to));
    }

    private Event loadEvent(String eventName, Principal principal) {
        Optional<Event> singleEvent = optionally(() -> eventManager.getSingleEvent(eventName, principal.getName()));
        Validate.isTrue(singleEvent.isPresent(), "event not found");
        return singleEvent.get();
    }

    @Data
    static class TicketsStatistics {
        private final List<TicketsByDateStatistic> sold;
        private final List<TicketsByDateStatistic> reserved;
    }

}

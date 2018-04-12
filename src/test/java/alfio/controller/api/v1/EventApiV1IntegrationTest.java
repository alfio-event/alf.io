package alfio.controller.api.v1;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.config.RepositoryConfiguration;
import alfio.controller.ReservationFlowIntegrationTest;
import alfio.controller.api.v1.admin.EventApiV1Controller;
import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.api.v1.admin.EventCreationRequest;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static alfio.test.util.IntegrationTestUtil.initSystemProperties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {RepositoryConfiguration.class, DataSourceConfiguration.class, TestConfiguration.class, ReservationFlowIntegrationTest.ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class EventApiV1IntegrationTest {

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
    }

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private UserManager userManager;

    @Autowired
    private OrganizationRepository organizationRepository;


    @Autowired
    private EventApiV1Controller controller;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Before
    public void ensureConfiguration() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
    }





    @Test
    public void createTest() throws URISyntaxException, IOException {
        EventCreationRequest eventCreationRequest = new EventCreationRequest(
            "Title",
            "title",
            Arrays.asList(new EventCreationRequest.DescriptionRequest("en","desc")),
            new EventCreationRequest.LocationRequest(
                "Pollegio 6742 Switzerland",
                new EventCreationRequest.CoordinateRequest("45.5","9.00")
            ),
            "Europe/Zurich",
            LocalDateTime.of(2020,1,10,12,00),
            LocalDateTime.of(2020,1,10,18,00),
            "https://alf.io",
            "https://alf.io",
            "https://avatars3.githubusercontent.com/u/34451076",
            new EventCreationRequest.TicketRequest(
                false,
                10,
                "CHF",
                new BigDecimal("7.7"),
                true,
                Arrays.asList(PaymentProxy.OFFLINE,PaymentProxy.STRIPE),
                Arrays.asList(
                    new EventCreationRequest.CategoryRequest(
                        "standard",
                        Arrays.asList(new EventCreationRequest.DescriptionRequest("en","desc")),
                        10,
                        false,
                        BigDecimal.TEN,
                        LocalDateTime.of(2019,1,10,12,00),
                        LocalDateTime.of(2019,1,30,18,00),
                        null,
                        null
                    )
                ),
                null
            )
        );

        String organizationName = UUID.randomUUID().toString();
        String username = UUID.randomUUID().toString();

        userManager.createOrganization(organizationName, "org", "email@example.com");
        Organization organization = organizationRepository.findByName(organizationName).get();
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com", Role.API_CONSUMER, User.Type.INTERNAL);

        Principal mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn(username);
        String shortName = controller.create(eventCreationRequest,mockPrincipal).getBody();

        Event event = eventManager.getSingleEvent(shortName,username);
        List<TicketCategory> tickets = ticketCategoryRepository.findByEventId(event.getId());



        assertEquals(eventCreationRequest.getTitle(),event.getDisplayName());
        assertEquals(eventCreationRequest.getSlug(),event.getShortName());
        assertEquals(eventCreationRequest.getTickets().getCurrency(),event.getCurrency());
        assertEquals(eventCreationRequest.getWebsiteUrl(),event.getWebsiteUrl());
        assertEquals(eventCreationRequest.getTickets().getPaymentsMethods(),event.getAllowedPaymentProxies());
        assertTrue(event.getFileBlobIdIsPresent());
        assertEquals(eventCreationRequest.getTickets().getCategories().size(),tickets.size());
        tickets.forEach((t) -> {
                List<EventCreationRequest.CategoryRequest> requestCategories = eventCreationRequest.getTickets().getCategories().stream().filter((rt) -> rt.getName().equals(t.getName())).collect(Collectors.toList());
                assertEquals(1,requestCategories.size());
                requestCategories.forEach((rtc) -> {
                        assertEquals(t.getMaxTickets(), rtc.getMaxTickets().intValue());
                        assertTrue(t.getPrice().compareTo(rtc.getPrice()) == 0);
                    }
                );
            }
        );

    }


}

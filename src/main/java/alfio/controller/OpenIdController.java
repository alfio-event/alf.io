package alfio.controller;

import alfio.manager.*;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.security.Principal;
import java.util.Map;

@Profile("auth0")
@Controller
@AllArgsConstructor
public class OpenIdController
{
    private final OpenIdAuthenticationManager openIdAuthenticationManager;

    @RequestMapping(value = "/callback", method = RequestMethod.GET)
    protected void getCallback(@RequestParam(value="code") String code,
                               Model model,
                               Principal principal,
                               HttpServletRequest request,
                               HttpServletResponse response) throws ServletException, IOException, InterruptedException
    {
        auth0CallbackFunction(code, model, principal, request, response);
    }

    @RequestMapping(value = "/callback", method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    protected void postCallback(@RequestParam(value="code") String code,
                                Model model,
                                Principal principal,
                                HttpServletRequest request,
                                HttpServletResponse response) throws ServletException, IOException, InterruptedException
    {
        auth0CallbackFunction(code, model, principal, request, response);
    }

    public void auth0CallbackFunction(String code,
                                      Model model,
                                      Principal principal,
                                      HttpServletRequest request,
                                      HttpServletResponse response) throws IOException, InterruptedException
    {
        String claimsUrl = openIdAuthenticationManager.buildClaimsRetrieverUrl();
        String body = openIdAuthenticationManager.buildRetrieveClaimsUrlBody(code);
        Map<String, Claim> claims = retrieveClaims(claimsUrl, body);

        String emailRedirect = "?email=" + claims.get("email").asString();
        response.setHeader("Location", "/authenticationAuth0" + emailRedirect);
        response.setStatus(302);
    }

    private Map<String, Claim> retrieveClaims(String claimsUrl, String body) throws IOException, InterruptedException
    {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(claimsUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        Map<String, Object> results = new ObjectMapper().readValue(response.body(), Map.class);
        String idToken = (String) results.get("id_token");

        return JWT.decode(idToken).getClaims();
    }

}
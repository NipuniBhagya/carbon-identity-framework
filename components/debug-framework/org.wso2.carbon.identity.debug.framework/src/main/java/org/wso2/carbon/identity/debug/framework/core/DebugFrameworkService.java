package org.wso2.carbon.identity.debug.framework.core;

import com.example.wso2.debug.model.DebugResult;
import org.mockito.Mockito;
import org.wso2.carbon.identity.application.authentication.framework.FederatedApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.AuthenticationFailedException;
import org.wso2.carbon.identity.application.common.model.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.idp.mgt.IdentityProviderManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class DebugFrameworkService {

    public String initiateDebugRequest(String idpName, String sessionId) throws Exception {

        IdentityProvider idp = IdentityProviderManager.getInstance().getIdPByName(idpName, "carbon.super");
        FederatedAuthenticatorConfig federatedAuthenticatorConfig = getEnabledFederatedAuthenticator(idp);
        FederatedApplicationAuthenticator authenticator = (FederatedApplicationAuthenticator) Class.forName(federatedAuthenticatorConfig.getName()).newInstance();

        AuthenticationContext context = new AuthenticationContext();
        context.setExternalIdP(idp);
        context.setAuthenticatorProperties(getAuthenticatorPropertiesAsMap(federatedAuthenticatorConfig));

        // Use a mock response to capture the redirect URL without actually redirecting
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        final String redirectUrl = new String[1];
        Mockito.doAnswer(invocation -> {
            redirectUrl = invocation.getArgument(0);
            return null;
        }).when(mockResponse).sendRedirect(Mockito.anyString());

        // Embed our debug session ID into the state parameter for tracking on the callback
        String state = "debugSessionId=" + sessionId;
        context.setContextIdentifier(state);

        // Directly invoke the authenticator's initiation method
        authenticator.initiateAuthenticationRequest(null, mockResponse, context);

        return redirectUrl;
    }

    public void handleDebugResponse(HttpServletRequest request, HttpServletResponse response) {
        String state = request.getParameter("state");
        String sessionId = state.substring(state.indexOf("=") + 1);

        DebugResult result = DebugResultCache.getInstance().get(sessionId);

        try {
            IdentityProvider idp = IdentityProviderManager.getInstance().getIdPByName(result.getIdpName(), "carbon.super");
            FederatedAuthenticatorConfig federatedAuthenticatorConfig = getEnabledFederatedAuthenticator(idp);
            FederatedApplicationAuthenticator authenticator = (FederatedApplicationAuthenticator) Class.forName(federatedAuthenticatorConfig.getName()).newInstance();

            AuthenticationContext context = new AuthenticationContext();
            context.setExternalIdP(idp);
            context.setAuthenticatorProperties(getAuthenticatorPropertiesAsMap(federatedAuthenticatorConfig));

            // Directly invoke the authenticator's response processing method, bypassing the framework
            authenticator.processAuthenticationResponse(request, response, context);

            // Populate results from the processed context
            result.setAuthenticationSuccess(context.isRequestAuthenticated());
            if (context.getSubject()!= null) {
                result.setAuthenticatedSubject(context.getSubject().getAuthenticatedSubjectIdentifier());
                result.setMappedClaims(context.getSubject().getSubjectAttributes());
            }

            result.setComplete(true);
            response.getWriter().write("<html><body><h2>Debug Test Complete</h2><p>You can now close this window.</p></body></html>");

        } catch (Exception e) {
            result.setAuthenticationSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setComplete(true);
        }
    }

    // Helper methods
    private FederatedAuthenticatorConfig getEnabledFederatedAuthenticator(IdentityProvider idp) throws AuthenticationFailedException {
        return Arrays.stream(idp.getFederatedAuthenticatorConfigs())
                .filter(FederatedAuthenticatorConfig::isEnabled)
                .findFirst()
                .orElseThrow(() -> new AuthenticationFailedException("No enabled federated authenticator found for IdP: " + idp.getIdentityProviderName()));
    }

    private Map<String, String> getAuthenticatorPropertiesAsMap(FederatedAuthenticatorConfig config) {
        return Arrays.stream(config.getProperties())
                .collect(Collectors.toMap(Property::getName, Property::getValue));
    }
}

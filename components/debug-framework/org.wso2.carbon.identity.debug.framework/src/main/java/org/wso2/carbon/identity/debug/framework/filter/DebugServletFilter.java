package org.wso2.carbon.identity.debug.framework.filter;

import com.example.wso2.debug.core.DebugFrameworkService;
import com.example.wso2.debug.internal.DebugFrameworkServiceComponent;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class DebugServletFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // OIDC/SAML 'state' parameter is used to carry our debug session identifier
        String state = httpRequest.getParameter("state");

        // Check if the state contains our specific debug marker
        if (state!= null && state.contains("debugSessionId=")) {
            // This is a debug flow callback. Intercept and divert to our service.
            DebugFrameworkService debugService = DebugFrameworkServiceComponent.getDebugFrameworkService();
            if (debugService!= null) {
                debugService.handleDebugResponse(httpRequest, (HttpServletResponse) response);
            } else {
                response.getWriter().write("Error: Debug Framework Service is not available.");
            }
        } else {
            // This is a normal user request, so let it proceed through the standard framework.
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }
}
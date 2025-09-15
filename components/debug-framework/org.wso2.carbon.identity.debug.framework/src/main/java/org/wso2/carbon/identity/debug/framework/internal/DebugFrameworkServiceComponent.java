package org.wso2.carbon.identity.debug.framework.internal;

import com.example.wso2.debug.core.DebugFrameworkService;
import com.example.wso2.debug.filter.DebugServletFilter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.http.HttpService;

@Component(name = "com.example.wso2.debug.framework.component", immediate = true)
public class DebugFrameworkServiceComponent {

    private static HttpService httpService;
    private static final DebugFrameworkService debugFrameworkService = new DebugFrameworkService();

    @Activate
    protected void activate(ComponentContext context) {
        try {
            // Register the servlet filter to intercept /commonauth for debug flows
            if (httpService!= null) {
                httpService.registerFilter("/commonauth", new DebugServletFilter(), null, null);
            }
            // The JAX-RS service (DebugResource) is registered via other mechanisms (e.g., CXF Whiteboard)
        } catch (Exception e) {
            // Log error
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        // Unregister filter if needed
    }

    @Reference(
            name = "osgi.httpservice",
            service = HttpService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetHttpService"
    )
    protected void setHttpService(HttpService service) {
        httpService = service;
    }

    protected void unsetHttpService(HttpService service) {
        httpService = null;
    }

    public static DebugFrameworkService getDebugFrameworkService() {
        return debugFrameworkService;
    }
}
package com.cloudbees.vietnam4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class ProxyServlet extends HttpServlet {
    private ProxiedWebApplication webApp;
    
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ProxiedWebApplication webApp = getProxyWebApplication();
        webApp.handleRequest(req, resp);
    }

    private synchronized ProxiedWebApplication getProxyWebApplication() {
        if (webApp==null)
            webApp = new ProxiedWebApplication(new File("/home/kohsuke/ws/jenkins/jenkins/war/target/jenkins"),
                    getServletContext().getContextPath());
        return null;
    }
}

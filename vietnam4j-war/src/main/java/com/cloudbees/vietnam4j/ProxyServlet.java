package com.cloudbees.vietnam4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    @Override
    public void destroy() {
        try {
            webApp.stop();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to shutdown "+webApp,e);
        }
    }

    private synchronized ProxiedWebApplication getProxyWebApplication() throws ServletException {
        if (webApp==null) {
            System.setProperty("JENKINS_HOME","/tmp/throwaway");
            webApp = new ProxiedWebApplication(new File("/home/kohsuke/ws/jenkins/jenkins/war/target/jenkins"),
                    getServletContext().getContextPath());
            try {
                webApp.start();
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
        return webApp;
    }

    private static final Logger LOGGER = Logger.getLogger(ProxyServlet.class.getName());
}

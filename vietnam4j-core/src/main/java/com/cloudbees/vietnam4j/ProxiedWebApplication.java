package com.cloudbees.vietnam4j;

import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.LocalConnector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.webapp.WebAppContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

import static org.mortbay.jetty.Handler.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class ProxiedWebApplication {
    private final File war;
    private final String contextPath;
    private Server server;
    private WebAppContext webApp;

    public ProxiedWebApplication(File war, String contextPath) {
        this.war = war;
        this.contextPath = contextPath;
    }
    
    public void start() throws Exception {
        server = new Server();
        webApp = new WebAppContext(war.getPath(),contextPath);
        server.setHandler(webApp);
        server.start();
    }

    public void stop() throws Exception {
        if (server!=null)
            server.stop();
        server=null;
    }

    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        // needed to fake Jetty
        HttpConnection2 hc = new HttpConnection2();
        HttpConnection old = hc.set(hc);

        try {
            webApp.getServletHandler().handle("/", request, response, REQUEST);
        } finally {
            hc.set(old);
        }
    }

    private class HttpConnection2 extends HttpConnection {
        HttpConnection2() {
            super(new LocalConnector(),null,server);
        }

        public HttpConnection set(HttpConnection v) {
            HttpConnection old = HttpConnection.getCurrentConnection();
            HttpConnection.setCurrentConnection(v);
            return old;
        }
    }
}

/*
 * Copyright 2010-2011, CloudBees Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudbees.vietnam4j;

import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.LocalConnector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.webapp.WebAppContext;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

import static org.mortbay.jetty.Handler.*;

/**
 * Encapsulates a proxy web application.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProxiedWebApplication {
    private final File war;
    private final String contextPath;
    private Server server;
    private WebAppContext webApp;

    /**
     * Creates a proxied web application.
     * 
     * @param war
     *      Either a web application archive or an exploded web application.
     * @param contextPath
     *      {@linkplain ServletContext#getContextPath() context path} of the deployed web application.
     */
    public ProxiedWebApplication(File war, String contextPath) {
        this.war = war;
        this.contextPath = contextPath;
    }

    /**
     * Starts the proxied web application.
     */
    public void start() throws Exception {
        server = new Server();
        webApp = new WebAppContext(war.getPath(),contextPath);
        server.setHandler(webApp);
        server.start();
    }

    /**
     * Stops the proxied web application.
     */
    public void stop() throws Exception {
        if (server!=null)
            server.stop();
        server=null;
    }

    /**
     * Dispatches a request.
     */
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        // needed to fake Jetty
        HttpConnection2 hc = new HttpConnection2();
        HttpConnection old = hc.set(hc);

        request = new HttpServletRequestWrapper(request) {
            public ServletContext getServletContext() {
                return webApp.getServletContext();
            }
        };

        ClassLoader oldCCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(webApp.getClassLoader());
        try {
            webApp.getServletHandler().handle("/", request, response, REQUEST);
        } finally {
            hc.set(old);
            Thread.currentThread().setContextClassLoader(oldCCL);
        }
    }

    /**
     * A hack to work around the restrictive access of {@link HttpConnection#setCurrentConnection(HttpConnection)}
     */
    private class HttpConnection2 extends HttpConnection {
        HttpConnection2() {
            // some random value just to avoid NPE
            super(new LocalConnector(),null,server);
        }

        public HttpConnection set(HttpConnection v) {
            HttpConnection old = HttpConnection.getCurrentConnection();
            HttpConnection.setCurrentConnection(v);
            return old;
        }
    }

    @Override
    public String toString() {
        return super.toString()+"["+war+"]";
    }
}

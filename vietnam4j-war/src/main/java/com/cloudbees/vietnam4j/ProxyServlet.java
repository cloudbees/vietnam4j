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

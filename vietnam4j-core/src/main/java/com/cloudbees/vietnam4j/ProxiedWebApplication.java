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
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.webapp.WebAppClassLoader;
import org.mortbay.jetty.webapp.WebAppContext;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.mortbay.jetty.Handler.*;

/**
 * Encapsulates a proxy web application.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProxiedWebApplication {
    private final String war;
    private final String contextPath;
    private Server server;
    private final WebAppContext webApp;
    private Object requestListeners;    // came from webApp._requestListeners

    private ClassLoader parentClassLoader;

    /**
     * Calls to {@link #addClassPath(File)} prior to {@link #start()}
     */
    private List<String> classPaths = new ArrayList<String>();


    /**
     * Creates a proxied web application.
     * 
     * @param war
     *      Either a web application archive or an exploded web application.
     * @param contextPath
     *      {@linkplain ServletContext#getContextPath() context path} of the deployed web application.
     */
    public ProxiedWebApplication(File war, String contextPath) {
        this.war = war.getPath();
        this.contextPath = contextPath;
        webApp = new WebAppContext(this.war,contextPath);
    }

    public ProxiedWebApplication(URL war, String contextPath) {
        this.war = war.toExternalForm();
        this.contextPath = contextPath;
        webApp = new WebAppContext(this.war,contextPath);
    }

    public String getContextPath() {
        return contextPath;
    }

    public ClassLoader getParentClassLoader() {
        return parentClassLoader;
    }

    /**
     * If set, web application will see this classloader as the parent classloader.
     * This needs to be able to see the same servlet API that vietnam4j itself uses,
     * but often masking other classes is useful to isolate the proxied webapp to
     * interfere with the caller's classes.
     *
     * The common idiom when used inside a servlet container is to pass in
     * {@code HttpServletRequest.class.getClassLoader()}.
     */
    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }

    /**
     * Returns the classloader that sees all the classes in the proxied webapp.
     *
     * Can be called only after the {@link #start()} method
     */
    public ClassLoader getWebAppClassLoader() {
        if (!webApp.isStarted())
            throw new IllegalStateException();
        return webApp.getClassLoader();
    }

    /**
     * If set to true, the webapp classloader will delegate to the parent first before attempting
     * to resolve the class by itself.
     *
     * The default is false, which is what the servlet spec specifies.
     */
    public void setParentLoaderHasPriority(boolean v) {
        webApp.setParentLoaderPriority(v);
    }

    /**
     * Call this method before {@link #start()}, and you set additional classpath for the webapp classloader.
     * These classpath elements are inserted before the contents of the war file.
     *
     * This lets you override some of what's in the war file with your own classpath elements.
     */
    public void addClassPath(URL url) throws IOException {
        if (webApp.isStarted())
            throw new IllegalStateException();
        classPaths.add(url.toExternalForm());
    }

    public void addClassPath(File f) throws IOException {
        addClassPath(f.toURI().toURL());
    }

    /**
     * Starts the proxied web application.
     */
    public void start() throws Exception {
        server = new Server();

        WebAppClassLoader cl = new WebAppClassLoader(parentClassLoader, webApp);
        for (String path : classPaths)
            cl.addClassPath(path);
        classPaths.clear();
        webApp.setClassLoader(cl);

        try {
            Class.forName("com.cloudbees.vietnam4j.mortbay.jetty.webapp.WebAppContext");
            webApp.setDefaultsDescriptor(ProxiedWebApplication.class.getResource("webdefault.xml").toExternalForm());
        } catch (ClassNotFoundException e) {
            // package not renamed, proceed without custom default XML
        }

        server.setHandler(webApp);
        server.start();

        // pry in and grab request listeners
        Field f = ContextHandler.class.getDeclaredField("_requestListeners");
        f.setAccessible(true);
        requestListeners = f.get(webApp);
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
     * Gets the servlet context of the proxied web app, after {@link #start} has been called.
     * @since 1.2
     */
    public ServletContext getProxiedServletContext() {
        return webApp.getServletContext();
    }

    /**
     * Dispatches a request.
     */
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        // needed to fake Jetty
        final HttpConnection2 hc = new HttpConnection2();
        HttpConnection old = hc.set(hc);
        hc.getRequest().setRequestListeners(requestListeners);

        request = new ProxiedRequest(this, request, hc);

        ClassLoader oldCCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(webApp.getClassLoader());
        try {
            webApp.getServletHandler().handle(request.getRequestURI().substring(contextPath.length()), request, response, REQUEST);
        } finally {
            hc.set(old);
            Thread.currentThread().setContextClassLoader(oldCCL);
        }
    }

    /**
     * Gets the {@link HttpSession} used by the proxied web application.
     *
     * @param base
     *      The outer session. The session used by the proxied web application is scoped by this session.
     */
    public HttpSession getProxiedSession(HttpSession base) {
        String id = ProxiedSession.class.getName()+getContextPath();
        HttpSession nested = (HttpSession)base.getAttribute(id);
        if (nested==null)
            base.setAttribute(id,nested=new ProxiedSession(this,base));
        return nested;
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

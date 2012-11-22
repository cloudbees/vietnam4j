package com.cloudbees.vietnam4j;

import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.Enumeration;

/**
 * {@link HttpServletRequest} that gets passed to the proxied webapp.
 *
 * <p>
 * Notable proxying work.
 *
 * <ul>
 *      <li>
 *          Create a separate attribute space so that the attributes in the caller's request and callee's request won't get mixed up.
 * </ul>
 * @author Kohsuke Kawaguchi
 */
class ProxiedRequest extends HttpServletRequestWrapper {
    private Request req;
    private ProxiedWebApplication webApp;

    ProxiedRequest(ProxiedWebApplication webApp, HttpServletRequest request, HttpConnection hc) {
        super(request);
        this.webApp = webApp;
        // ServletHandler.handle computes various properties and updates them in hc.getRequest() object, so
        // we'll delegate to those
        this.req = hc.getRequest();
    }

    /* @Override */
    public ServletContext getServletContext() {
        return webApp.getProxiedServletContext();
    }

    @Override
    public String getContextPath() {
        return webApp.getContextPath();
    }

    @Override
    public String getServletPath() {
        return req.getServletPath();
    }

    @Override
    public String getPathInfo() {
        return req.getPathInfo();
    }

    /* @Override */
    public String getServletName() {
        return req.getServletName();
    }

    @Override
    public String getPathTranslated() {
        return req.getPathTranslated();
    }

    @Override
    public Enumeration getAttributeNames() {
        return req.getAttributeNames();
    }

    @Override
    public Object getAttribute(String name) {
        return req.getAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object o) {
        req.setAttribute(name, o);
    }

    @Override
    public void removeAttribute(String name) {
        req.removeAttribute(name);
    }

    @Override
    public boolean isUserInRole(String role) {
        return req.isUserInRole(role);
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return req.getRequestDispatcher(path);
    }

    @Override
    public String getRealPath(String path) {
        return req.getRealPath(path);
    }

    // TODO: session should be isolated from the main session
}

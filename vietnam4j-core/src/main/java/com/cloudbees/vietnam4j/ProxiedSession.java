package com.cloudbees.vietnam4j;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
class ProxiedSession implements HttpSession {
    /**
     * Session of the caller.
     */
    private final HttpSession base;

    private final ProxiedWebApplication webApp;

    private final Map attributes = new HashMap();

    ProxiedSession(ProxiedWebApplication webApp, HttpSession base) {
        this.base = base;
        this.webApp = webApp;
    }

    public synchronized Object getAttribute(String name) {
        return attributes.get(name);
    }

    public synchronized Enumeration getAttributeNames() {
        return Collections.enumeration(new ArrayList(attributes.keySet()));
    }

    public long getCreationTime() {
        return base.getCreationTime();
    }

    public String getId() {
        return base.getId();
    }

    public long getLastAccessedTime() {
        return base.getLastAccessedTime();
    }

    public int getMaxInactiveInterval() {
        return base.getMaxInactiveInterval();
    }

    public ServletContext getServletContext() {
        return webApp.getProxiedServletContext();
    }

    public HttpSessionContext getSessionContext() {
        // no way to support this
        return base.getSessionContext();
    }

    public Object getValue(String name) {
        return getAttribute(name);
    }

    public synchronized String[] getValueNames() {
        return (String[])attributes.keySet().toArray(new String[attributes.size()]);
    }

    public void invalidate() {
        webApp.invalidateProxiedSession(this,base);
    }

    public boolean isNew() {
        return base.isNew();
    }

    public void putValue(String name, Object value) {
        setAttribute(name, value);
    }

    public synchronized void removeAttribute(String name) {
        attributes.remove(name);
    }

    public void removeValue(String name) {
        removeAttribute(name);
    }

    public synchronized void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public void setMaxInactiveInterval(int interval) {
        base.setMaxInactiveInterval(interval);
    }
}

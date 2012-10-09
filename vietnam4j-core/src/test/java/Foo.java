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
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.LocalConnector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.webapp.WebAppContext;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Vector;

import static org.mockito.Mockito.*;
import static org.mortbay.jetty.Handler.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class Foo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalStateException("Usage: … /path/to/jenkins/war/target/jenkins");
        }
        final Server server = new Server();
        System.setProperty("JENKINS_HOME","/tmp/throwaway");
        WebAppContext webApp = new WebAppContext(args[0],"/");
        server.setHandler(webApp);
        server.start();
//        webApp.setServletHandler(new MyServletHandler());

        class HttpConnection2 extends HttpConnection {
            HttpConnection2() {
                super(new LocalConnector(),null,server);
            }

            public void setCurrent() {
                HttpConnection.setCurrentConnection(this);
            }
        }

        new HttpConnection2().setCurrent();

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/");
        when(request.getContextPath()).thenReturn("");
        when(request.getServletPath()).thenReturn("/");
        when(request.getAttributeNames()).thenReturn(new Vector().elements());
        when(response.getOutputStream()).thenReturn(new MyServletOutputStream());

        webApp.getServletHandler().handle("/", request, response, REQUEST);
    }

    static class MyServletOutputStream extends ServletOutputStream {
        @Override
        public void write(int b) throws IOException {
            System.out.write(b);
        }
    }

/*
    private static class MyServletHandler extends ServletHandler {
        @Override
        public void handle(String target, HttpServletRequest request, HttpServletResponse response, int type) throws IOException, ServletException {
            if (!isStarted())
                return;

            // Get the base requests
            final Request base_request=null;
            final String old_servlet_name=base_request.getServletName();
            final String old_servlet_path=base_request.getServletPath();
            final String old_path_info=base_request.getPathInfo();
            final Map old_role_map=base_request.getRoleMap();
            Object request_listeners=null;
            ServletRequestEvent request_event=null;

            try
            {
                ServletHolder servlet_holder=null;
                FilterChain chain=null;

                // Look for the servlet by path
                PathMap.Entry entry=getHolderEntry(target);
                if (entry!=null)
                {
                    servlet_holder=(ServletHolder)entry.getValue();
                    base_request.setServletName(servlet_holder.getName());
                    base_request.setRoleMap(servlet_holder.getRoleMap());
                    if(Log.isDebugEnabled())Log.debug("servlet="+servlet_holder);

                    String servlet_path_spec=(String)entry.getKey();
                    String servlet_path=entry.getMapped()!=null?entry.getMapped():PathMap.pathMatch(servlet_path_spec,target);
                    String path_info=PathMap.pathInfo(servlet_path_spec,target);

                    if (type==INCLUDE)
                    {
                        base_request.setAttribute(Dispatcher.__INCLUDE_SERVLET_PATH,servlet_path);
                        base_request.setAttribute(Dispatcher.__INCLUDE_PATH_INFO, path_info);
                    }
                    else
                    {
                        base_request.setServletPath(servlet_path);
                        base_request.setPathInfo(path_info);
                    }

                    if (servlet_holder!=null && _filterMappings!=null && _filterMappings.length>0)
                        chain=getFilterChain(type, target, servlet_holder);
                }

                // Handle context listeners
                request_listeners = base_request.takeRequestListeners();
                if (request_listeners!=null)
                {
                    request_event = new ServletRequestEvent(getServletContext(),request);
                    final int s= LazyList.size(request_listeners);
                    for(int i=0;i<s;i++)
                    {
                        final ServletRequestListener listener = (ServletRequestListener)LazyList.get(request_listeners,i);
                        listener.requestInitialized(request_event);
                    }
                }

                // Do the filter/handling thang
                if (servlet_holder!=null)
                {
                    base_request.setHandled(true);
                    if (chain!=null)
                        chain.doFilter(request, response);
                    else
                        servlet_holder.handle(request,response);
                }
                else
                    notFound(request, response);
            }
            catch(RetryRequest e)
            {
                base_request.setHandled(false);
                throw e;
            }
            catch(EofException e)
            {
                throw e;
            }
            catch(RuntimeIOException e)
            {
                throw e;
            }
            catch(Exception e)
            {
                if (type!=REQUEST)
                {
                    if (e instanceof IOException)
                        throw (IOException)e;
                    if (e instanceof RuntimeException)
                        throw (RuntimeException)e;
                    if (e instanceof ServletException)
                        throw (ServletException)e;
                }


                // unwrap cause
                Throwable th=e;
                if (th instanceof UnavailableException)
                {
                    Log.debug(th);
                }
                else if (th instanceof ServletException)
                {
                    Log.debug(th);
                    Throwable cause=((ServletException)th).getRootCause();
                    if (cause!=th && cause!=null)
                        th=cause;
                }

                // hnndle or log exception
                if (th instanceof RetryRequest)
                {
                    base_request.setHandled(false);
                    throw (RetryRequest)th;
                }
                else if (th instanceof HttpException)
                    throw (HttpException)th;
                else if (th instanceof RuntimeIOException)
                    throw (RuntimeIOException)th;
                else if (th instanceof EofException)
                    throw (EofException)th;
                else if (Log.isDebugEnabled())
                {
                    Log.warn(request.getRequestURI(), th);
                    Log.debug(request.toString());
                }
                else if (th instanceof IOException || th instanceof UnavailableException)
                {
                    Log.warn(request.getRequestURI()+": "+th);
                }
                else
                {
                    Log.warn(request.getRequestURI(),th);
                }

                // TODO httpResponse.getHttpConnection().forceClose();
                if (!response.isCommitted())
                {
                    request.setAttribute(ServletHandler.__J_S_ERROR_EXCEPTION_TYPE,th.getClass());
                    request.setAttribute(ServletHandler.__J_S_ERROR_EXCEPTION,th);
                    if (th instanceof UnavailableException)
                    {
                        UnavailableException ue = (UnavailableException)th;
                        if (ue.isPermanent())
                            response.sendError(HttpServletResponse.SC_NOT_FOUND,th.getMessage());
                        else
                            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,th.getMessage());
                    }
                    else
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,th.getMessage());
                }
                else
                    if(Log.isDebugEnabled())Log.debug("Response already committed for handling "+th);
            }
            catch(Error e)
            {
                if (type!=REQUEST)
                    throw e;
                Log.warn("Error for "+request.getRequestURI(),e);
                if(Log.isDebugEnabled())Log.debug(request.toString());

                // TODO httpResponse.getHttpConnection().forceClose();
                if (!response.isCommitted())
                {
                    request.setAttribute(ServletHandler.__J_S_ERROR_EXCEPTION_TYPE,e.getClass());
                    request.setAttribute(ServletHandler.__J_S_ERROR_EXCEPTION,e);
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,e.getMessage());
                }
                else
                    if(Log.isDebugEnabled())Log.debug("Response already committed for handling ",e);
            }
            finally
            {
                if (request_listeners!=null)
                {
                    for(int i=LazyList.size(request_listeners);i-->0;)
                    {
                        final ServletRequestListener listener = (ServletRequestListener)LazyList.get(request_listeners,i);
                        listener.requestDestroyed(request_event);
                    }
                }

                base_request.setServletName(old_servlet_name);
                base_request.setRoleMap(old_role_map);
                if (type!=INCLUDE)
                {
                    base_request.setServletPath(old_servlet_path);
                    base_request.setPathInfo(old_path_info);
                }
            }
            return;
        }
    }
    */
}

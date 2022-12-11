/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.myfaces.trinidad.webapp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Reader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.faces.FacesException;
import javax.faces.FactoryFinder;
import javax.faces.application.ProjectStage;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.FacesContextFactory;
import javax.faces.event.PhaseListener;
import javax.faces.lifecycle.Lifecycle;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.myfaces.trinidad.config.Configurator;
import org.apache.myfaces.trinidad.logging.TrinidadLogger;
import org.apache.myfaces.trinidad.resource.CachingResourceLoader;
import org.apache.myfaces.trinidad.resource.DirectoryResourceLoader;
import org.apache.myfaces.trinidad.resource.ResourceLoader;
import org.apache.myfaces.trinidad.resource.ServletContextResourceLoader;
import org.apache.myfaces.trinidad.util.URLUtils;

/**
 * A Servlet which serves up web application resources (images, style sheets,
 * JavaScript libraries) by delegating to a ResourceLoader.
 *
 * The servlet path at which this servlet is registered is used to lookup the
 * class name of the resource loader implementation.
 * For example, if this servlet is registered with name "resources" and
 * URL pattern "/images/*", then its servlet path is "/images".  This is used
 * to construct the class loader lookup for the text file
 * "/META-INF/servlets/resources/images.resources" which contains a single line entry
 * with the class name of the resource loader to use.  This technique is very
 * similar to "/META-INF/services" lookup that allows the implementation object
 * to implement an interface in the public API and be used by the public API
 * but reside in a private implementation JAR.
 */
// TODO use ClassLoader.getResources() and make hierarchical
// TODO verify request headers and (cached) response headers
// TODO set "private" cache headers in debug mode?
public class ResourceServlet extends HttpServlet
{
  /**
     *
     */
    @SuppressWarnings("compatibility:8282627001212629976")
    private static final long serialVersionUID = 4547362994406585148L;
  
  /**
   * Override of Servlet.destroy();
   */
  @Override
  public void destroy()
  {
    _loaders = null;
    _loaderErrors = null;
    _facesContextFactory = null;
    _lifecycle = null;

    super.destroy();
  }
  
  /**
   * Override of Servlet.init();
   */
  @Override
  public void init(
    ServletConfig config
    ) throws ServletException
  {
    super.init(config);

    // Acquire our FacesContextFactory instance
    try
    {
      _facesContextFactory = (FacesContextFactory)
                FactoryFinder.getFactory
                (FactoryFinder.FACES_CONTEXT_FACTORY);
    }
    catch (FacesException e)
    {
      Throwable rootCause = e.getCause();
      if (rootCause == null)
      {
        throw e;
      }
      else
      {
        throw new ServletException(e.getMessage(), rootCause);
      }
    }

    // Acquire our Lifecycle instance
    _lifecycle = new _ResourceLifecycle();
    _initDebug(config);
    _loaders = new ConcurrentHashMap<String, ResourceLoader>();
    _loaderErrors = new ConcurrentHashMap<String, Class<?>>();
  }

  @Override
  public void service(
    ServletRequest  request,
    ServletResponse response
    ) throws ServletException, IOException
  {
    boolean hasFacesContext = false;
    FacesContext context = FacesContext.getCurrentInstance();

    // If we happen to invoke the ResourceServlet *via* the
    // FacesServlet, you get a lot of fun from the recursive
    // attempt to create a FacesContext.  Developers should not
    // do this, but it's easy to check
    if (context != null)
    {
      // Additional _isContextValid check on the context to workaround Mojarra issues like this one...
      // https://java.net/jira/browse/JAVASERVERFACES-2533
      if (_isContextValid(context))
      {
        hasFacesContext = true;
      }
      else
      {
        // clean up the invalid context before we try to create one
        context.release();
      }
    }
    
    if (!hasFacesContext)
    {
      Configurator.disableConfiguratorServices(request);
    
      //=-= Scott O'Bryan =-=
      // Be careful.  This can be wrapped by other things even though it's meant to be a
      // Trinidad only resource call.
      context = _facesContextFactory.getFacesContext(getServletContext(), request, response, _lifecycle);
    }

    try
    {
      super.service(request, response);
    }
    catch (ServletException e)
    {
      _logServiceException(e, request);
      throw e;
    }
    catch (IOException e)
    {
      if (!_canIgnore(e))
        _logServiceException(e, request);
      throw e;
    }
    catch (RuntimeException e)
    {
      _logServiceException(e, request);
      throw e;
    }
    catch (Error e)
    {
      _logServiceException(e, request);
      throw e;
    }
    finally
    {
      // cleanup the context we created for serving this request
      if (!hasFacesContext)
        context.release();
    }
  }
  
  /**
   * Override of HttpServlet.doGet()
   */
  @Override
  protected void doGet(
    HttpServletRequest request,
    HttpServletResponse response
    ) throws ServletException, IOException
  {
    ResourceLoader loader = _getResourceLoader(request);
    String resourcePath = getResourcePath(request);
    URL url = loader.getResource(resourcePath);

    // Make sure the resource is available
    if (url == null)
    {
      _logURLNotFound(request, loader, resourcePath);
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    // Stream the resource contents to the servlet response
    URLConnection connection = url.openConnection();
    connection.setDoInput(true);
    connection.setDoOutput(false);

    //We need to do a connect here.  Some connections, like file connections and
    //whatnot may have header information right away.  Other connections, like
    //to external resources, will not have been able to connect until this is called.
    //The reason this worked before is the getInputStream implicitly called the
    //connect, but the header information which was returned would before the stream
    //was obtained would not be avialble.
    connection.connect();    
 
    _setHeaders(connection, response, loader);

    InputStream in = connection.getInputStream();
    OutputStream out = response.getOutputStream();
    byte[] buffer = new byte[_BUFFER_SIZE];

    try
    {
      _pipeBytes(in, out, buffer);
    }
    finally
    {
      try
      {
        in.close();
      }
      finally
      {
        out.close();
      }
    }
  }

  /**
   * Override of HttpServlet.getLastModified()
   */
  @Override
  protected long getLastModified(
    HttpServletRequest request)
  {
    try
    {
      ResourceLoader loader = _getResourceLoader(request);
      String resourcePath = getResourcePath(request);
      URL url = loader.getResource(resourcePath);

      if (url == null)
        return super.getLastModified(request);

      return URLUtils.getLastModified(url);
    }
    catch (IOException e)
    {
      // Note: API problem with HttpServlet.getLastModified()
      //       should throw ServletException, IOException
      return super.getLastModified(request);
    }
  }

  /**
   * Returns the resource path from the http servlet request.
   *
   * @param request  the http servlet request
   *
   * @return the resource path
   */
  protected String getResourcePath(
    HttpServletRequest request)
  {
    return request.getServletPath() + request.getPathInfo();
  }

  /**
   * Checks if the faces context that we have is valid for this request
   */
  private boolean _isContextValid(FacesContext context)
  {
    ExternalContext ec = context.getExternalContext();
    
    // a bogus context would not have a request object
    return ((ec != null) && (ec.getRequest() != null));
  }

  /**
   * Returns the resource loader for the requested servlet path. 
   */
  private ResourceLoader _getResourceLoader(
    String servletPath)
  {
    ResourceLoader loader = null;
    
    try
    {
      String key = "META-INF/servlets/resources" +
                  servletPath +
                  ".resources";
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      URL url = cl.getResource(key);

      if (url != null)
      {
        Reader r = new InputStreamReader(url.openStream());
        BufferedReader br = new BufferedReader(r);
        try
        {
          String className = br.readLine();
          if (className != null)
          {
            className = className.trim();
            Class<?> clazz = cl.loadClass(className);
            try
            {
              // check if this is a decorator first, and if not, fall back to the no-arg constructor. 
              Constructor<?> decorator = clazz.getConstructor(_DECORATOR_SIGNATURE);
              ServletContext context = getServletContext();
              File tempdir = (File)
              context.getAttribute("javax.servlet.context.tempdir");
              ResourceLoader delegate = new DirectoryResourceLoader(tempdir);
              loader = (ResourceLoader) decorator.newInstance(new Object[]{delegate});
            }
            catch (InvocationTargetException e)
            {
              // by default, create new instance with no-args constructor
              _logLoaderException(e, servletPath);
              loader = (ResourceLoader) clazz.newInstance();
            }
            catch (NoSuchMethodException e)
            {
              // by default, create new instance with no-args constructor
              loader = (ResourceLoader) clazz.newInstance();
            }
          }
        }
        finally
        {
          br.close();
        }
      }
      else
      {
        // default to serving resources from the servlet context
        if (_LOG.isWarning())
        {
          _LOG.warning("Unable to find ResourceLoader for ResourceServlet" +
                       " at servlet path:{0}" +
                       "\nCause: Could not find resource:{1}",
                       new Object[] {servletPath, key});
        }
        
        loader = new ServletContextResourceLoader(getServletContext())
                 {
                   @Override
                   public URL getResource(
                     String path) throws IOException
                   {
                     return super.getResource(path);
                   }
                 };
      }

      // Enable resource caching, but only if we aren't debugging
      if (!_debug && loader.isCachable())
      {
        loader = new CachingResourceLoader(loader);
      }
    }
    catch (IllegalAccessException e)
    {
      loader = logExceptionAndReturnFailureLoader(e, servletPath);
    }
    catch (InstantiationException e)
    {
      loader = logExceptionAndReturnFailureLoader(e, servletPath);
    }
    catch (ClassNotFoundException e)
    {
      loader = logExceptionAndReturnFailureLoader(e, servletPath);
    }
    catch (IOException e)
    {
      loader = logExceptionAndReturnFailureLoader(e, servletPath);
    }

    return loader;    
  }
  
  /**
   * Get the servlet path from the request and see if we've already cached the resource loader for this path.
   * If not, call _getResourceLoader(String servletPath) to find it.
   *
   * @param request
   * @return
   */
  private ResourceLoader _getResourceLoader(
    HttpServletRequest request)
  {
    final String servletPath = request.getServletPath();
    ResourceLoader loader = _loaders.get(servletPath);

    if (loader == null)
    {
      loader = _getResourceLoader(servletPath);

      // add the new loader to the loader cache
      _registerLoader(servletPath, loader);
    }

    return loader;
  }

  /**
   * Add the loader to the loader map
   * @param servletPath
   * @param loader
   */
  private void _registerLoader(String servletPath, ResourceLoader loader)
  {
    _loaders.put(servletPath, loader);
  }
  
  /*
   * exception logger for _getResourceLoader() that will only log one error for multiple failures of the same servlet
   */
  private void _logLoaderException(Exception e, String servletPath)
  {
    Class<?> previousExceptionClass = _loaderErrors.get(servletPath);
    
    // if we haven't logged an error for this servletPath, or the exception class differs, log an error
    if (previousExceptionClass == null || previousExceptionClass != e.getClass())
    {
      _LOG.severe(e);
      
      if (e.getCause() != null)
        _LOG.severe("Caused by ", e.getCause());
            
      _loaderErrors.put(servletPath, e.getClass());
    }
  }

  /**
   * Method to log a warning message with helpful details when we fail to find a URL.
   * 
   * @param request
   * @param loader
   * @param resourcePath
   */
  private void _logURLNotFound(HttpServletRequest request,
                               ResourceLoader loader,
                               String resourcePath)
  {
    // log some details on the failure
    if (_LOG.isWarning())
    {
      FacesContext context = FacesContext.getCurrentInstance();
      Object servletContext = _getServletContextFromFacesContext(context);
      
      _LOG.warning("URL for resource not found.\n"+
                   "  resourcePath: {0}\n"+
                   "  loader class name: {1}\n"+
                   "  request.pathTranslated: {2}\n"+
                   "  request.requestURL: {3}\n"+
                   "  FacesContext: {4}\n" +
                   "  ServletContext: {5}\n",
                   new Object[] { resourcePath, 
                                  loader, 
                                  request.getPathTranslated(), 
                                  request.getRequestURL(),
                                  context,
                                  servletContext});
    }
  }
  
  /**
   * Method to log a severe error when we get an exception in the .service() method.
   * 
   * @param e
   * @param request
   */
  private void _logServiceException(Throwable e, ServletRequest  request)
  {
    // log some details on the service exception
    FacesContext context = FacesContext.getCurrentInstance();
    Object servletContext = _getServletContextFromFacesContext(context);
    HttpServletRequest sr = (HttpServletRequest) request;
   
    _LOG.severe("An Exception occured in ResourceServlet.service().\n"+
                 "  request.pathTranslated:" + sr.getPathTranslated() + "\n" +
                 "  request.requestURI:" + sr.getRequestURI() + "\n" +
                 "  FacesContext: " + context + "\n" +
                 "  ServletContext: " + servletContext + "\n",
                 e);
  }

  /**
   *Returns the servlet context object from the FacesContext
   * 
   * @param context
   * @return
   */
  private Object _getServletContextFromFacesContext(FacesContext context)
  {
    ExternalContext ec = null;
    Object sc = null;
    
    if (context != null)
    {
      ec = context.getExternalContext();
      
      if (ec != null)
      {
        sc = ec.getContext();
      }
    }
    
    return sc;
  }
  
  /**
   * Reads the specified input stream into the provided byte array storage and
   * writes it to the output stream.
   */
  private static void _pipeBytes(
    InputStream in,
    OutputStream out,
    byte[] buffer
    ) throws IOException
  {
    int length;

    while ((length = (in.read(buffer))) >= 0)
    {
      out.write(buffer, 0, length);
    }
  }

  /**
   * Initialize whether resource debug mode is enabled.
   */
  private void _initDebug(
    ServletConfig config
    )
  {
    String debug = config.getInitParameter(DEBUG_INIT_PARAM);
    if (debug == null)
    {
      // Check for a context init parameter if servlet init
      // parameter isn't set
      debug = config.getServletContext().getInitParameter(DEBUG_INIT_PARAM);
    }

    // private call to get the used JSF 2.0 ProjectStage as we don't have
    // access to the FacesContext object here...
    ProjectStage currentStage = _getFacesProjectStage(config.getServletContext());

    if (debug != null)
    {
      _debug = "true".equalsIgnoreCase(debug);  
    }
    else
    {
      // if the DDEBUG_INIT_PARAM parameter has NOT been specified, let us
      // apply the DEFAULT values for the certain Project Stages:
      // -PRODUCTION we want this value to be FALSE;
      // -other stages we use TRUE
      _debug = !(ProjectStage.Production.equals(currentStage));
    }

    if (_debug)
    {
      // If DEBUG_INIT_PARAM is TRUE on Production-Stage, we
      // generate a WARNING msg
      if (ProjectStage.Production.equals(currentStage))
      {
        _LOG.warning("RESOURCESERVLET_IN_DEBUG_MODE",DEBUG_INIT_PARAM);
      }
      else
      {
        _LOG.info("RESOURCESERVLET_IN_DEBUG_MODE",DEBUG_INIT_PARAM); 
      }
    }
  }

  /**
   * private version of the <code>Application.getProjectStage()</code>. See the 
   * original JavaDoc for a description of the underlying algorithm.
   * 
   * It is written as we do not have access to the FacesContext object at the point
   * of executing this method. 
   * 
   * This code comes from the <b>Apache MyFaces 2.0</b> implementation.
   */
  private ProjectStage _getFacesProjectStage(ServletContext servletContext)
  {
    if (_projectStage == null)
    {
      String stageName = null;
      // Look for a JNDI environment entry under the key given by the
      // value of ProjectStage.PROJECT_STAGE_JNDI_NAME (a String)
      try
      {
        Context ctx = new InitialContext();
        Object temp = ctx.lookup(ProjectStage.PROJECT_STAGE_JNDI_NAME);
        if (temp != null)
        {
          if (temp instanceof String)
          {
            stageName = (String) temp;
          }
          else
          {
            if (_LOG.isSevere())
            {
              _LOG.severe("Invalid JNDI lookup for key " + ProjectStage.PROJECT_STAGE_JNDI_NAME);
            }
          }
        }
      }
      catch (NamingException e)
      {
        // no-op we need to ignore this...
      }

      /*
       * If found, continue with the algorithm below, otherwise, look for an entry in the initParamMap of the
       * ExternalContext from the current FacesContext with the key ProjectStage.PROJECT_STAGE_PARAM_NAME
       */
      if (stageName == null)
      {
        stageName = servletContext.getInitParameter(ProjectStage.PROJECT_STAGE_PARAM_NAME);
      }
      
      // If a value is found found
      if (stageName != null)
      {
        /*
         * see if an enum constant can be obtained by calling ProjectStage.valueOf(), passing the value from the
         * initParamMap. If this succeeds without exception, save the value and return it.
         */
        try
        {
          _projectStage = ProjectStage.valueOf(stageName);
          return _projectStage;
        }
        catch (IllegalArgumentException e)
        {
          _LOG.severe("Couldn't discover the current project stage", e);
        }
      }
      else
      {
        if (_LOG.isInfo())
        {
          _LOG.info("Couldn't discover the current project stage, using " + ProjectStage.Production);
        }
      }
      /*
       * If not found, or any of the previous attempts to discover the enum constant value have failed, log a
       * descriptive error message, assign the value as ProjectStage.Production and return it.
       */

      _projectStage = ProjectStage.Production;      
    }

    return _projectStage;
  }

  /**
   * Sets HTTP headers on the response which tell
   * the browser to cache the resource indefinitely.
   */
  private void _setHeaders(
    URLConnection       connection,
    HttpServletResponse response,
    ResourceLoader      loader)
  {
    String resourcePath;
    URL    url;
    String contentType  = ResourceLoader.getContentType(loader, connection);

    if (contentType == null || "content/unknown".equals(contentType))
    {
      url = connection.getURL();
      resourcePath = url.getPath();

      // 'Case' statement for unknown content types
      if (resourcePath.endsWith(".css"))
        contentType = "text/css";
      else if (resourcePath.endsWith(".js"))
        contentType = "application/x-javascript";
      else if (resourcePath.endsWith(".cur") || resourcePath.endsWith(".ico"))
        contentType = "image/vnd.microsoft.icon";
      else
        contentType = getServletContext().getMimeType(resourcePath);

      // The resource has an file extension we have not
      // included in the case statement above
      if (contentType == null)
      {
        _LOG.warning("ResourceServlet._setHeaders(): " +
                     "Content type for {0} is NULL!\n" +
                     "Cause: Unknown file extension",
                     resourcePath);
      }
    }

    if (contentType != null)
    {
      response.setContentType(contentType);
      int contentLength = connection.getContentLength();

      if (contentLength >= 0)
        response.setContentLength(contentLength);
    }

    long lastModified;
    try
    {
      lastModified = URLUtils.getLastModified(connection);
    }
    catch (IOException exception)
    {
      lastModified = 0;
    }

    if (lastModified == 0)
      response.setDateHeader("Last-Modified", lastModified);

    //Handle cache controls.  So this is a bit complex and confusing.  If
    //either of the cache headers are provide AND we are not in debug mode
    //then we should set the cache to a default value.  This is to maintain
    //backward compatibility.  If, however, the expiration or cache control
    //headers are provided, we should just use those.
    String cacheControl =  connection.getHeaderField("cache-control");
    Long expires = connection.getExpiration();
    
    if (!_debug && null == cacheControl && 0 == expires)
    {
      // We set two headers: Cache-Control and Expires.
      // This combination lets browsers know that it is
      // okay to cache the resource indefinitely.

      // Set Cache-Control to "Public".
      cacheControl = "Public";
      expires = System.currentTimeMillis() + ONE_YEAR_MILLIS;
    }
    
    if(null != cacheControl)
    {
      response.setHeader("Cache-Control", cacheControl);
    }
    
    if(0 != expires)
    {
      response.setDateHeader("Expires", expires);
    }
    
    Map<String, List<String>> headerMap = connection.getHeaderFields();
    if(null != headerMap) //There are some wonkey impl's out there, best to guard against null
    {
      //put additional headers
      for(Map.Entry<String, List<String>> entry: connection.getHeaderFields().entrySet())
      {
        //We handled a number of headers above and there are others we might want
        //to exclude.  We test for those headers here.
        String key = entry.getKey();
        if(Arrays.binarySearch(_INCLUDED_HEADERS, key.toLowerCase()) >= 0 && null != entry.getValue())
        {
          //Header is not excluded, add all the entries
          for(String value:entry.getValue())
          {
            response.addHeader(key, value);
          }
        }
      }
    }
  }

  private static boolean _canIgnore(Throwable t)
  {
    if (t instanceof InterruptedIOException)
    {
      // All "interrupted" IO is not notable
      return true;
    }
    else if (t instanceof SocketException)
    {
      // And any sort of SocketException should also be
      // ignored (Internet Explorer is a prime source of these,
      // as it doesn't try to close down sockets properly
      // when a user cancels)
      return true;
    }
    else if (t instanceof IOException)
    {
      String message = t.getMessage();
      // Check for "Broken pipe" and "connection was aborted"/
      // "connection abort" messages
      if ((message != null) &&
          ((message.indexOf("Broken pipe") >= 0) ||
           (message.indexOf("abort") >= 0)))
        return true;
    }
    return false;
  }

  /**
   * Instead of calling ResourceLoader.getNullResourceLoader(), we want to return our own version which will
   * periodically attempt to get the real resource loader and start using that instead.
   */
  private ResourceLoader logExceptionAndReturnFailureLoader(Exception e, String servletPath)
  {
    _logLoaderException(e, servletPath);
    return new _ExponentialBackoffResourceLoader(servletPath);
  }

  /**
   * The _ExponentialBackOffResourceLoader is a private class to be used in place of the resource loader returned by
   * ResourceLoader.getNullResourceLoader().  We want to initially start out with a wait time of 10 miliseconds before
   * we attempt to get the correct resource loader.  We'll double the wait time for every attempt, until the wait time
   * is longer than a minute.  At that point, we will still keep trying but no longer double the wait time.
   */
  private final class _ExponentialBackoffResourceLoader extends ResourceLoader
  {
    /**
     * Constructs a new repairing resource loader.
     */
    protected _ExponentialBackoffResourceLoader(String servletPath)
    {
      super(null);
      
      // Set the timestamp for this request
      _initialTimeStamp = System.currentTimeMillis();
      _nextBackoffTime = _initialTimeStamp + _INITIAL_WAIT_TIME;
      _servletPath = servletPath;
    }

    
    @Override
    protected URL findResource(
      String name
      ) throws IOException
    {
      long currentTime = System.currentTimeMillis();

      // If we have waited the current wait time, we need to try again.
      if (currentTime > _nextBackoffTime)
      {
        synchronized(this)
        {
          // We need to check this again, just in case a different thread already updated the value
          // If another thread updated the value, we don't need try to get the resource loader or
          // update the backoff time
          if (currentTime > _nextBackoffTime)
          {
            ResourceLoader newLoader = _getResourceLoader(_servletPath);
            
            if (!(newLoader instanceof _ExponentialBackoffResourceLoader))
            {
              // We got a real resource loader, lets repair ourself by updating the loader cache.
              _registerLoader(_servletPath, newLoader);
              
              //Log a message
              _LOG.warning("Fixed resource loader for servlet path: {0}", _servletPath);
              
              return newLoader.getResource(name);
            }

            // No new loader found, update the next backoff time.
            // this doesn't produce an exact doubling of the _INITIAL_WAIT_TIME, but we really don't need to
            // be exact here, as long as we reach the max wait time
            _nextBackoffTime += Math.min((currentTime - _initialTimeStamp) * 2, _MAX_WAIT_TIME);
          }
        }
      }
      
      // We haven't waited long enough, just return null.
      return null;
    }

    private volatile long _nextBackoffTime; // The number of miliseconds to wait before trying again.
    private final long _initialTimeStamp; // The timestamp of the last failed attempt to get the resource loader
    private final String _servletPath;
  }

  static private class _ResourceLifecycle extends Lifecycle
  {
    @Override
    public void execute(FacesContext p0) throws FacesException
    {
    }

    @Override
    public PhaseListener[] getPhaseListeners()
    {
      return null;
    }

    @Override
    public void removePhaseListener(PhaseListener p0)
    {
    }

    @Override
    public void render(FacesContext p0) throws FacesException
    {
    }

    @Override
    public void addPhaseListener(PhaseListener p0)
    {
    }
  }

  /**
   * Context parameter for activating debug mode, which will disable
   * caching.
   */
  public static final String DEBUG_INIT_PARAM =
    "org.apache.myfaces.trinidad.resource.DEBUG";

  // One year in milliseconds.  (Actually, just short of on year, since
  // RFC 2616 says Expires should not be more than one year out, so
  // cutting back just to be safe.)
  public static final long ONE_YEAR_MILLIS = 31363200000L;
  private static final long _INITIAL_WAIT_TIME = 10L; // 10 milliseconds  
  private static final long _MAX_WAIT_TIME = 60000L; // 60 * 1000 milliseconds

  private static final Class[] _DECORATOR_SIGNATURE =
                                  new Class[]{ResourceLoader.class};

  private static final TrinidadLogger _LOG = TrinidadLogger.createTrinidadLogger(ResourceServlet.class);

  // Size of buffer used to read in resource contents
  private static final int _BUFFER_SIZE = 2048;
  
  private volatile boolean _debug;
  private volatile ConcurrentMap<String, ResourceLoader> _loaders;
  private volatile ConcurrentMap<String, Class<?>> _loaderErrors;
  private volatile FacesContextFactory _facesContextFactory;
  private volatile Lifecycle _lifecycle;
  private volatile ProjectStage _projectStage;

  //There are only a handful of headers that this servlet supports being propigated
  //to the server.  Headers must be included in this white-list in order to be sent
  //over.  The reason we provide a white-list instead of a black-list is primarily 
  //for security.
  private static final String[] _INCLUDED_HEADERS;

  static 
  {
    //Here are some notable headers that are missing from the list:
    // "Content-Type"      - Set directly on the response
    //  "Last-Modified"    - Sets directly on the response
    //  "Cache-Control"    - This is added because the servlet sets its own
    //  "Expires"          - This is addded because the servlet sets its own
    //  "Server"           - This should reflect THIS server
    //  "Set-Cookie"       - Cookies, right now, are not preserved in the client.  
    //                       We are a proxy, we need better cookie handling.
    //  "Status"           - Returned by the servlet container
    //  "Via"              - Intentionally mask any proxying behind the firwall
    //  "WWW-Authenticate" - This should be handled by this servlet
    //  "Allow"            - Returned by this servlet    

    String[] tempArray = new String[]
    {
      "age",
      "content-language",
      "content-location",
      "content-md5",
      "content-disposition",
      "content-range",
      "date",
      "link",
      "p3p",
      "refresh",
      "retry-after",
      "strict-transport-security",
      "trailer",
//      "transfer-encoding",
//      "vary",
      "warning"
    };
          
    //Ensure the items are in natural order so they can be binary searched.
    Arrays.sort(tempArray);
    
    _INCLUDED_HEADERS = tempArray;
  }
}

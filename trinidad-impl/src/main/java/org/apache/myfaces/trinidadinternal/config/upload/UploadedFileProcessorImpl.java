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
package org.apache.myfaces.trinidadinternal.config.upload;

import java.io.File;
import java.io.IOException;

import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.PortletContext;
import javax.portlet.PortletRequest;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;

import org.apache.myfaces.trinidad.logging.TrinidadLogger;
import org.apache.myfaces.trinidad.model.UploadedFile;
import org.apache.myfaces.trinidad.util.ClassLoaderUtils;
import org.apache.myfaces.trinidad.webapp.UploadedFileProcessor;
import org.apache.myfaces.trinidadinternal.context.external.PortletApplicationMap;
import org.apache.myfaces.trinidadinternal.context.external.PortletInitParameterMap;
import org.apache.myfaces.trinidadinternal.context.external.PortletRequestMap;
import org.apache.myfaces.trinidadinternal.context.external.ServletApplicationMap;
import org.apache.myfaces.trinidadinternal.context.external.ServletInitParameterMap;
import org.apache.myfaces.trinidadinternal.context.external.ServletRequestMap;

/**
 * @deprecated This implementation uses CompositeUploadedFileProcessorImpl as a delegate.
 * Please use CompositeUploadedFileProcessorImpl directly instead.
 */
@Deprecated
public class UploadedFileProcessorImpl implements UploadedFileProcessor
{
  public UploadedFileProcessorImpl()
  {
    _delegate = new CompositeUploadedFileProcessorImpl();
  }

  public void init(Object context)
  {
    _delegate.init(context);
  }

  public UploadedFile processFile(
      Object request, UploadedFile tempFile) throws IOException
  {
    return _delegate.processFile(request, tempFile);
  }

  private UploadedFileProcessor _delegate;
}

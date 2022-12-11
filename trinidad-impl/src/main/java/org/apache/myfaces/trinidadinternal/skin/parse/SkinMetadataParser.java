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
package org.apache.myfaces.trinidadinternal.skin.parse;

import javax.el.ValueExpression;

import org.apache.myfaces.trinidad.logging.TrinidadLogger;
import org.apache.myfaces.trinidad.skin.CustomMetadata;
import org.apache.myfaces.trinidad.skin.SkinFeatures;
import org.apache.myfaces.trinidad.skin.SkinMetadata;
import org.apache.myfaces.trinidad.skin.SkinVersion;
import org.apache.myfaces.trinidadinternal.config.LazyValueExpression;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.XhtmlConstants;
import org.apache.myfaces.trinidadinternal.share.xml.BaseNodeParser;
import org.apache.myfaces.trinidadinternal.share.xml.NodeParser;
import org.apache.myfaces.trinidadinternal.share.xml.ParseContext;
import org.apache.myfaces.trinidadinternal.share.xml.StringParser;

import org.xml.sax.Attributes;
import org.xml.sax.SAXParseException;

/**
 * NodeParser for &lt;skin&gt; node in trinidad-skins.xml
 *
 * @version $Name:  $ ($Revision: adfrt/faces/adf-faces-impl/src/main/java/oracle/adfinternal/view/faces/ui/laf/xml/parse/SkinExtensionParser.java#0 $) $Date: 10-nov-2005.18:50:44 $
 * @todo ELIMINATE NAMESPACE
 */
public class SkinMetadataParser extends BaseNodeParser
  implements XMLConstants
{
  @Override
  public void startElement(
    ParseContext context,
    String       namespaceURI,
    String       localName,
    Attributes   attrs
    ) throws SAXParseException
  {
    _namespace = namespaceURI;
  }

  @Override
  public Object endElement(
    ParseContext context,
    String       namespaceURI,
    String       localName
    ) throws SAXParseException
  {

    // id and family are required. log a severe error if they are null.
    if (_id == null)
    {
      _LOG.severe("REQUIRED_ELEMENT_ID_NOT_FOUND");
      throw new NullPointerException(_LOG.getMessage("NULL_ID"));
    }

    if (_family == null)
    {
      _LOG.severe("REQURIED_ELEMENT_FAMILY_NOT_FOUND");
      throw new NullPointerException(_LOG.getMessage("NULL_FAMILY"));
    }

    if ((_bundleName != null) && (_translationSourceExpression != null))
    {
      _LOG.severe("BOTH_BUNDLENAME_TRANSLATIONSOURCE_SET");
      _translationSourceExpression = null;
    }

    if (_translationSourceExpression != null &&
        !(_translationSourceExpression.startsWith("#{") &&
        _translationSourceExpression.endsWith("}")))
    {
      _LOG.severe("TRANSLATION_SOURCE_NOT_EL");
      _translationSourceExpression = null;
    }

    Object isMetaInf = context.getProperty(SKIN_NAMESPACE, META_INF);

    if (isMetaInf != null && _styleSheetName != null
      && Boolean.parseBoolean(isMetaInf.toString()) && !(_styleSheetName.startsWith("/")))
      _styleSheetName = META_INF_DIR.concat(_styleSheetName);

    ValueExpression translationSource = null;
    
    if (_translationSourceExpression != null)
      translationSource =  LazyValueExpression.createValueExpression(_translationSourceExpression, Object.class);

    // here since we are reading from trinidad-skins.xml we have to assert that id and
    // family is set.
    // creation of SkinNode threw NullPointerException if Id or family was not provided
    // at this point. Since we use SkinMetadata for querying skins we cannot assert that
    // id and family should be set. So we do this check here in the parser.
    return new SkinMetadata.Builder().id(_id).family(_family).renderKitId(SkinMetadata.RenderKitId.fromId(_renderKitId))
    .baseSkinId(_extends).styleSheetName(_styleSheetName).resourceBundleName(_bundleName)
    .translationSource(translationSource).version(_skinVersionNode)
    .features(_skinFeaturesNode).metadata(_skinMetadataNode).build();
  }

  @Override
  public NodeParser startChildElement(
    ParseContext context,
    String       namespaceURI,
    String       localName,
    Attributes   attrs
    ) throws SAXParseException
  {
    if (!namespaceURI.equals(_namespace))
      return null;

    if ("id".equals(localName) ||
        "family".equals(localName) ||
        "render-kit-id".equals(localName) ||
        "style-sheet-name".equals(localName) ||
        "bundle-name".equals(localName) ||
        "translation-source".equals(localName) ||
        "extends".equals(localName))

    {
      return new StringParser();
    }
    else if ("version".equals(localName))
    {
      return context.getParser(SkinVersion.class, namespaceURI, localName);
    }
    else if ("features".equals(localName))
    {
      return context.getParser(SkinFeatures.class, namespaceURI, localName);
    }
    else if ("metadata".equals(localName))
    {
      return context.getParser(CustomMetadata.class, namespaceURI, localName);
    }

    return null;
  }

  @Override
  public void addCompletedChild(
    ParseContext context,
    String       namespaceURI,
    String       localName,
    Object       child
    ) throws SAXParseException
  {

    if ("id".equals(localName))
      _id = (String) child;
    else if ("family".equals(localName))
      _family = (String) child;
    else if ("render-kit-id".equals(localName))
      _renderKitId = (String) child;
    else if ("style-sheet-name".equals(localName))
      _styleSheetName = (String) child;
    else if ("bundle-name".equals(localName))
      _bundleName = (String) child;
    else if ("translation-source".equals(localName))
      _translationSourceExpression = (String) child;
    else if ("extends".equals(localName))
      _extends = (String) child;
    else if ((child instanceof SkinVersion))
      _skinVersionNode = ((SkinVersion)child);
    else if ((child instanceof SkinFeatures))
      _skinFeaturesNode = ((SkinFeatures)child);
    else if ((child instanceof CustomMetadata))
      _skinMetadataNode = ((CustomMetadata)child);
  }

  private String      _namespace;
  private String      _id;
  private String      _family;
  private String      _styleSheetName;
  private String      _renderKitId;
  private String      _bundleName;
  private String      _translationSourceExpression;
  private String      _extends;
  private SkinVersion _skinVersionNode;
  private SkinFeatures _skinFeaturesNode;
  private CustomMetadata _skinMetadataNode;

  private static final TrinidadLogger _LOG =
    TrinidadLogger.createTrinidadLogger(SkinMetadataParser.class);

}
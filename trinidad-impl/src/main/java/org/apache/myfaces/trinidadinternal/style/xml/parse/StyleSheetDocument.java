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
package org.apache.myfaces.trinidadinternal.style.xml.parse;

import java.awt.Color;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.myfaces.trinidad.context.AccessibilityProfile;
import org.apache.myfaces.trinidad.context.LocaleContext;
import org.apache.myfaces.trinidad.logging.TrinidadLogger;
import org.apache.myfaces.trinidad.skin.Icon;
import org.apache.myfaces.trinidad.style.Style;
import org.apache.myfaces.trinidad.util.ArrayMap;
import org.apache.myfaces.trinidad.util.IntegerUtils;
import org.apache.myfaces.trinidadinternal.agent.TrinidadAgent;
import org.apache.myfaces.trinidadinternal.skin.icon.ContextImageIcon;
import org.apache.myfaces.trinidadinternal.skin.icon.NullIcon;
import org.apache.myfaces.trinidadinternal.skin.icon.TextIcon;
import org.apache.myfaces.trinidadinternal.skin.icon.URIImageIcon;
import org.apache.myfaces.trinidadinternal.style.PropertyParseException;
import org.apache.myfaces.trinidadinternal.style.StyleContext;
import org.apache.myfaces.trinidadinternal.style.UnmodifiableStyle;
import org.apache.myfaces.trinidadinternal.style.util.CSSUtils;
import org.apache.myfaces.trinidadinternal.style.util.ModeUtils;
import org.apache.myfaces.trinidadinternal.style.util.NameUtils;
import org.apache.myfaces.trinidadinternal.style.util.StyleUtils;
import org.apache.myfaces.trinidadinternal.util.nls.LocaleUtils;


/**
 * Parsed representation of a Trinidad style sheet document.
 *
 * The StyleSheetDocument provides access to both style as well as icons
 * information, but not to skin properties.
 *
 * @version $Name:  $ ($Revision: adfrt/faces/adf-faces-impl/src/main/java/oracle/adfinternal/view/faces/style/xml/parse/StyleSheetDocument.java#0 $) $Date: 10-nov-2005.18:58:12 $
 */
public class StyleSheetDocument
{
  /**
   * Value used to indicate that the document timestamp is not know.
   */
  public static final long UNKNOWN_TIMESTAMP = -1;

  /**
   * Creates a StyleSheetDocument
   */
  @Deprecated
  public StyleSheetDocument(
    StyleSheetNode[] styleSheets
    )
  {
    this (styleSheets, null, UNKNOWN_TIMESTAMP);
  }

  /**
   * Creates a StyleSheetDocument
   */
  @Deprecated
  public StyleSheetDocument(
    StyleSheetNode[] styleSheets,
    String documentVersion
    )
  {
    this(styleSheets, documentVersion, UNKNOWN_TIMESTAMP);
  }

  /**
   * Creates a StyleSheetDocument
   * @param styleSheets The StyleSheetNodes which define the contents
   *          of this StyleSheetDocument.
   * @param documentVersion The version identifier for this StyleSheetDocument.
   * @param documentTimestamp The timestamp for this StyleSheetDocument.
   */
  public StyleSheetDocument(
    StyleSheetNode[] styleSheets,
    String           documentVersion,
    long             documentTimestamp
    )
  {
    if (styleSheets != null)
    {
      _styleSheets = new StyleSheetNode[styleSheets.length];
      System.arraycopy(styleSheets, 0, _styleSheets, 0, styleSheets.length);
    }

    _documentVersion = documentVersion;
    _documentTimestamp = documentTimestamp;
  }

  /**
   * Returns the StyleSheetDocument's id.
   * StyleSheetDocument contains one or more StyleSheetNodes. 
   * StyleSheetNodes contain StyleNodes & locale, browser, direction, etc.
   * We compute the hashcode of the StyleSheetDocument's styleSheetNodes(StyleContext) and
   * return that as a document id. One use of this id is to use it in the Skin's generated
   * CSS filename so the filename will change if the Skin's StyleSheetNodes change.
   * @param sContext The current StyleContext which is used to get the stylesheets that match,
   * and then we create an id based on those stylesheets
   * @return String The document Id as a String.
   */
  public String getDocumentId(StyleContext sContext)
  {
    int hashCode = 17;
    Iterator<StyleSheetNode> styleSheetNodes = getStyleSheets(sContext);
    while (styleSheetNodes.hasNext())
    {
      hashCode = hashCode*37 +  styleSheetNodes.next().getStyleSheetId();      
    }
    return Integer.toString(Math.abs(hashCode), 36);
  }

  /**
   * Returns the version identifier for this style sheet document.
   */
  public String getDocumentVersion()
  {
    return _documentVersion;
  }

  /**
   * Returns a timestamp which indicates when the underlying source
   * files which were used to create this StyleSheetDocument were
   * last modified.  The semantics of this method are slightly
   * different than the typical last modified timestamp (eg.
   * File.lastModified()) in that the timestamp is determined when
   * the StyleSheetDocument is created.  The StyleSheetDocument
   * itself represents the contents of a style sheet at a
   * particular moment in time - and does not detect later
   * updates to the underlying source files.
   */
  public long getDocumentTimestamp()
  {
    return _documentTimestamp;
  }

  /**
   * Implementation of StyleSheetDocument.getStyleSheets();
   */
  public Iterator<StyleSheetNode> getStyleSheets()
  {
    return getStyleSheetsAsCollection().iterator();
  }
  
  public Collection<StyleSheetNode> getStyleSheetsAsCollection()
  {
    if(_styleSheets==null)
    {
      // -= Simon Lessard =- 
      // TODO: Collections.emptyList() maybe?
      return  (Arrays.asList(new StyleSheetNode[0]));
    }
    else
    {
      return (Arrays.asList(_styleSheets));
    }
  }

  /**
   * Returns all StyleSheetNodes which can be applied to the specified
   * context, sorted from lowest to highest precedence.
   */
  public Iterator<StyleSheetNode> getStyleSheets(StyleContext context)
  {
    return getStyleSheetsAsCollection(context).iterator();
  }
  
  public Collection<StyleSheetNode> getStyleSheetsAsCollection(StyleContext context)
  {
    return _getStyleSheets(context).styleSheets();
  }

  /**
   * Returns an Iterator of IconNode objects for the specified context.
   */
   @SuppressWarnings("unchecked")
  public Iterator<IconNode> getIcons(StyleContext context)
  {
    // document.getStyleSheets sorts by lowest to highest precedence
    StyleSheetList styleSheets = _getStyleSheets(context);
    if (styleSheets.isEmpty())
    {
      List<IconNode> emptyList = Collections.emptyList();
      return emptyList.iterator();
    }
    
    // We also need to provide a Map for storing selector-based
    // styles and another for storing name-based styles, used by
    // _resolveStyle() to store results
    Map<String, StyleNode> resolvedStyles      = new HashMap<String, StyleNode>();
    Map<String, StyleNode> resolvedNamedStyles = new HashMap<String, StyleNode>();

    // Keep track of all selectors and names that we've already
    // found, so that we don't bother re-resolving them
    // These differ from the above Maps in that the Maps are
    // mutated by _resolveStyle - so an included selector, for
    // instance, might already be present in _resolveStyle but
    // not yet have been encountered by this top loop.  We
    // could eliminate the need for this if we were willing
    // to give up the ordering of the list
    Set<String>    foundNames = new HashSet<String>();
    List<IconNode> iconNodes  = new ArrayList<IconNode>();

    // We want to cache IDs in this use of StyleSheetList. We'll use the cache
    // when we resolve styles.
    styleSheets.cacheIconIds();
    styleSheets.cacheStyleIds();
    
    for (StyleSheetNode styleSheet : styleSheets.styleSheets())
    {
      Iterable<IconNode> iconNodeList = styleSheet.getIcons();

      // iterate through each one and add to the list.
      for (IconNode iconNodeFromStyleSheet : iconNodeList)
      {
        StyleNode sNode = iconNodeFromStyleSheet.getStyleNode();
        String clientRule = (sNode == null) ? null : sNode.getClientRule();
        boolean isFound;
        String id = iconNodeFromStyleSheet.getIconName();
        isFound = foundNames.contains(sNode.getId());
        

        // If we've already seen that node/selector, no need to look
        // for it again
        if (!isFound)
        {
          StyleNode resolvedNode = _resolveStyleNode(context, true,
                                                     styleSheets,
                                                     resolvedStyles,
                                                     resolvedNamedStyles,
                                                     null,
                                                     null,
                                                     id,
                                                     false,
                                                     sNode.getId(),
                                                     clientRule);

          // Create the Icon
          if (resolvedNode != null)        
          {
            Icon icon = _createIconFromNode(resolvedNode);

            if (icon != null)
            {
              iconNodes.add(new IconNode(id, icon, resolvedNode));
              foundNames.add(resolvedNode.getId());
            }
          }
        }
      }
    }

    return iconNodes.iterator();
  }

  /**
   * From each property in the resolvedNode, create an Icon object.
   * @param resolvedNode StyleNode that contains all the resolved css properties
   * @return an Icon object that was created from the information in the resolvedNode.
   */
  private Icon _createIconFromNode(StyleNode resolvedNode)
  {    
    Integer width = null;
    String  widthValue = null;
    Integer height = null;
    String  heightValue = null;
    String  uri = null;
    String  text = null;
    boolean isNullIcon = false;
    
    Map<String, String> propertyMap = new ArrayMap<String, String>();
    
    // loop through each property in the StyleNode.
    // If 'content', then get the url and the type of icon: 
    // Context, Image, Null, or Text
    // If 'width', then get the width
    // If 'height', then get the height
    // Build up all the rest of the properties as an UnmodifiableStyle object.
    // Then create an Icon object. 
    Collection<PropertyNode> properties = resolvedNode.getProperties();
    for (PropertyNode propertyNode : properties)
    {
      String propertyName = propertyNode.getName();
      String propertyValue = propertyNode.getValue();
      
      if (propertyName != null && propertyValue != null)
      {
        if (propertyName.equals("width"))
        {
          // save the original propertyValue for the 'width' property in widthValue.
          // Then strip off px from the string and return an Integer and store in width.
          if (_INTEGER_PATTERN.matcher(propertyValue).matches())
          {
            widthValue = propertyValue;
            width = _convertPxDimensionStringToInteger(widthValue);
          }
          else
          {
            widthValue = null;
            // use inlineStyle for non-integer width values;
            propertyMap.put(propertyName, propertyValue);
          }
        }
        else if (propertyName.equals("height"))
        {
          // save original height value
          // strip off px from the string and return an Integer
          if (_INTEGER_PATTERN.matcher(propertyValue).matches())
          {
            heightValue = propertyValue;
            height = _convertPxDimensionStringToInteger(heightValue);
          }
          else
          {
            // use inlineStyle for non-integer height values;
            heightValue = null;
            propertyMap.put(propertyName, propertyValue);
          }
        }
        else if (propertyName.equals("content"))
        {
          // is it a text or uri
          if (_isURLValue(propertyValue))
          {
            uri = _getURIString(propertyValue);
          }
          else if (propertyValue.startsWith("inhibit"))
          {
            isNullIcon = true;
          }
          else
          {
            text = _trimQuotes(propertyValue);
          }
        }
        else
        {
          // create an inlineStyle with all the extraneous style properties
          propertyMap.put(propertyName, propertyValue);
        }
      }
    }
     

    // now I need to create the icon.
    // do not create an icon if isNullIcon is true.
    Icon icon = null;

    if (!isNullIcon)
    {
     if (text != null)
     {
       // don't allow styleClass from the css parsing file. We can handle
       // this when we have style includes
       // put back the width/height properties if there were some
       if (heightValue != null)
         propertyMap.put("height", heightValue);
       if (widthValue != null)
         propertyMap.put("width", widthValue);
       icon = new TextIcon(text, text, null, 
                           propertyMap.isEmpty() ? null : new UnmodifiableStyle(propertyMap));
     }
     else if (uri != null)
     {
       // A URIImageIcon url starts with '/' or 'http:',
       // whereas a ContextImageIcons uri does not. 
       boolean startsWithASlash = uri.startsWith("/");
      Style inlineStyle = propertyMap.isEmpty() ? null : new UnmodifiableStyle(propertyMap);

       if (!startsWithASlash && !CSSUtils.isAbsoluteURI(uri))
       {
         icon =
           new ContextImageIcon(uri, uri, width, height, null, inlineStyle);
       }
       else
       {
         icon =
           new URIImageIcon(uri, uri, width, height, null, inlineStyle);  


       }
      }
    }
    else
    {
      icon = NullIcon.sharedInstance();
    }

    
    return icon;
  }

  /**
   * Returns an Iterator of StyleNode objects for the specified context.
   */
  @SuppressWarnings("unchecked")
  public Iterator<StyleNode> getStyles(StyleContext context)
  {
    StyleSheetList styleSheets = _getStyleSheets(context);
    if (styleSheets.isEmpty())
    {
      List<StyleNode> emptyList = Collections.emptyList();
      return emptyList.iterator();
    }

    // We are going to loop through every StyleNode in every StyleSheetNode,
    // resolving each one along the way.  We store resolved StyleNodes in
    // a List, so that the generated CSS somewhat matches the order that
    // the style elements appear in the XSS document.
    ArrayList<StyleNode> styles = new ArrayList<StyleNode>();

    // We also need to provide a Map for storing selector-based
    // styles and another for storing name-based styles, used by
    // _resolveStyle() to store results
    HashMap<String, StyleNode> resolvedStyles = 
      new HashMap<String, StyleNode>();
    HashMap<String, StyleNode> resolvedNamedStyles = 
      new HashMap<String, StyleNode>();

    // Keep track of all selectors and names that we've already
    // found, so that we don't bother re-resolving them
    // These differ from the above Maps in that the Maps are
    // mutated by _resolveStyle - so an included selector, for
    // instance, might already be present in _resolveStyle but
    // not yet have been encountered by this top loop.  We
    // could eliminate the need for this if we were willing
    // to give up the ordering of the list
    Set<String> foundSelectors = new HashSet<String>();
    Set<String> foundNames     = new HashSet<String>();

    // Now, loop through all StyleNodes in all StyleSheetNodes
    
    // We want to cache IDs in this use of StyleSheetList. We'll use the cache
    // when we resolve styles.
    styleSheets.cacheStyleIds();

    for (StyleSheetNode styleSheet : styleSheets.styleSheets())
    {
      Iterable<StyleNode> styleNodeList = styleSheet.getStyles();

      for (StyleNode node : styleNodeList)
      {
        String id, cacheId = node.getId();
        boolean isFound;
        boolean isNamed;

        // Is this a named node or a selector?
        if (node.getName() != null)
        {
          isNamed = true;
          id = node.getName();
          isFound = foundNames.contains(cacheId);
        }
        else
        {
          isNamed = false;
          id = node.getSelector();
          isFound = foundSelectors.contains(cacheId);
        }

        // If we've already seen that node/selector, no need to look
        // for it again
        if (!isFound)
        {
          StyleNode resolvedNode = _resolveStyleNode(context, false,
                                                 styleSheets,
                                                 resolvedStyles,
                                                 resolvedNamedStyles,
                                                 null,
                                                 null,
                                                 id,
                                                 isNamed,
                                                 cacheId,
                                                 node.getClientRule());

          // If we got a node, add it in to our list
          if (resolvedNode != null)        
          {
            cacheId = resolvedNode.getId();
            styles.add(resolvedNode);
            if (isNamed)
              foundNames.add(cacheId);
            else
              foundSelectors.add(cacheId);
          }
        }
      }

    }

    return styles.iterator();
  }

  /**
   * Returns the fully-resolved StyleNode for the style with the
   * specified selector.
   */
  public StyleNode getStyleBySelector(
    StyleContext context,
    String       selector
    )
  {
    return _getStyle(context, selector, false);
  }

  /**
   * Returns the fully-resolved StyleNode for the style with the
   * specified name.
   */
  public StyleNode getStyleByName(
    StyleContext context,
    String       name
    )
  {
    return _getStyle(context, name, true);
  }

  // Returns array of matching style sheets sorted by specificity
  private StyleSheetList _getStyleSheets(
    StyleContext context
    )
  {
    LocaleContext localeContext = context.getLocaleContext();
    Locale locale = localeContext.getTranslationLocale();
    int direction = LocaleUtils.getReadingDirection(localeContext);
    int mode = NameUtils.getMode(ModeUtils.getCurrentMode(context));
    TrinidadAgent agent = context.getAgent();
    AccessibilityProfile accProfile = context.getAccessibilityProfile();

    List<StyleSheetNode> v = new ArrayList<StyleSheetNode>(); // List of matching style sheets
    Iterator<StyleSheetNode> e = getStyleSheets();  // Iterator of all style sheets

    // Loop through the style sheets, storing matches in the List
    while (e.hasNext())
    {
      StyleSheetNode styleSheet = e.next();

      if (styleSheet.compareVariants(locale, direction, agent, mode, accProfile) > 0)
        v.add(styleSheet);
    }

    int count = v.size();
    if (count == 0)
      return new StyleSheetList(null);

    // Sort the matching style sheets by specificity
    StyleSheetNode[] styleSheets = v.toArray(new StyleSheetNode[count]);
    Comparator<StyleSheetNode> comparator = 
      new StyleSheetComparator(locale,
                               direction,
                               agent,
                               mode,
                               _styleSheets,
                               accProfile);

    Arrays.sort(styleSheets, comparator);

    return new StyleSheetList(styleSheets);
  }

  // Gets the style with the specified selector/name
  private StyleNode _getStyle(
    StyleContext context,
    String       id,
    boolean      isNamed
    )
  {
    StyleSheetList styleSheets = _getStyleSheets(context);
    if (styleSheets.isEmpty())
      return null;

    return _resolveStyleNode(context, false,
                         styleSheets,
                         new HashMap<String, StyleNode>(19),  // Resolved styles
                         new HashMap<String, StyleNode>(19),  // Resolved named styles
                         null,               // Include stack
                         null,               // Named include stack
                         id,
                         isNamed,
                         null, // pass null here - because the query is for retrieving StyleNode by selector or name
                         null // pass null here - because the query is for retrieving StyleNode by selector or name
    );
  }

  /**
   * Resolves the (named or selector-based) style with the specified id.
   * In SkinStyleSheetParserUtils we parse the CSS file and create a raw StyleNode, one
   * where all the PropertyNodes/SkinPropertyNodes have not been completely resolved. A
   * raw StyleNode has trRuleRef properties, trPropertyRef property values, etc. A fully
   * resolved StyleNode only has propertyNodes and skinPropertyNodes, because everything 
   * else has been resolved into those two things.
   * @param context The StyleContext
   * @param forIconNode if you are resolving the styles for an IconNode, this is true.
   * @param styleSheets The StyleSheetNodes to use for resolving the style,
   *          sorted from lowest to highest precedence.
   * @param resolvedStyles The set of already resolved styles, hashed by
   *          selector
   * @param resolvedNamedStyles The set of already resolved named styles,
   *          hashed by name.
   * @param includesStack The stack of included style selectors which have
   *          already been visited.  Used to detect circular dependencies.
   * @param namedIncludesStack The stack of included style names which have
   *          already been visited.  Used to detect circular dependencies.
   * @param id The selector or name of the style to resolve
   * @param isNamed A boolean indicating whether the id is a name or selector.
   * @param cacheId cache id for the node
   * @param clientRule client rule if any for the node
   * @return Returns the fully resolved StyleNode, or null if the style
   *           could not be resolved.  Also, as a side-effect, the
   *           resolved style is stored in the appropriate resolved style
   *           Map.
   */
  private StyleNode _resolveStyleNode(
    StyleContext           context,
    boolean                forIconNode,
    StyleSheetList         styleSheets,
    Map<String, StyleNode> resolvedStyles,
    Map<String, StyleNode> resolvedNamedStyles,
    Deque<String>          includesStack,
    Deque<String>          namedIncludesStack,
    String                 id,
    boolean                isNamed,
    String                 cacheId,
    String                 clientRule
    )
  {
    assert (styleSheets != null);
    assert (resolvedStyles != null);
    assert (resolvedNamedStyles != null);
    assert (id != null);

    // First, let's check to see if we've already got a StyleNode for this id
    StyleNode style = null;
    String selector = null;
    String name = null;

    if (cacheId == null) cacheId = id;

    if (isNamed)
    {
      style = resolvedNamedStyles.get(cacheId);
      name = id;
    }
    else
    {
      style = resolvedStyles.get(cacheId);
      selector = id;
    }

    // If we've already got a style, return it and we're done!
    if (style != null)
    {
      // FIXME: AdamWiner - _ERROR_STYLE_NODE is in fact never used!
      
      // We use _ERROR_STYLE_NODE for internal error tracking, but we
      // never return it - return null instead.
      if (style == _ERROR_STYLE_NODE)
        return null;

      return style;
    }

    // Next, make sure we don't have a circular dependency
    if ((isNamed && _stackContains(namedIncludesStack, cacheId)) ||
        (!isNamed && _stackContains(includesStack, cacheId)))
    {
      if (_LOG.isWarning())
        _LOG.warning(_CIRCULAR_INCLUDE_ERROR + id);
      return null;
    }

    // Create the StyleEntry that we're going to use to store properties
    // as we are resolving this style
    StyleEntry entry = new StyleEntry(selector, name, clientRule);

    // Push this style onto the appropriate include stack
    if (isNamed)
    {
      if (namedIncludesStack == null)
        namedIncludesStack = new ArrayDeque<String>();

      namedIncludesStack.push(cacheId);
    }
    else
    {
      if (includesStack == null)
        includesStack = new ArrayDeque<String>();

      includesStack.push(cacheId);
    }

    // styleSheets.styleNodes(id, isNamed) returns a List of StyleNodes that match the StyleContext
    // and have the same selector name. For example, if the css files contains 
    // .someStyle {color: red} .someStyle {font-size: 11px}
    // you will get two StyleNodes, and the properties will get merged together.
    List<StyleNode> nodeList = styleSheets.styleNodes(cacheId, isNamed);
    
    // get the StyleNodes from each iconNodeList and add it to the StyleNode list
    if (forIconNode)
    {
      List<IconNode> iconNodeList = styleSheets.iconNodes(cacheId);

      // protect against null - 
      // iconNodeList could be null if in SkinStyleSheetParserUtils 
      // we thought a selector was an icon because it ended in -icon and we created an IconNode.
      // But we also saw that it had no 'content', so we created a StyleNode.
      // Really this is a mis-named style selector (should have ended with -icon-style),
      // and this resolving work is wasted cycles.
      if (iconNodeList != null)
      {
        for (IconNode iconNode: iconNodeList)
        {
          StyleNode sNode = iconNode.getStyleNode();

          if (sNode != null)
          {
            if (nodeList == null)
              nodeList = new ArrayList<StyleNode>(iconNodeList.size());
            nodeList.add(sNode);
          }
        }
      }        
    }
    _resolveStyleWork(context, id, forIconNode, styleSheets, resolvedStyles, resolvedNamedStyles,  
                      includesStack, namedIncludesStack, entry, nodeList);
    
    // Pop the include stack
    if (isNamed)
    {
      namedIncludesStack.pop();
    }
    else
    {
      includesStack.pop();
    }

    // Set<String> blackList = _DESTYLED_SUPPRESSED_PROPERTIES;
    //Set<String> blackList = _IE_SUPPRESSED_PROPERTIES;
    Set<String> blackList = null;
      
    StyleNode resolvedNode = entry.toStyleNode(blackList);

    // If we got a node, add it in to our list
    if (resolvedNode != null)
    {
      // cache already resolved styles so we don't
      // resolve them again. This saves (a lot of) time.
      // TODO: AdamWiner: entry.toStyleNode() will return
      // null if it's an empty style.  But that means
      // that this cache doesn't get used, so we end
      // up hammering on these.  This doesn't appear
      // to be a performance issue at this time.
      String namedStyle = resolvedNode.getName();
      if (namedStyle != null)
      {
        resolvedNamedStyles.put(resolvedNode.getId(), resolvedNode);
      }
      else 
      {
        String selectorStyle = resolvedNode.getSelector();
        if (selectorStyle != null)
        {
          resolvedStyles.put(resolvedNode.getId(), resolvedNode);
        }
      }
    }
    
    // Convert the StyleEntry to a StyleNode and return it
    return resolvedNode;
  }

  private void _resolveStyleWork(
    StyleContext           context,
    String                 id,
    boolean                forIconNode,
    StyleSheetList         styleSheets,
    Map<String, StyleNode> resolvedStyles,
    Map<String, StyleNode> resolvedNamedStyles,
    Deque<String>          includesStack,
    Deque<String>          namedIncludesStack,
    StyleEntry             entry,
    List<StyleNode>        nodeList)
  {
    if (nodeList != null)
    {
      for (StyleNode node : nodeList)
      {
        // We've got a match!  We need to do the following:
        // 0. Check to see whether we need to reset our properties.
        // 1. Resolve any included styles, and shove those properties
        //    into our StyleEntry.
        // 2. Resolve any included properties, and shove those properties
        //    into our StyleEntry.
        // 3. Remove all properties that were inhibited.
        // 4. Shove all properties from the matching StyleNode into our
        //    StyleEntry, overwriting included values
        // 5. Shove all skin properties from the matching StyleNode into our
        //     StyleEntry, overwriting included values
        // -= Simon Lessard =-
        // FIXME: That sequence looks buggy. If more than 1 matching node 
        //        is found, then the included properties of the second will
        //        have priority over the properties found at step 5 on the
        //        first node, which is most likely incorrect.
        //
        //        A possible fix would be to put entries from the 5 steps 
        //        into 5 different lists then resolve all priorities at the 
        //        end.
        
        // 0. Reset properties?
        if (node.__getResetProperties() || node.isInhibitingAll())
          entry.resetProperties();
    
        // 1. Resolve included styles
        Iterable<IncludeStyleNode> includedStyles = node.getIncludedStyles();
        for (IncludeStyleNode includeStyle : includedStyles)
        {
          String includeID = null;
          boolean includeIsNamed = false;

          if (includeStyle.getName() != null)
          {
            includeID = includeStyle.getName();
            includeIsNamed = true;
          }
          else
          {
            includeID = includeStyle.getSelector();
          }
        
          // for include styles we do not support selectors and properties within client rules
          // this is because, we cannot determine which selector or property should be applied,
          // as the client rules are resolved on the browser.
          // so we look for a selector or property without any client rules.
          // thus pass null for cacheId and clientRule
          StyleNode resolvedNode = _resolveStyleNode(context, forIconNode,
                                                 styleSheets,
                                                 resolvedStyles,
                                                 resolvedNamedStyles,
                                                 includesStack,
                                                 namedIncludesStack,
                                                 includeID,
                                                 includeIsNamed,
                                                 null,
                                                 null);

          if (resolvedNode != null)
            _addIncludedProperties(entry, resolvedNode);
          else 
          {
            // Fortunately this is an uncommon usecase
            // af|foo::some-icon {content: url(); width:16px; height:16px} 
            // // In SkinStyleSheetParserUtils, we are not sure if this is an icon or style 
            // // since there is no explicit 'content' attr. So we create both an Icon and a Style.
            // af|bar::some-icon {-tr-rule-ref: selector("af|foo");} 
            if (_LOG.isFinest() && !forIconNode && StyleUtils.isIcon(includeID) && 
                StyleUtils.isIcon(id))
            {
                _LOG.finest(id + " is being written to the CSS file " +
                  "even though it is likely a Skin Icon Object, not a style.");
  

            }
          }
        }
    
    
        // 2. Resolve included properties
        // color: -tr-property-ref(".AFDefaultFont:alias","background-color");
        Iterable<IncludePropertyNode> includedProperties = node.getIncludedProperties();
        for (IncludePropertyNode includeProperty : includedProperties)
        {
          StyleNode resolvedNode = 
            _resolveIncludedProperties(context, forIconNode, styleSheets, resolvedStyles, 
                                       resolvedNamedStyles, includesStack, namedIncludesStack, 
                                       includeProperty);
          if (resolvedNode != null)
          {
            _addIncludedProperty(entry,
                                 resolvedNode,
                                 includeProperty.getPropertyName(),
                                 includeProperty.getLocalPropertyName());
          }
        }
        
        // 2'. Resolve included properties in a composite property value.
        // e.g., border: 1px solid -tr-property-ref(".AFDarkColor:alias",color);
        // e.g., background: -ms-linear-gradient(top, -tr-property-ref(".AFRed:alias", "color") 0%, -tr-property-ref(".AFGreen:alias","color") 100%);
        Iterable<EmbeddedIncludePropertyNode> embeddedIncludeProperties = 
          node.getEmbeddedIncludeProperties();
        for (EmbeddedIncludePropertyNode embeddedInclude : embeddedIncludeProperties)
        {
          // get each IncludePropertyNode, and resolve it.        
          Map<String, IncludePropertyNode> ipNodes = embeddedInclude.getIncludedProperties();
          Map<String, String> resolvedValues = new HashMap<String, String>();
          
          Iterator<Map.Entry<String,IncludePropertyNode>> entryIter = ipNodes.entrySet().iterator();
          
          while (entryIter.hasNext())
          {
            Map.Entry<String,IncludePropertyNode> currEntry = entryIter.next();
            String placeHolder = currEntry.getKey();
            IncludePropertyNode includeProperty = currEntry.getValue();
            
            StyleNode resolvedNode = _resolveIncludedProperties(context, 
                                                                forIconNode, 
                                                                styleSheets, 
                                                                resolvedStyles,
                                                                resolvedNamedStyles, 
                                                                includesStack, 
                                                                namedIncludesStack,
                                                                includeProperty);
              
            if (resolvedNode != null)
            {
              List<PropertyNode> pNodes = _getIncludedPropertyNodes(
                                            resolvedNode,
                                            includeProperty.getPropertyName(),
                                            includeProperty.getLocalPropertyName());
              // stores all the resolved 'values'. we will concat these values when we
              // create the PropertyNode because these are composite values, like "1px solid red"
              if (pNodes != null && !pNodes.isEmpty())
                resolvedValues.put(placeHolder, pNodes.get(0).getValue());
            }
          }
          
          // here we are. We have processed one EmbeddedIncludePropertyNode's includePropertyNodes.
          // now we need to add to the entry: 
          if (!resolvedValues.isEmpty() || embeddedInclude.getPropertyValues().hasNext())
          {
            String compositeValue = _createCompositeValue(embeddedInclude, resolvedValues);
            String localName = embeddedInclude.getLocalPropertyName();
            PropertyNode propertyNode = new PropertyNode(localName,
                                                         compositeValue);
            
            if (!(localName.startsWith(_TR_PROPERTY_PREFIX)))
              entry.addProperty(propertyNode);
            else
              entry.addSkinProperty(propertyNode);
          }

        }
    
        // 3. Check inhibited properties
        Iterable<String> inhibitedProperties = node.getInhibitedProperties();
        for (String inhibitedPropertyName : inhibitedProperties)
        {
          entry.removeProperty(inhibitedPropertyName);
          entry.removeSkinProperty(inhibitedPropertyName);
        }
        
    
        // 4. Add non-included properties
        Iterable<PropertyNode> properties = node.getProperties();
        for (PropertyNode propertyNode : properties)
        {
          entry.addProperty(propertyNode);
          
        }
        
        // 5. Add non-included skin property node properties
        Iterable<PropertyNode> skinProperties = node.getSkinProperties();
        for (PropertyNode skinPropNode : skinProperties)
        {
          entry.addSkinProperty(skinPropNode);
        }

      }
    }
  }

  /**
   * Builds the composite value of the property that has one or more -tr-property-ref blocks
   *  used in it.
   *  @param node The EmbeddedIncludePropertyNode that has info about all the -tr-property-refs
   *    and other non-whitespace segments in the entire value
   *  @param values The map of placeholder token to resolved value of the respective -tr-property-ref
   *    segment. The placeholder is used to identify the position in the whole value where the 
   *    different -tr-property refs existed.
   */
  private String _createCompositeValue(
    EmbeddedIncludePropertyNode node, 
    Map<String, String> values)
  {
    StringBuilder builder = new StringBuilder();
    
    Iterator<String> propertyValues = node.getPropertyValues();
    while (propertyValues.hasNext())
    {
      String valueFrag = propertyValues.next();
      // the placeholder tokens will be of form -tr-0, -tr-1, -tr-2
      if (valueFrag.startsWith(_TR_PROPERTY_PREFIX))
      {
        String resolvedValue = values.get(valueFrag);
        // sometimes the alias referenced by -tr-property-ref may not be defined, so resolved value
        //  can be null
        if (resolvedValue != null)
        {
          builder.append(resolvedValue);
          if (propertyValues.hasNext())
            builder.append(" ");
        }
      }
      else
      {
        builder.append(valueFrag);
        if (propertyValues.hasNext())
          builder.append(" ");
      }
    }

    return builder.toString();
  }

  private StyleNode _resolveIncludedProperties(
    StyleContext           context,
    boolean                forIconNode,
    StyleSheetList         styleSheets,
    Map<String, StyleNode> resolvedStyles,
    Map<String, StyleNode> resolvedNamedStyles,
    Deque<String>          includesStack, 
    Deque<String>          namedIncludesStack,
    IncludePropertyNode    includeProperty)
  {
    String includeID = null;
    boolean includeIsNamed = false;
    
    if (includeProperty.getName() != null)
    {
      includeID = includeProperty.getName();
      includeIsNamed = true;
    }
    else
    {
      includeID = includeProperty.getSelector();
    }
    
    // for include styles we do not support selectors and properties within client rules
    // this is because, we cannot determine which selector or property should be applied,
    // as the client rules are resolved on the browser.
    // so we look for a selector or property without any client rules.
    // thus pass null for cacheId and clientRule
    StyleNode resolvedNode = _resolveStyleNode(context, forIconNode,
                                           styleSheets,
                                           resolvedStyles,
                                           resolvedNamedStyles,
                                           includesStack,
                                           namedIncludesStack,
                                           includeID,
                                           includeIsNamed,
                                           null,
                                           null);
    return resolvedNode;
  }

  
  // Add included properties. These are css properties and skin properties (server-side properties).
  private void _addIncludedProperties(
    StyleEntry entry,
    StyleNode  node
    )
  {
    if (node == null)
      return;

    Iterable<PropertyNode> properties = node.getProperties();
    for (PropertyNode propertyNode : properties)
    {
      if (propertyNode != null)
      {
        entry.addProperty(propertyNode);
      }
    }
    
    // server-side skin properties
    Iterable<PropertyNode> skinProperties = node.getSkinProperties();
    for (PropertyNode skinPropertyNode : skinProperties)
    {
      entry.addSkinProperty(skinPropertyNode);
    }
  }

  // resolves -tr-property-ref, and adds the resolved PropertyNode to the StyleEntry.
  private void _addIncludedProperty(
    StyleEntry entry,
    StyleNode  node,
    String     propertyName,
    String     localPropertyName
    )
  {
    if (node == null)
      return;
    
    // localPropertyName is the value we are setting.
    // if we have color: -tr-property-ref(".AFFoo:alias","background-color"), then 
    // localPropertyName is color.
    if (localPropertyName != null)
    {
      // check to see if localPropertyName is a skin property like '-tr-show-last-item',
      // as opposed to a regular css property like 'color' or 'font-size'.
      if (localPropertyName.startsWith(_TR_PROPERTY_PREFIX))
      {
        // these are currently StyleNode propertyNodes, but they may actually be skin properties.
        // if we have af|breadCrumbs {-tr-show-last-item: -tr-property-ref(".AFFoo:alias")},
        // and .AFFoo:alias contains only property nodes, like color: red.
        // this gets parsed and set on StyleNode as a IncludePropertyNode, not a SkinStyleNode,
        // then it gets resolved as properties.
        Iterable<PropertyNode> properties = node.getProperties();
        List<PropertyNode> includedProperties = 
          _getIncludedProperties(propertyName, localPropertyName, properties);
        for (PropertyNode propertyToAdd : includedProperties)
        {
          entry.addSkinProperty(propertyToAdd);
        }
        
        // most likely this block of code will run instead of the above code
        // .AFFoo:alias {-tr-show-last-item: true;} 
        // af|breadCrumbs{-tr-show=last-item: -tr-property-ref(".AFFoo:alias"); 
        // .AFFoo:alias's -tr-show-last-item is in a SkinPropertyNode, not a PropertyNode
        Iterable<PropertyNode> skinProperties = node.getSkinProperties();
        List<PropertyNode> includedSkinProperties = 
          _getIncludedProperties(propertyName, localPropertyName, skinProperties);
        for (PropertyNode propertyToAdd : includedSkinProperties)
        {
          entry.addSkinProperty(propertyToAdd);
        }
       
      }
      else
      {
        // regular css properties (not skin server-side properties)...
        // loop through all the properties and find a match. then merge.
        Iterable<PropertyNode> properties = node.getProperties();
        List<PropertyNode> includedProperties = 
          _getIncludedProperties(propertyName, localPropertyName, properties);
        for (PropertyNode propertyToAdd : includedProperties)
        {
          entry.addProperty(propertyToAdd);
        }
      }

    }

  }
  
  // resolves -tr-property-ref, and returns the resolved PropertyNodes
  private List<PropertyNode> _getIncludedPropertyNodes(
    StyleNode  node,
    String     propertyName,
    String     localPropertyName
    )
  {
    if (node == null || localPropertyName == null)
      return Collections.emptyList();
    

    // check to see if localPropertyName is a skin property like '-tr-show-last-item',
    // as opposed to a regular css property like 'color' or 'font-size'.
    if (localPropertyName.startsWith(_TR_PROPERTY_PREFIX))
    {
      Iterable<PropertyNode> properties = node.getProperties();
      List<PropertyNode> includedProperties = 
        _getIncludedProperties(propertyName, localPropertyName, properties);

      
      // most likely this block of code will run instead of the above code
      // .AFFoo:alias {-tr-show-last-item: true;} 
      // af|breadCrumbs{-tr-show=last-item: -tr-property-ref(".AFFoo:alias"); 
      // .AFFoo:alias's -tr-show-last-item is in a SkinPropertyNode, not a PropertyNode
      Iterable<PropertyNode> skinProperties = node.getSkinProperties();
      List<PropertyNode> pNodes = _getIncludedProperties(propertyName, localPropertyName, skinProperties);
      includedProperties.addAll(pNodes);
      return includedProperties;
     
    }
    else
    {
      // regular css properties (not skin server-side properties)...
      // loop through all the properties and find a match. then merge.
      Iterable<PropertyNode> properties = node.getProperties();
      List<PropertyNode> includedProperties = 
        _getIncludedProperties(propertyName, localPropertyName, properties);
      return includedProperties;
    }

   

  }

  // used when resolving -tr-property-ref
  // given a list of PropertyNodes to traverse, find the one whose name matches the propertyName.
  // then we assign the value to the localPropertyName.
  // .AFFoo:alias {color: red} af|test {background-color: -tr-property-ref(".AFFoo:alias", "color");
  // localPropertyName is background-color
  // propertyName is color
  // properties is .AFFoo:alias's properties
  private List<PropertyNode> _getIncludedProperties(
    String propertyName,
    String localPropertyName,
    Iterable<PropertyNode> properties)
  {
    List<PropertyNode> propertyNodesToAdd = new ArrayList<PropertyNode>();

    for (PropertyNode property : properties)
    {
      // must first find a match of the includeProperty's propertyName 
      // and the property node's name
      if (propertyName.equals(property.getName()))
      {
        if (!propertyName.equals(localPropertyName))
        {
          property = new PropertyNode(localPropertyName, property.getValue());
        }
        // if propertyName is equal to localPropertyName, we can simply reuse the 
        // property node instead of creating a new PropertyNode.        
        // since we found a match, we include this PropertyNode into the entry.    
        //entry.addIncludedSkinProperty(property);
        propertyNodesToAdd.add(property);
      }
    }
    return propertyNodesToAdd;
  }
  
  // Static utility method used by StyleEntry & FontSizeConverter
  // to get the real font size, taking relative size into account
  static PropertyNode _getRealFontSize(
    PropertyNode property,
    int relativeFontSize
    )
  {
    if (relativeFontSize == 0)
      return property;

    String value = property.getValue();

    // Strip off units
    String units = _POINT_UNITS;

    if (value.endsWith(_POINT_UNITS))
    {
      value = value.substring(0, value.length() - _POINT_UNITS.length());
      units = _POINT_UNITS;
    }
    else if (value.endsWith(_PIXEL_UNITS))
    {
      value = value.substring(0, value.length() - _PIXEL_UNITS.length());
      units = _PIXEL_UNITS;
    }

    int size = 0;

    try
    {
      size = Integer.parseInt(value);
    }
    catch (NumberFormatException e)
    {
      // there are valid values like small, large etc for font-size
      // we do not have to process those
      // so just log this and return the original PropertyNode
      _LOG.fine("Could not parse font size: " + value + ". Returning the property as is.");

      return property;
    }

    size += relativeFontSize;

    String newValue = IntegerUtils.getString(size) + units;
    return new PropertyNode(_FONT_SIZE_NAME, newValue);
  }

  /**
   * Given a String that denotes a width or height css style
   * property, return an Integer. This will strip off 'px' from
   * the string if there is one.
   * e.g., if propertyValue is '7px', the Integer 7 will be returned.
   * @param propertyValue - this is a string that indicates width
   * or height.
   * @return Integer
   */
  private static Integer _convertPxDimensionStringToInteger(
    String propertyValue)
  {
    int pxPosition = propertyValue.indexOf("px");
    if (pxPosition > -1)
      propertyValue = propertyValue.substring(0, pxPosition);
    return Integer.valueOf(propertyValue);
  }
  
  // Tests whether the specified property value is an "url" property.
  private static boolean _isURLValue(String propertyValue)
  {
    // URL property values start with "url("
    return propertyValue.startsWith("url(");
  }
  
  /**
   * Trim the leading/ending quotes, if any.
   */
  private static String _trimQuotes(String in)
  {
    if ( in == null )
      return in;

    boolean startsWithDoubleQuote = in.startsWith( "\"" );
    boolean startsWithSingleQuote = in.startsWith( "\'" );
    boolean endsWithDoubleQuote = in.endsWith( "\"" );
    boolean endsWithSingleQuote = in.endsWith( "\'" );
    
    if (( startsWithDoubleQuote && endsWithSingleQuote ) ||
       ( startsWithSingleQuote && endsWithDoubleQuote ))
    {
      if (_LOG.isWarning())
        _LOG.warning("ERR_PARSING", in);
    }
                                                          
    if ( startsWithDoubleQuote && endsWithDoubleQuote )
      return in.substring( 1, in.length() - 1 );
    if ( startsWithSingleQuote && endsWithSingleQuote )
      return in.substring( 1, in.length() - 1 );
    
    return in;
  }
  
  // Returns the uri portion of the url property value
  private static String _getURIString(String propertyValue)
  {
    assert(_isURLValue(propertyValue));

    int uriEnd = propertyValue.indexOf(')');
    String uri = propertyValue.substring(4, uriEnd);

    return _trimQuotes(uri);
  }
  
  // Tests whether the value is present in the (possibly null) stack.
  private static boolean _stackContains(Deque<?> stack, Object value)
  {
    if (stack == null)
      return false;

    return stack.contains(value);
  }

  /**
   * Simple encapsulation of an array of StyleSheetNodes, to
   * support maintaining a cache of (name | selector --> StyleNode)
   */
  private static class StyleSheetList
  {
    public StyleSheetList(StyleSheetNode[] styleSheets)
    {
      _styleSheets = styleSheets;
    }
    
    /**
     * @return true if the list is empty
     */
    public boolean isEmpty()
    {
      return (_styleSheets == null) || (_styleSheets.length == 0);
    }

    /**
     * Forcibly caches IDs.  Caching IDs is only useful if
     * we're going to be retrieving a lot (generally, all)
     * of the nodes at some point.
     */
    public void cacheStyleIds()
    {
      // Typically, there are a lot more selector nodes than
      // name nodes.  These numbers come from some statistics gathered
      // on Trinidad and other libraries
      _nameNodes = new HashMap<String, List<StyleNode>>(512);
      _selectorNodes = new HashMap<String, List<StyleNode>>(4096);
            
      for (int i = 0; i < _styleSheets.length; i++)
      {
        StyleSheetNode styleSheet = _styleSheets[i];
        Iterable<StyleNode> styleNodeList = styleSheet.getStyles();

        for (StyleNode node : styleNodeList)
        {
          // Add to the map where the key is the 'name' or 'selector name', and the value
          // is a List of StyleNodes that have that same 'name' or 'selector name'. This 
          // is in case someone created the same selector more than once, or overrode a selector.
          if (node.getName() != null)
          {
            _addToStyleMap(_nameNodes, node, node.getId());
          }
          else
          {
            _addToStyleMap(_selectorNodes, node, node.getId());
          }
         
        }
      }

    }
    

    /**
     * Forcibly caches IDs.  Caching IDs is only useful if
     * we're going to be retrieving a lot (generally, all)
     * of the nodes at some point.
     */
    public void cacheIconIds()
    {
      _iconNodes = new HashMap<String, List<IconNode>>(1024);
      
      
      for (int i = 0; i < _styleSheets.length; i++)
      {
        StyleSheetNode styleSheet = _styleSheets[i];
        Iterable<IconNode> iconNodeList = styleSheet.getIcons();
        for (IconNode node : iconNodeList)
        {
          // Add to the map where the key is the 'icon name', and the value
          // is a List of StyleNodes that have that same 'icon name'. This 
          // is in case someone created the same selector more than once, or overrode a selector.
          if (node.getIconName() != null)
            _addToIconMap(_iconNodes, node, node.getIconName()); 
        }
      }
    }
    
    /**
     * Return a List of StyleNodes based on the "id" (either
     * a selector or name).
     * Call cacheStyleIds first if you want everything to be cached.
     * @param cacheId for the selector or name
     * @param isNamed if true, interpret "id" as a name, otherwise
     *   as a selector
     * @return the list of StyleNodes (potentially null)
     */
    public List<StyleNode> styleNodes(String cacheId, boolean isNamed)
    {
      if (_styleSheets == null)
        return Collections.emptyList();
      // _nameNodes and _selectorNodes are initialized in cacheStyleIds
      Map<String, List<StyleNode>> m = isNamed ? _nameNodes : _selectorNodes;
      // Cached version - go to the Map
      if (m != null)
        return m.get(cacheId);
      
      // Uncached version - iterate through everything and build up
      // the List, but do not cache it.
      // This gets called if you do not call cacheStyleIds() first.
      List<StyleNode> l = new ArrayList<StyleNode>();
      for (int i = 0; i < _styleSheets.length; i++)
      {
        StyleSheetNode styleSheet = _styleSheets[i];
        Iterable<StyleNode> styleNodeList = styleSheet.getStyles();

        for (StyleNode node : styleNodeList)
        {
          if (cacheId.equals(node.getId()))
            l.add(node);
        }
      }
      
      return l;
    }
   
    /**
     * Return a List of IconNodes based on the "id"
     * @param cacheId for the icon selector name
     * @return the list of IconNodes (potentially null)
     */
    public List<IconNode> iconNodes(String cacheId)
    {
      if (_styleSheets == null)
        return Collections.emptyList();
      Map<String, List<IconNode>> m = _iconNodes;
      // Cached version - go to the Map
      if (m != null)
        return m.get(cacheId);
      
      // Uncached version - iterate through everything and build up
      // the List, but do not cache it.
      // This gets called if you do not call cacheIconIds() first.
      List<IconNode> l = new ArrayList<IconNode>();
      for (int i = 0; i < _styleSheets.length; i++)
      {
        StyleSheetNode styleSheet = _styleSheets[i];
        Iterable<IconNode> iconNodeList = styleSheet.getIcons();

        for (IconNode node : iconNodeList)
        {
          if (cacheId.equals(node.getStyleNode().getId()))
            l.add(node);
        }
      }
      
      return l;
    } 
    /**
     * @return an unmodifiable list of all StyleSheetNodes
     */
    public List<StyleSheetNode> styleSheets()
    {
      if (_styleSheets == null)
      {
        return Collections.emptyList();
      }
      else
      {
        return Collections.unmodifiableList(Arrays.asList(_styleSheets));
      }
    }

    static private void _addToStyleMap(
      Map<String, List<StyleNode>> m, 
      StyleNode                    node,
      String                       id)
    {
      List<StyleNode> l = m.get(id);
      if (l == null)
      {
        // you may see this id (aka selector) multiple times in the resolved skin.
        // The reasons could be: duplicated in the css file due to @rules, in one css file then 
        // another that extends that one.
        // the most I've seen is 6. But as new skins are created that extend old skins,
        // this number could increase.
        l = new ArrayList<StyleNode>(6);
        m.put(id, l);
      }
      
      l.add(node);
    }


    static private void _addToIconMap(
      Map<String, List<IconNode>> m, 
      IconNode                    node,
      String                      id)
    {
      List<IconNode> l = m.get(id);
      if (l == null)
      {
        // you may see this id (aka selector) multiple times in the resolved skin.
        // The reasons could be: duplicated in the css file due to @rules, in one css file then 
        // another that extends that one.
        // The most I've seen is 6. But as new skins are created that extend old skins,
        // this number could increase.
        l = new ArrayList<IconNode>(6);
        m.put(id, l);
      }
      
      l.add(node);
    }
    
    private Map<String, List<StyleNode>> _nameNodes;
    private Map<String, List<StyleNode>> _selectorNodes;
    private Map<String, List<IconNode>>  _iconNodes;
    
    private final StyleSheetNode[] _styleSheets;
  }
  
  // Comparator for StyleSheetNodes which sorts by variant specificity
  private static class StyleSheetComparator implements Comparator<StyleSheetNode>
  {
    public StyleSheetComparator(
      Locale locale,
      int direction,
      TrinidadAgent agent,
      int mode,
      StyleSheetNode[] styleSheets,
      AccessibilityProfile accessibilityProfile
      )
    {
      _direction = direction;
      _locale = locale;
      _agent = agent;
      _styleSheets = styleSheets;
      _mode = mode;
      _accProfile = accessibilityProfile;
    }

    public int compare(StyleSheetNode item1, StyleSheetNode item2)
    {
      if (item1 == item2)
        return 0;

      int match1 = item1.compareVariants(_locale, 
                                         _direction, 
                                         _agent, 
                                         _mode,
                                         _accProfile);
      
      int match2 = item2.compareVariants(_locale, 
                                         _direction, 
                                         _agent, 
                                         _mode,
                                         _accProfile);

      if (match1 == match2)
      {
        return _compareOrder(item1, item2);
      }

      if (match1 < match2)
        return -1;

      return 1;
    }
    
    @Override
    public boolean equals(Object o)
    {
      if (this == o)
        return true;
      
      if (!(o instanceof StyleSheetComparator))
        return false;
      
      StyleSheetComparator other = (StyleSheetComparator)o;
      
      return _direction == other._direction        &&
             _mode      == other._mode             &&
             _locale.equals(other._locale)         &&
             _accProfile.equals(other._accProfile) &&
             _agent.equals(other._agent)           &&
             _styleSheets.equals(other._styleSheets);
    }

    private int _compareOrder(Object item1, Object item2)
    {
      assert (item1 != item2);

      for (int i = 0; i < _styleSheets.length; i++)
      {
        StyleSheetNode styleSheet = _styleSheets[i];
        if (styleSheet == item1)
          return -1;
        if (styleSheet == item2)
          return 1;
      }

      // Huh?  This should never happen
      assert false;

      return 0;
    }

    private Locale _locale;
    private int    _direction;
    private TrinidadAgent  _agent;
    private int _mode;
    // We use the style sheet node array to determine the
    // precedence of two stylesheets with the same attributes.
    // Later style sheets take precedence over earlier ones
    private StyleSheetNode[] _styleSheets;
    private AccessibilityProfile _accProfile;
  }

  // Private style class that we use when we're building up
  // our list of styles for a particular end user environment.
  private static class StyleEntry
  {
    // The selector of the style
    public final String selector;

    // The name of the style
    public final String name;

    // client side rule such as @media, @keyframes etc
    public final String clientRule;

    // Create a StyleEntry with the specified selector, name
    public StyleEntry(String selector, String name, String clientRule)
    {
      this.selector = selector;
      this.name = name;
      this.clientRule = clientRule;
    }

    public void addSkinProperty(PropertyNode property)
    {
      if (_skinProperties == null)
        _skinProperties = new ArrayList<PropertyNode>(2);  
      
      // remove property if it already exists. 
      // We could just use a Set instead of an ArrayList, right?
      String name = property.getName();
      removeSkinProperty(name);
      
      _skinProperties.add(property);
    }
    // Add the specified property.
    // If we already have a PropertyNode with the same name as the
    // property, we remove the old value, as newly added properties
    // take precedence.
    public void addProperty(PropertyNode property)
    {
      if (_properties == null)
        _properties = new ArrayList<PropertyNode>(5);


      // Relative font sizes are a special case - they get added to
      // the _relativeFontSize value instead of to the _properties list.
      if (_isRelativeFontSize(property))
      {
        _addRelativeFontSize(property);
      }
      else if (_isRelativeColor(property))
      {
        _addRelativeColor(property);
      }
      else
      {
        // Remove the old property value before adding the new value
        String name = property.getName();
        removeProperty(name);

        _properties.add(property);
        _propertyCount++;

        // If we are setting an absolute font size, we override
        // any relative sizes that have already been specified
        if (name.equals(_FONT_SIZE_NAME))
          _relativeFontSize = 0;
      }
    }

    // Removes the property with the specified name
    public void removeProperty(String name)
    {
      if (_removeProperty(_properties, name))
        _propertyCount--;
    }
    
    // Removes the skin property with the specified name
    public void removeSkinProperty(String name)
    {
      if (_removeSkinProperty(_skinProperties, name))
        _skinPropertyCount--;
    }

    // Clears out all properties
    public void resetProperties()
    {
      _skinProperties = null;
      _skinPropertyCount = 0;
      _properties = null;
      _propertyCount = 0;
      _relativeFontSize = 0;
    }
    
    /**
     * Returns the filtered and trimmed PropertyNode[] for the properties
     * @param unfilteredProperties
     * @param blackList
     * @return
     */
    private static PropertyNode[] _filterAndTrimPropertyNodes(
      List<PropertyNode> unfilteredProperties, Set<String> blackList, int relativeFontSize)
    {
      int propertyCount = (unfilteredProperties != null) ?  unfilteredProperties.size() : 0;
      
      if (propertyCount == 0)
      {
        return _EMPTY_PROPERTY_NODE_ARRAY;
      }
      else
      { 
        // filter the properties
        PropertyNode[] filteredProperties = _filteredCopyInto(unfilteredProperties, blackList,
                                                              new PropertyNode[propertyCount]);
        
        // Adjust the font size based for relative font
        if (relativeFontSize != 0)
        {
          for (int i = 0; i < filteredProperties.length; i++)
          {
            PropertyNode property = filteredProperties[i];
            
            if (_FONT_SIZE_NAME.equals(property.getName()))
            {
              filteredProperties[i] = _getRealFontSize(property, relativeFontSize);
              break;
            }
          }
        }
        
        return filteredProperties;
      }
    }

    // Converts this StyleEntry to a StyleNode
    public StyleNode toStyleNode(Set<String> blackList)
    {
      PropertyNode[] properties     = _filterAndTrimPropertyNodes(_properties, blackList, _relativeFontSize);
      PropertyNode[] skinProperties = _filterAndTrimPropertyNodes(_skinProperties, null, 0);
                                                      
      if (properties.length + skinProperties.length == 0)
      {
        // no properties, so nothing to return
        return null;
      }
      else
      {
        // Create and return our StyleNode.  We don't need to specify
        // a name or included styles, as they have already been resolved.
        // StyleNode(name, selector, properties, skinProperties, includedStyles, includedProperties, inhibitedProperties)
        return new StyleNode(name, selector, clientRule, properties, skinProperties, null, null,null, null);
      }
    }

    // Removes the PropertyNode with the specified name from the
    // List of properties.  Note - we assume that the properties
    // List will contain at most one property with the specified
    // name.  Returns a boolean indicating whether the specified
    // property was found (and thus removed).
    private boolean _removeProperty(
        ArrayList<PropertyNode> properties, 
        String name)
    {
      if (properties == null)
        return false;

      for (int i = 0; i < properties.size(); i++)
      {
        PropertyNode property = properties.get(i);

        if ((property != null) && property.getName().equals(name))
        {
          // We don't actually remove the old value, just
          // null it out.  We do this to avoid the calls
          // to System.arraycopy() that would occur if we
          // actually removed the property.  For us, time
          // is more important than memory usage.
          properties.set(i, null);
          return true;
        }
      }

      return false;
    }

    // Removes the SkinPropertyNode with the specified name from the
    // List of properties.  Note - we assume that the properties
    // List will contain at most one property with the specified
    // name.  Returns a boolean indicating whether the specified
    // property was found (and thus removed).
    private boolean _removeSkinProperty(
        ArrayList<PropertyNode> properties, 
        String name)
    {
      if (properties == null)
        return false;

      for (int i = 0; i < properties.size(); i++)
      {
        PropertyNode property = properties.get(i);

        if ((property != null) && property.getName().equals(name))
        {
          // We don't actually remove the old value, just
          // null it out.  We do this to avoid the calls
          // to System.arraycopy() that would occur if we
          // actually removed the property.  For us, time
          // is more important than memory usage.
          properties.set(i, null);
          return true;
        }
      }

      return false;
    }
    
    /**
     * For each non-null PropertyNode in the source List, filter out all null items and any ProeprtyNode's whose
     * name appears in the blacklist
     * 
     * The returned Array might not be the initially passed array if filtering changed the number of elements
     */
    private static PropertyNode[] _filteredCopyInto(
      List<? extends PropertyNode> source,
      Set<String> blackList,
      PropertyNode[] target)
    {
      int copyIndex = 0;
      
      int sourceSize = source.size();
      
      for (int i = 0; i < sourceSize; i++)
      {
        PropertyNode property = source.get(i);
    
        if ((property != null) && ((blackList == null) || !blackList.contains(property.getName())))
        {
          target[copyIndex] = property;
          copyIndex++;
        }
      }
      
      // return the array, creating a new array if necessary
      if (sourceSize != copyIndex)
      {
        if (copyIndex != 0)
        {
          return Arrays.copyOf(target, copyIndex);          
        }
        else
        {
          return _EMPTY_PROPERTY_NODE_ARRAY;
        }
      }
      else
      {
        return target;
      }
    }

    // Is the property a relative font size?
    private boolean _isRelativeFontSize(PropertyNode property)
    {
      if (!_FONT_SIZE_NAME.equals(property.getName()))
        return false;

      String value = property.getValue();
      if ((value != null) && value.length() > 0)
      {
        char c = value.charAt(0);
        return ((c == '+') || (c == '-'));
      }

      return false;
    }

    // Is the property a relative color?
    private boolean _isRelativeColor(PropertyNode property)
    {
      // Relative colors start with "-/+#"
      String value = property.getValue();

      if (value == null)
        return false;

      int length = value.length();

      // Length should either be 8 ("+#RRGGBB") or 5 ("+#RGB")
      if ((length != 8) && (length != 5))
        return false;

      char c0 = value.charAt(0);

      return (((c0 == '+') || (c0 == '-')) && (value.charAt(1) == '#'));
    }

    // Add the relative font size into _relativeFontSize
    private void _addRelativeFontSize(PropertyNode property)
    {
      assert (_isRelativeFontSize(property));

      String value = property.getValue();
      boolean increment = (value.charAt(0) == '+');

      // Rip off the +/- and units
      if (value.endsWith(_POINT_UNITS))
        value = value.substring(1, value.length() - _POINT_UNITS.length());
      else if (value.endsWith(_PIXEL_UNITS))
        value = value.substring(1, value.length() - _PIXEL_UNITS.length());
      else
        value = value.substring(1);

      int size = 0;

      try
      {
        size = Integer.parseInt(value);
      }
      catch (NumberFormatException e)
      {
        return;
      }

      if (increment)
        _relativeFontSize += size;
      else
        _relativeFontSize -= size;
    }

    // Add the relative color property
    private void _addRelativeColor(PropertyNode property)
    {
      assert (_isRelativeColor(property));

      String relativeValue = property.getValue();
      boolean increment = (relativeValue.charAt(0) == '+');

      Color relativeColor = null;

      try
      {
        // Note CSS-specific parsing code here!
        relativeColor = CSSUtils.parseColor(relativeValue.substring(1));
      }
      catch (PropertyParseException e)
      {
        // This should have been logged when the document was first parsed.
        ;
      }

      if (relativeColor == null)
      {
        return;
      }

      // Now, get the current absolute value
      String absoluteValue = _getPropertyValue(property.getName());

      if (absoluteValue == null)
        return;

      // Try parsing the absolute value into a Color
      Color absoluteColor = null;

      try
      {
        // Note CSS-specific parsing code here!
        absoluteColor = CSSUtils.parseColor(absoluteValue);
      }
      catch (PropertyParseException e)
      {
        // This should have been logged when the document was first parsed.
        ;
      }

      if (absoluteColor == null)
        return;

      // Apply the relative value to the absolute value
      int red = absoluteColor.getRed();
      int green = absoluteColor.getGreen();
      int blue = absoluteColor.getBlue();

      if (increment)
      {
        red = Math.min((red + relativeColor.getRed()), 255);
        green = Math.min((green + relativeColor.getGreen()), 255);
        blue = Math.min((blue + relativeColor.getBlue()), 255);
      }
      else
      {
        red = Math.max((red - relativeColor.getRed()), 0);
        green = Math.max((green - relativeColor.getGreen()), 0);
        blue = Math.max((blue - relativeColor.getBlue()), 0);
      }

      // Convert the resolved color values into a #RRGGBB string
      Color resolvedColor = new Color(red, green, blue);
      String resolvedValue = CSSUtils.getColorValue(resolvedColor);

      // Finally, add in the resolved property node
      addProperty(new PropertyNode(property.getName(), resolvedValue));
    }

    // Returns the String value for the specified property, or null
    // if no value has been added
    private String _getPropertyValue(String name)
    {
      if (_properties != null)
      {
        for(PropertyNode property : _properties)
        {
          if ((property != null) && (name.equals(property.getName())))
            return property.getValue();
        }
      }

      return null;
    }

    private static final PropertyNode[] _EMPTY_PROPERTY_NODE_ARRAY = new PropertyNode[0];

    // The set of properties (PropertyNodes) defined by this style
    private ArrayList<PropertyNode> _properties;
    
    // The set of skinProperties defined by this style. These are server-side properties,
    // like -tr-show-last-item
    private ArrayList<PropertyNode> _skinProperties;

    // We keep count of the number of non-null values in each vector
    private int _propertyCount;
    private int _skinPropertyCount;

    // _relativeFontSize accumulates the total relative font size
    // from any "font-size" properties with relative values.
    private int _relativeFontSize;
  }

  private StyleSheetNode[] _styleSheets;
  private final String     _documentVersion;
  private final long       _documentTimestamp;
  
  private static final Set<String> _IE_SUPPRESSED_PROPERTIES = new HashSet<String>(Arrays.asList(
    "border-radius", "box-shadow"));
  
  
  private static final Set<String> _DESTYLED_SUPPRESSED_PROPERTIES = new HashSet<String>(Arrays.asList(
    "background-attachment", "background-color", "background-image", "background-position", "background-repeat", "background",
    "border-collapse", "border-color", "border-spacing", "border-style",
    "border-top", "border-right", "border-bottom", "border-left",
    "border-top-color", "border-right-color", "border-bottom-color", "border-left-color",
    "border-top-width", "border-right-width", "border-bottom-width", "border-left-width", "border-width",
    "border", "border-image", "border-radius",
    "caption-side",
    "color",
    "content",
    "cursor",
    "font-family", "font-size", "font-style", "font-variant", "font-weight", "font",
    "font-feature-settings", "font-kerning", "font-language-override", "font-size-adjust", "font-stretch", "font-variant-alternates",
    "font-variant-caps", "font-variant-east-asian", "font-variant-ligatures", "font-variant-numeric", "font-variant-position",
    "hanging-punctuation",
    "hyphens",
    "icon",
    "letter-spacing",
    "line-break",
    "list-style-image", "list-style-position", "list-style-type", "list-style",
    "margin-right", "margin-left", "margin-top", "margin-bottom", "margin",
    "marquee-direction", "marquee-loop", "marquee-speed", "marquee-style",
    "opacity",
    "outline-color", "outline-style", "outline-width", "outline", "outline-offset",
    "overflow-style", "overflow-wrap",
    "padding-top", "padding-right", "padding-bottom", "padding-left", "padding",
    "resize",
    "rotation", "rotation-point",
    "target-new", "target-position",
    "text-align", "text-align-last", "text-decoration", "text-indent", "text-justify", "text-transform", "text-overflow",
    "transition", "transition-delay", "transition-duration", "transition-property", "transition-timing-function",
    "vertical-align",
    "white-space",
    "word-break", "word-spacing", "word-wrap"));
  
  private static final String _FONT_SIZE_NAME = "font-size";

  // Unit strings.  For now, we only support points and pixels
  // for relative font sizes.
  private static final String _POINT_UNITS    = "pt";
  private static final String _PIXEL_UNITS    = "px";

  // A StyleNode used as a placeholder for a style which couldn't be resolved
  private static final StyleNode _ERROR_STYLE_NODE = new StyleNode("error",
                                                                   "error",
                                                                   null,
                                                                   null,
                                                                   null,
                                                                   null,
                                                                   null,
                                                                   null,
                                                                   null);

  // Error messages
  private static final String _CIRCULAR_INCLUDE_ERROR =
    "Circular dependency detected in style ";
  private static final String _TR_PROPERTY_PREFIX = "-tr-";
  private static final Pattern _INTEGER_PATTERN = Pattern.compile("\\d+(px)?");
  private static final TrinidadLogger _LOG = TrinidadLogger.createTrinidadLogger(StyleSheetDocument.class);
}

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
package org.apache.myfaces.trinidadinternal.renderkit.core.desktop;

import java.io.IOException;

import java.util.List;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;

import org.apache.myfaces.trinidad.bean.FacesBean;
import org.apache.myfaces.trinidad.bean.PropertyKey;
import org.apache.myfaces.trinidad.component.CollectionComponent;
import org.apache.myfaces.trinidad.component.UIXCollection;
import org.apache.myfaces.trinidad.component.UIXColumn;
import org.apache.myfaces.trinidad.component.UIXTable;
import org.apache.myfaces.trinidad.component.core.data.CoreColumn;
import org.apache.myfaces.trinidad.component.core.data.CoreTable;
import org.apache.myfaces.trinidad.context.RenderingContext;
import org.apache.myfaces.trinidad.render.CoreRenderer;
import org.apache.myfaces.trinidad.skin.Icon;
import org.apache.myfaces.trinidad.util.IntegerUtils;
import org.apache.myfaces.trinidadinternal.io.RepeatIdResponseWriter;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.OutputUtils;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.ShowDetailRenderer;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.SkinSelectors;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.TableRenderer;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.XhtmlConstants;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.XhtmlUtils;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.table.CellUtils;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.table.ColumnData;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.table.RenderStage;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.table.RowData;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.table.TableRenderingContext;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.table.TableSelectManyRenderer;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.table.TableUtils;
import org.apache.myfaces.trinidadinternal.renderkit.core.xhtml.table.TreeUtils;


public class DesktopTableRenderer
  extends TableRenderer
{
  /**
   * @todo Figure out if "height" is really being used;  it's
   *   not exposed on our tag, but it might be a "hidden" feature
   *   =-= awijeyek =-= height is used for server-side scrollable tables for ECM,
   *   but we don't support it beyond what is needed by ECM.
   */
  protected DesktopTableRenderer(FacesBean.Type type)
  {
    super(type);
  }

  @Override
  protected void findTypeConstants(FacesBean.Type type)
  {
    super.findTypeConstants(type);
    _summaryKey = type.findKey("summary");
    _heightKey = type.findKey("height");
    // Since height isn't really exposed, we won't really
    // have a key for it...
    if (_heightKey == null)
      _heightKey = PropertyKey.createPropertyKey("height");
    _allDetailsEnabledKey = type.findKey("allDetailsEnabled");
    _allDisclosed = new AllDetail(type, true);
    _allUndisclosed = new AllDetail(type, false);
    _autoSubmitKey = type.findKey("autoSubmit");
  }

  public DesktopTableRenderer()
  {
    this(CoreTable.TYPE);
  }

  @Override
  protected final void renderSingleRow(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                       UIComponent component)
    throws IOException
  {
    // This renders a whole bunch of <TH>..</TH> elements or <TD>..</TD>
    // elements depending on the RenderStage
    RenderStage renderStage = tContext.getRenderStage();
    int stage = renderStage.getStage();
    if (stage == RenderStage.COLUMN_HEADER_STAGE)
    {
      renderColumnHeader(context, rc, tContext, component);
      return;
    }

    // render the special columns, such as selection and details:
    int physicalColumn = renderSpecialColumns(context, rc, tContext, component, 0);

    _renderRegularColumns(context, tContext, component, physicalColumn);
  }

  /**
   * @todo Support autoSubmit!
   */
  protected void renderSelectionLinks(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                      UIComponent component)
    throws IOException
  {
    // Never render for empty tables
    if (tContext.getRowData().isEmptyTable())
      return;

    // =-=AEW For some odd reason, we want all the above rendering even if we don't
    // have select all or detail disclosure, just not this cell.
    if (hasControlBarLinks(context, rc, tContext, component))
    {
      ResponseWriter writer = context.getResponseWriter();
      writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);
      writer.startElement(XhtmlConstants.TABLE_DATA_ELEMENT, null);
      writer.startElement(XhtmlConstants.TABLE_ELEMENT, null);
      OutputUtils.renderLayoutTableAttributes(context, rc, "0", "100%");
      renderStyleClass(context, rc, SkinSelectors.AF_TABLE_SUB_CONTROL_BAR_STYLE);
      writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);
      writer.startElement(XhtmlConstants.TABLE_DATA_ELEMENT, null);
      writer.writeAttribute("nowrap", Boolean.TRUE, null);
      writer.writeAttribute("valign", XhtmlConstants.MIDDLE_ATTRIBUTE_VALUE, null);

      renderControlBarLinks(context, rc, tContext, component, false);

      writer.endElement(XhtmlConstants.TABLE_DATA_ELEMENT);
      writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
      writer.endElement(XhtmlConstants.TABLE_ELEMENT);
      writer.endElement(XhtmlConstants.TABLE_DATA_ELEMENT);
      writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
    }
  }

  /**
   * Should we render the select-all/none links?
   */
  protected boolean hasControlBarLinks(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                       UIComponent component)
    throws IOException
  {
    return tContext.hasSelectAll() ||
           ((tContext.getDetail() != null) && getAllDetailsEnabled(component, getFacesBean(component)));
  }

  /**
   *
   * @param context
   * @param arc
   * @param component
   * @param useDivider  whether to render a divider after all the links
   * @throws IOException
   */
  protected void renderControlBarLinks(FacesContext context, RenderingContext arc, TableRenderingContext trc,
                                       UIComponent component, boolean useDivider)
    throws IOException
  {
    FacesBean bean = getFacesBean(component);
    boolean hasAllDetails = ((trc.getDetail() != null) && getAllDetailsEnabled(component, bean));

    boolean needsDivider = false;
    if (trc.hasSelectAll())
    {
      String jsVarName = trc.getJSVarName();
      renderControlBarLink(context, arc, TreeUtils.callJSSelectAll(jsVarName, true), _SELECT_ALL_TEXT_KEY, null, true);
      renderControlBarLink(context, arc, TreeUtils.callJSSelectAll(jsVarName, false), _SELECT_NONE_TEXT_KEY, null,
                           hasAllDetails);
      needsDivider = true;

      TableSelectManyRenderer.renderScripts(context, arc, trc, isAutoSubmit(component, bean));
    }

    ResponseWriter writer = context.getResponseWriter();
    if (hasAllDetails)
    {
      delegateRenderer(context, arc, component, bean, _allUndisclosed);
      writer.writeText(LINKS_DIVIDER_TEXT, null);
      delegateRenderer(context, arc, component, bean, _allDisclosed);
      needsDivider = true;
    }

    if (useDivider && needsDivider)
    {
      writer.writeText(LINKS_DIVIDER_TEXT, null);
    }
  }

  protected final void renderControlBarLink(FacesContext context, RenderingContext rc, String onclick,
                                            String translationKey, String id, boolean hasDivider)
    throws IOException
  {
    ResponseWriter writer = context.getResponseWriter();
    writer.startElement("a", null);
    writer.writeAttribute(XhtmlConstants.ID_ATTRIBUTE, id, null);
    renderStyleClass(context, rc, SkinSelectors.NAV_BAR_ALINK_STYLE_CLASS);
    writer.writeAttribute("onclick", onclick, null);
    writer.writeURIAttribute("href", "#", null);

    Icon icon = rc.getIcon(getControlLinkIconName(translationKey));
    if (icon != null)
    {
      OutputUtils.renderIcon(context, rc, icon, rc.getTranslatedString(translationKey), null);
    }
    else
    {
      writer.writeText(rc.getTranslatedString(translationKey), null);
    }
    writer.endElement("a");

    if (hasDivider)
      writer.writeText(LINKS_DIVIDER_TEXT, null);
  }

  @Override
  protected void renderSubControlBar(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                     UIComponent component, boolean isUpper)
    throws IOException
  {
    if (!isUpper)
      return;

    RenderStage rs = tContext.getRenderStage();
    rs.setStage(RenderStage.SUB_CONTROL_BAR_STAGE);
    renderSelectionLinks(context, rc, tContext, component);
  }

  @Override
  protected void renderTableContent(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                    UIComponent component)
    throws IOException
  {
    if (getFacet(component, CoreTable.FOOTER_FACET) != null)
      tContext.setExplicitHeaderIDMode(true);

    ResponseWriter writer = context.getResponseWriter();
    UIComponent table = tContext.getTable();
    //    DataObject savedData      = tContext.getCurrentDataObject();
    RenderStage renderStage = tContext.getRenderStage();
    Object assertKey = null;
    assert ((assertKey = ((UIXCollection) table).getRowKey()) != null) || true;
    //
    // 2. Render the top / column header
    //
    // =-= ACW: if a table is very wide, then we render in a special mode
    // where the control bars and the table content are in different HTML
    // tables. see bug 2530006:
    boolean wideMode = "100%".equals(tContext.getTableWidth());
    if (wideMode)
    {
      // if we are in wideMode, we must close the outer table:
      writer.endElement(XhtmlConstants.TABLE_ELEMENT);
    }
    else
    {
      // the content table is a row in the overall table
      writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);
      writer.startElement(XhtmlConstants.TABLE_DATA_ELEMENT, null);
    }

    String height = getHeight(component, getFacesBean(component));
    final boolean useScroll = (height != null);
    String tableId = tContext.getTableId();

    writer.startElement(XhtmlConstants.TABLE_ELEMENT, null); // header table
    renderStyleClass(context, rc, SkinSelectors.AF_TABLE_CONTENT_STYLE);
    if (useScroll)
    {
      writer.writeAttribute(XhtmlConstants.ID_ATTRIBUTE, tableId + "_head", null);
      writer.writeAttribute(XhtmlConstants.STYLE_ATTRIBUTE, "table-layout:fixed;", null);
    }


    FacesBean bean = getFacesBean(table);
    String summary = getSummary(component, bean);

    Object cellPadding = getTablePadding(table);
    OutputUtils.renderDataTableAttributes(context, rc, cellPadding, "0", // cell spacing
                                          "0", //border
                                          "100%", //table width
                                          summary);

    _renderTableHeader(context, rc, tContext, table);

    // render the column header
    if (tContext.hasColumnHeaders())
    {
      renderStage.setStage(RenderStage.COLUMN_HEADER_STAGE);
      writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);

      renderColumnHeader(context, rc, tContext, component);
      writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
    }
    assert _assertCurrencyKeyPreserved(assertKey, table);

    // in scrollable tables we need to separate header, content and footer into 3 tables
    ColumnData colData = tContext.getColumnData();
    if (useScroll)
    {
      writer.endElement(XhtmlConstants.TABLE_ELEMENT); // end header table
      writer.startElement(XhtmlConstants.DIV_ELEMENT, null);
      // IE in standards compliant mode needs the "width:100%" for the vertical
      // scroll bars to appear:
      writer.writeAttribute(XhtmlConstants.STYLE_ATTRIBUTE,
                            "overflow:auto;overflow-x:hidden;width:100%;height:" + height, null);

      writer.startElement(XhtmlConstants.TABLE_ELEMENT, null); // start content table
      renderStyleClass(context, rc, SkinSelectors.AF_TABLE_CONTENT_STYLE);
      writer.writeAttribute(XhtmlConstants.ID_ATTRIBUTE, tableId + "_data", null);
      OutputUtils.renderLayoutTableAttributes(context, rc, "0", // cell spacing
                                              "0", // border
                                              "100%"); // table width
      String style = "padding-bottom:1px;";
      writer.writeAttribute(XhtmlConstants.STYLE_ATTRIBUTE, style, null);
      // add col tags to set widths
      for (int i = 0; i < colData.getColumnCount(); i++)
      {
        writer.startElement("col", null);
        writer.writeAttribute(XhtmlConstants.WIDTH_ATTRIBUTE, colData.getWidth(i), null);
        writer.endElement("col");
      }
    }


    //
    // 3. Render all the rows
    //
    renderStage.setStage(RenderStage.DATA_STAGE);
    renderTableRows(context, rc, tContext, component, bean);
    assert _assertCurrencyKeyPreserved(assertKey, table);

    // the content table is a row in the overall table
    writer.endElement(XhtmlConstants.TABLE_ELEMENT);
    if (useScroll)
    {
      // if we have a footer, then we've just closed the footer table, not the content table
      // The difference is that the content table is wrapped inside a DIV, so we have to close
      // that DIV only if we don't have footers
      UIComponent footer = getFacet(component, CoreTable.FOOTER_FACET);
      boolean hasColumnFooters = colData.getPhysicalIndexOfFirstFooter() >= 0;
      if (footer == null && !hasColumnFooters)
        writer.endElement(XhtmlConstants.DIV_ELEMENT);
      writer.startElement(XhtmlConstants.SCRIPT_ELEMENT, null);
      renderScriptDeferAttribute(context, rc);
      renderScriptTypeAttribute(context, rc);
      _writeScrollScript(context, rc, tableId);
      writer.endElement(XhtmlConstants.SCRIPT_ELEMENT);

    }
    if (wideMode)
    {
      // when we are in wide mode, we break the outer table into two and
      // insert the content table between the two. Now we have to start the
      // second half of the outer table. This second half will be used by a
      // repeating control bar:
      writer.startElement(XhtmlConstants.TABLE_ELEMENT, null);
      // ideally, the attributes on this table should be the same as on the
      // root table, rendered by super.renderAttributes(..). However, that
      // method writes out an ID_ATTR, which we do not want to do here:
      OutputUtils.renderLayoutTableAttributes(context, rc, "0", // cell spacing
                                              "0", // border
                                              "100%"); // table width
    }
    else
    {
      writer.endElement(XhtmlConstants.TABLE_DATA_ELEMENT);
      writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
    }
    // restore the saved data object
    //    tContext.setCurrentDataObject(savedData);

  }

  private void _writeScrollScript(FacesContext context, RenderingContext rc, String tableId)
    throws IOException
  {
    ResponseWriter writer = context.getResponseWriter();
    boolean previouslyNotRendered = (rc.getProperties().put(_IE_SCROLL_KEY, Boolean.TRUE) == null);
    if (previouslyNotRendered)
    {
      writer.write("function _uixAdjustWidths(){};\n" +

        "_uixAdjustWidths.addElement = function(tableId)\n" + "{\n" + "if (_uixAdjustWidths.elements == null)\n" +
        "{\n" + "_uixAdjustWidths.elements = new Array();\n" + "}\n" + "_uixAdjustWidths.elements.push(tableId);\n" +
        "};\n" + "_uixAdjustWidths.incrementIndex = function(headerList, index, increment)\n" + "{\n" +
        "var inc=parseInt(increment,10);\n" + "index+=inc;\n" +
        "while (index<headerList.length && headerList[index]!=null)\n" + "index++;\n" + "return index;\n" + "};\n" +

        "_uixAdjustWidths.setColWidths = function()\n" + "{\n" + "var elements = _uixAdjustWidths.elements;\n" +
        "for(var k=0; k<elements.length; k++)\n" + "{\n" + "var headerId=elements[k]+'_head';\n" +
        "var dataId=elements[k]+'_data';\n" + "var footerId=elements[k]+'_foot';\n" + "var headerList=new Array();\n" +
        "var firstRowCells=null;\n" + "if (document.getElementById(dataId) != null)\n" +
        "if (document.getElementById(dataId).rows != null)\n" + "if (document.getElementById(dataId).rows.length>0)\n" +
        "{\n" + "firstRowCells=document.getElementById(dataId).rows[0].cells;\n" + "var footerCells=null;" +
        "if (document.getElementById(footerId) != null) footerCells=document.getElementById(footerId).rows[0].cells;\n"
        // 1. fill headerList with actual headers

        // tr loop
        + "var rows=document.getElementById(headerId).getElementsByTagName('tr');\n" +
        "for (var i=0;i<rows.length;i++)\n" + "{\n"
        // th loop
        + "var headers=rows[i].getElementsByTagName('th');\n" + "var index=_uixAdjustWidths.incrementIndex(headerList, 0, 0);\n" // find the first empty position
        + "for (var j=0;j<headers.length;j++)\n" + "{\n" + "if (headers[j].colSpan >'1')\n" + "{\n" + "headerList.splice(index, parseInt(headers[j].colSpan,10));\n" // adds as many null elements as spanned columns
        + "index+=_uixAdjustWidths.incrementIndex(headerList, index, headers[j].colSpan);\n" + "}\n" + "else\n" +
        "{\n" + "headerList.splice(index,1,headers[j]);\n" // adds this header to the list of headers to be resized
        + "index+=_uixAdjustWidths.incrementIndex(headerList, index, 1);\n" + "}\n" + "}\n" + "}\n"
        // 2.  remove widths from headers
        + "for (var i=0;i<headerList.length;i++)\n" + "{\n" + "headerList[i].width='';\n" + "}\n"

        // 3. special : if no width has been specified for the main table, set one temporarily
        + "var outerTable=document.getElementById(elements[k]).getElementsByTagName('table')[0];\n" +
        "var noTableWidth=false;\n" + "if (outerTable.width=='' && outerTable.style.width=='')\n" + "{\n" +
        "noTableWidth=true;\n" + "outerTable.width='100%';\n" + "}\n"

        // 4. adapt header widths to better match content width
        // it's length-1, because we do not resize the last column (we let it take the remaining space,
        // a lot easier, because otherwise we should take care of the space used by the scrollbar
        + "if (headerList.length>0)\n" + "for (var i=0;i<firstRowCells.length-1;i++)\n" + "{\n"
        // setting width is a 2-pass process : the second corrects the first to take into account
        // differences induced by different padding/border settings
        + "headerList[i].style.width=parseInt(firstRowCells[i].offsetWidth,10) +'px';\n" +
        "headerList[i].style.width=(parseInt(firstRowCells[i].offsetWidth,10)*2-parseInt(headerList[i].offsetWidth,10) )+'px';\n" +
        "}\n"
        // same for footer cells, if we have some. This time we start at the end of the line and ignore the first cell

        // This is because the first rows without a footer are given an empty 1-cell footer with a colspan
        + "if (footerCells != null)\n" + "for (var i=0;i<footerCells.length-1;i++)\n" + "{\n" +
        " var footerIdx=footerCells.length-1-i;\n" + " var dataIdx=firstRowCells.length-1-i;\n" +
        " footerCells[footerIdx].style.width=parseInt(firstRowCells[dataIdx].offsetWidth,10) +'px';\n" +
        " footerCells[footerIdx].style.width=(parseInt(firstRowCells[dataIdx].offsetWidth,10)*2-parseInt(footerCells[footerIdx].offsetWidth,10) )+'px';\n" +
        "}\n" + "if (noTableWidth)\n" + "outerTable.width='';\n" + "}\n" + "}\n" + "};\n")

        ;
    }
    writer.write("_uixAdjustWidths.addElement('" + tableId + "');\n");
    writer.write(
      // bug 4635425:
      // this script should always run to support multiple scroll tables
      // with PPR:
      "if (document.readyState == 'complete')\n" + "{\n" + "_uixAdjustWidths.setColWidths();\n" + "}\n");
    if (previouslyNotRendered)
    {
      writer.write(
        // bug 4635425:
        // attach the onload handler only if it has not been attached before.
        // there are 3 cases:
        // 1. this script is run on initial page load
        // 2. this script is run on PPR
        // 3. this script is run both by initial page load and subsequent PPR
        "if (_uixAdjustWidths.attached == null)\n" + "{\n" + "_uixAdjustWidths.attached = true;\n" +
        "if (window.addEventListener) \n" +
        "window.addEventListener('load', _uixAdjustWidths.setColWidths, false); \n" + "else if (window.attachEvent)\n" +
        "window.attachEvent('onload', _uixAdjustWidths.setColWidths); \n" + "else if (document.getElementById)\n" + "window.onload=_uixAdjustWidths.setColWidths;\n" +

        "if (window.addEventListener) \n" +
        "window.addEventListener('resize', _uixAdjustWidths.setColWidths, false); \n" +
        "else if (window.attachEvent)\n" + "window.attachEvent('onresize', _uixAdjustWidths.setColWidths); \n" +
        "else if (document.getElementById)\n" + "window.onresize=_uixAdjustWidths.setColWidths;\n" + "}\n");
    }

  }


  // render the control bar
  @Override
  protected final void renderControlBar(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                        UIComponent component)
    throws IOException
  {
    UIComponent action = getFacet(component, CoreTable.ACTIONS_FACET);
    boolean tableNotEmpty = !tContext.getRowData().isEmptyTable();
    boolean hasNav = tContext.hasNavigation() && tableNotEmpty;

    if (hasNav || (action != null))
    {
      boolean isUpper = (tContext.getRenderStage().getStage() == RenderStage.UPPER_CONTROL_BAR_STAGE);

      ResponseWriter oldRW = null;

      try
      {
        // Install a RepeatIdResponseWriter for the lower bar
        // to ensure that IDs are unique
        if (!isUpper)
          oldRW = RepeatIdResponseWriter.install(context);

        ResponseWriter writer = context.getResponseWriter();
        // start control bar row
        writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);
        writer.startElement(XhtmlConstants.TABLE_DATA_ELEMENT, null);
        // start control bar
        String style = SkinSelectors.AF_TABLE_CONTROL_BAR_TOP_STYLE;
        if (!isUpper)
          style = SkinSelectors.AF_TABLE_CONTROL_BAR_BOTTOM_STYLE;

        writer.startElement(XhtmlConstants.TABLE_ELEMENT, null);
        OutputUtils.renderLayoutTableAttributes(context, rc, "0", "0", "0", "100%");
        renderStyleClass(context, rc, style);
        writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);

        if (action != null)
        {
          writer.startElement(XhtmlConstants.TABLE_DATA_ELEMENT, null);
          encodeChild(context, action);
          writer.endElement(XhtmlConstants.TABLE_DATA_ELEMENT);
        }
        writer.startElement(XhtmlConstants.TABLE_DATA_ELEMENT, null);
        writer.writeAttribute(XhtmlConstants.WIDTH_ATTRIBUTE, XhtmlConstants.ONE_HUNDRED_PERCENT_ATTRIBUTE_VALUE, null);
        writer.endElement(XhtmlConstants.TABLE_DATA_ELEMENT);
        if (hasNav)
        {
          writer.startElement(XhtmlConstants.TABLE_DATA_ELEMENT, null);
          if (rc.isRightToLeft())
            writer.writeAttribute(XhtmlConstants.ALIGN_ATTRIBUTE, XhtmlConstants.LEFT_ATTRIBUTE_VALUE, null);
          else
            writer.writeAttribute(XhtmlConstants.ALIGN_ATTRIBUTE, XhtmlConstants.RIGHT_ATTRIBUTE_VALUE, null);
          writer.writeAttribute(XhtmlConstants.VALIGN_ATTRIBUTE, XhtmlConstants.MIDDLE_ATTRIBUTE_VALUE, null);
          renderRangePagingControl(context, rc, tContext, component);
          writer.endElement(XhtmlConstants.TABLE_DATA_ELEMENT);
        }

        // end control bar table
        writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
        writer.endElement(XhtmlConstants.TABLE_ELEMENT);

        // end control bar row
        writer.endElement(XhtmlConstants.TABLE_DATA_ELEMENT);
        writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
      }
      finally
      {
        if (!isUpper)
        {
          assert oldRW != null;
          RepeatIdResponseWriter.remove(context, oldRW);
        }
      }
    }
  }

  /**
   * Render the next, previous links and the choicebar
   */
  protected void renderRangePagingControl(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                          UIComponent component)
    throws IOException
  {
    delegateRenderer(context, rc, component, getFacesBean(component), getSharedNavBarRenderer());
  }

  private boolean _assertCurrencyKeyPreserved(Object oldKey, UIComponent table)
  {
    UIXCollection base = (UIXCollection) table;
    Object newKey = base.getRowKey();
    return (oldKey != null)? oldKey.equals(newKey): (newKey == null);
  }

  // needed for BIBeans. Contact: Max Starets
  protected Object getTablePadding(UIComponent component)
  {
    return "1";
  }

  protected void renderTableRows(FacesContext context, RenderingContext rc, TableRenderingContext trc,
                                 UIComponent component, FacesBean bean)
    throws IOException
  {
    if (trc.getRowData().isEmptyTable())
      _renderEmptyTable(context, rc, trc);
    else
      _renderTableRows(context, rc, trc, component);
    // render the footer
    renderFooter(context, rc, trc, component);
  }

  /**
   * renders attributes on the outermost table element.
   * this includes width, cellpadding, cellspacing, border.
   */
  @Override
  protected void renderTableAttributes(FacesContext context, RenderingContext rc, UIComponent component, FacesBean bean,
                                       Object cellPadding, Object border)
    throws IOException
  {
    super.renderTableAttributes(context, rc, component, bean, cellPadding, border);
  }

  /**
   * @todo Implement cellClass correctly!
   * @todo Implement "headers" attribute correctly!
   */
  protected void renderCellFormatAttributes(FacesContext context, RenderingContext rc, TableRenderingContext tContext)
    throws IOException
  {
    // renders "style", "class", "nowrap", "headers".
    // renders "width" when there are no column headers.

    //TODO: must get individual column's style:
    String cellClass = SkinSelectors.AF_COLUMN_CELL_TEXT_STYLE; /*ColumnRenderer.getDataStyleClass(...)*/

    String borderStyleClass = CellUtils.getDataBorderStyle(rc, tContext);

    renderStyleClasses(context, rc, new String[]
    {
      cellClass, borderStyleClass
    });

    final ResponseWriter writer = context.getResponseWriter();
    int row = tContext.getRowData().getRangeIndex();
    int physicalColumn = tContext.getColumnData().getPhysicalColumnIndex();
    boolean noSelect = (!tContext.hasSelection());
    // Bug 1807935: if there's no column headers (and no
    // selection) then we haven't yet rendered the width
    // attribute.  Render it on the first row of cells.
    if ((row == 0) && noSelect && !tContext.hasColumnHeaders())
    {
      Object width = tContext.getColumnWidth(physicalColumn);
      writer.writeAttribute(XhtmlConstants.WIDTH_ATTRIBUTE, width, null);
    }

    // render "headers" attribute if necessary
    /*ColumnRenderer.renderHeadersAttr(context);*/

    // support "nowrap"
    if (tContext.getColumnData().getNoWrap(physicalColumn))
      writer.writeAttribute(XhtmlConstants.NOWRAP_ATTRIBUTE, Boolean.TRUE, null);
  }

  /**
   * @todo Reconsider our choice of style for this element!
   */
  private void _renderTableHeader(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                  UIComponent component)
    throws IOException
  {
    // implement header facet on table: see bug 3788610
    ResponseWriter writer = context.getResponseWriter();
    UIComponent header = getFacet(component, CoreTable.HEADER_FACET);
    if (header != null)
    {
      writer.startElement("thead", null);
      writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);
      writer.startElement(XhtmlConstants.TABLE_DATA_ELEMENT, null);
      writer.writeAttribute(XhtmlConstants.COLSPAN_ATTRIBUTE, tContext.getActualColumnCount(), null);
      renderStyleClass(context, rc, SkinSelectors.AF_COLUMN_SORTABLE_HEADER_ICON_STYLE_CLASS);

      encodeChild(context, header);

      writer.endElement(XhtmlConstants.TABLE_DATA_ELEMENT);
      writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
      writer.endElement("thead");
    }
  }

  private void _renderEmptyTable(FacesContext context, RenderingContext rc, TableRenderingContext tContext)
    throws IOException
  {
    int specialCols = tContext.hasSelection()? 1: 0;
    if (tContext.getDetail() != null)
      specialCols++;
    renderEmptyTableRow(context, rc, tContext, specialCols);
  }

  /**
   * Renders a row for an empty table. This includes the rowHeader and any
   * special columns, and all the regular columns.  The emptyText is
   * rendered in the first column following the special columns.
   * @param specialColumnCount The number of special columns in this table.
   */
  protected final void renderEmptyTableRow(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                           int specialColumnCount)
    throws IOException
  {
    renderEmptyTableRow(context, rc, tContext, specialColumnCount, null);
  }

  protected final void renderEmptyTableRow(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                           int specialColumnCount, CoreRenderer emptyTextRenderer)
    throws IOException
  {
    // renders <TR> followed by a whole bunch of <TD>..</TD>, followed by
    // </TR>
    ResponseWriter writer = context.getResponseWriter();
    writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);
    final ColumnData colData = tContext.getColumnData();
    int physicalColumn = 0;

    int objectNameColumnIndex = colData.getObjectNameColumnIndex();
    for (int i = 0, sz = Math.max(specialColumnCount, objectNameColumnIndex); i < sz; i++)
    {
      _renderEmptyCell(context, rc, tContext, physicalColumn++, null, 1);
    }

    int totalCols = tContext.getActualColumnCount();
    UIComponent table = tContext.getTable();
    FacesBean bean = getFacesBean(table);

    if (emptyTextRenderer == null)
    {
      _renderEmptyCell(context, rc, tContext, physicalColumn, getEmptyText(table, bean), totalCols - physicalColumn);
      physicalColumn++;
    }
    else
    {
      delegateRenderer(context, rc, table, bean, emptyTextRenderer);
      while (physicalColumn < totalCols)
      {
        _renderEmptyCell(context, rc, tContext, physicalColumn++, null, 1);
      }
    }
    // clear the current header id
    colData.setCurrentHeaderID(null);
    writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
  }

  private void _renderEmptyCell(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                int physicalColumn, String text, int colspan)
    throws IOException
  {
    ColumnData colData = tContext.getColumnData();
    ResponseWriter writer = context.getResponseWriter();
    // root columns only, so headerID is singleton
    // rather than space-separated list
    String colID = colData.getHeaderID(physicalColumn);
    colData.setCurrentHeaderID(colID);
    colData.setColumnIndex(physicalColumn, ColumnData.SPECIAL_COLUMN_INDEX);
    writer.startElement(XhtmlConstants.TABLE_DATA_ELEMENT, null);
    renderCellFormatAttributes(context, rc, tContext);
    if (colspan > 1)
      writer.writeAttribute(XhtmlConstants.COLSPAN_ATTRIBUTE, colspan, null);
    if (text != null)
      writer.writeText(text, null);
    writer.endElement(XhtmlConstants.TABLE_DATA_ELEMENT);
  }

  private void _renderTableRows(FacesContext context, final RenderingContext rc, final TableRenderingContext tContext,
                                UIComponent component)
    throws IOException
  {
    // renders a whole bunch of <TR>...</TR> elements, one for each row in the
    // table, and additional ones for any disclosed-details rows
    //
    // 1. Gather all the data we need to render
    //
    final RowData rowData = tContext.getRowData();
    final UIComponent detail = tContext.getDetail();
    final RenderStage renderStage = tContext.getRenderStage();
    TableUtils.RowLoop loop = new TableUtils.RowLoop()
    {
      @Override
      protected void processRowImpl(FacesContext fc, CollectionComponent tableBase)
        throws IOException
      {
        ResponseWriter writer = fc.getResponseWriter();
        // compute all the rowSpans for the current row:
        rowData.setCurrentRowSpan(-1);
        //reset
        renderStage.setStage(RenderStage.START_ROW_STAGE);
        renderSingleRow(fc, rc, tContext, (UIComponent) tableBase);
        renderStage.setStage(RenderStage.DATA_STAGE);
        // render each of the individual rows in the rowSpan:
        for (int i = 0, sz = rowData.getCurrentRowSpan(); i < sz; i++)
        {
          // start the row
          writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);
          renderSingleRow(fc, rc, tContext, (UIComponent) tableBase);
          rowData.incCurrentSubRow();
          // end the row
          writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
        }
        // if necessary, render a detail row
        if ((detail != null) && ((UIXTable) tableBase).getDisclosedRowKeys().isContained())
        {
          // indicate that we are now rendering inside of a details section
          renderStage.setStage(RenderStage.DETAIL_ROW_STAGE);
          ColumnData colData = tContext.getColumnData();
          // while rendering the named children in the detail row,
          // do not use the special response writer that
          // defaults data cells with no data to <br>.
          // This fixes bug 2367693.
          writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);
          writer.startElement(XhtmlConstants.TABLE_DATA_ELEMENT, null);
          writer.writeAttribute("headers", colData.getHeaderID(tContext.getDetailColumnIndex()), null);
          writer.writeAttribute(XhtmlConstants.COLSPAN_ATTRIBUTE,
                                IntegerUtils.getString(tContext.getActualColumnCount()), null);
          String styleClass = SkinSelectors.AF_TABLE_DETAIL_STYLE;
          String borderStyleClass = CellUtils.getBorderClass(true, true, true, true);
          renderStyleClasses(fc, rc, new String[]
          {
            styleClass, borderStyleClass
          });

          encodeChild(fc, detail);
          writer.endElement(XhtmlConstants.TABLE_DATA_ELEMENT);
          writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
          // restore the data stage
          renderStage.setStage(RenderStage.DATA_STAGE);
        }
        // end detail
      }
    };

    ResponseWriter writer = context.getResponseWriter();
    String height = getHeight(component, getFacesBean(component));
    boolean useScroll;

    if ((height != null) && isGecko(rc))
    {
      useScroll = true;
      writer.startElement("tbody", null);
      writer.writeAttribute("style", "overflow:auto;max-height:" + height, null);
    }
    else
      useScroll = false;
    loop.run(context, tContext.getCollectionComponent());
    if (useScroll)
    {
      writer.endElement("tbody");
    }
  }

  /**
   * render the complete column header, including the special columns (like
   * select,details,...) and the regular table columns
   */
  protected final void renderColumnHeader(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                          UIComponent component)
    throws IOException
  {
    // This renders a whole bunch of <TH>...</TH> elements
    final ColumnData colData = tContext.getColumnData();
    // we need to keep track of which row we are on; this makes it easier
    // to do rowSpanning in columnGroups:
    colData.setRowIndex(0);
    int physicalCol = renderSpecialColumns(context, rc, tContext, component, 0);
    renderRegularHeaders(context, rc, tContext, component, physicalCol);
    // we are done, so reset the current row:
    colData.setRowIndex(-1);
  }


  /**
   * renders the regular table column headers.
   */
  protected final void renderRegularHeaders(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                            UIComponent component, int physicalCol)
    throws IOException
  {
    // this renders a whole bunch of <TH>...</TH> elements.
    // if there are columnGroups present, it will render some
    // </TR><TR><TH>...</TH> sequences.
    final ColumnData colData = tContext.getColumnData();
    _renderRegularColumns(context, tContext, component, physicalCol);
    int rowSpan = colData.getHeaderRowSpan();
    if (rowSpan > 1)
    {
      ResponseWriter writer = context.getResponseWriter();
      for (int i = 1; i < rowSpan; i++)
      {
        colData.setRowIndex(i);
        writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
        writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);
        _renderRegularColumns(context, tContext, component, physicalCol);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void _renderRegularColumns(FacesContext context, TableRenderingContext tContext, UIComponent component,
                                     int physicalCol)
    throws IOException
  {
    // this renders a whole bunch of <TH>...</TH> elements.
    // part of #1313720, base column header count on
    // table child count
    List<UIComponent> children = component.getChildren();
    int colCount = children.size();
    int[] hidden = tContext.getHiddenColumns();
    ColumnData colData = tContext.getColumnData();
    for (int i = 0; i < colCount; i++)
    {
      if (hidden[i] != TableRenderingContext.NORMAL_COLUMN)
        continue;
      UIComponent child = children.get(i);
      if (!(child instanceof UIXColumn))
        continue;

      UIXColumn column = (UIXColumn) child;
      boolean isRowHeader = Boolean.TRUE.equals(column.getAttributes().get(CoreColumn.ROW_HEADER_KEY.getName()));
      if (!isRowHeader)
      {
        colData.setColumnIndex(physicalCol, i);
        encodeChild(context, column);
        // ColumnBeans automatically increment the physical and logical
        // column indices (these may be increase by more than one, if
        // there are columnGroups). So we must not increment the column
        // indices here
        physicalCol = colData.getPhysicalColumnIndex();
      }
    }
  }

  /**
   * @todo Re-fix bug 3211593 (see below)
   */
  @SuppressWarnings("unchecked")
  protected final void renderFooter(FacesContext context, RenderingContext rc, TableRenderingContext tContext,
                                    UIComponent component)
    throws IOException
  {
    tContext.getRenderStage().setStage(RenderStage.COLUMN_FOOTER_STAGE);
    final ColumnData colData = tContext.getColumnData();
    UIComponent footer = getFacet(component, CoreTable.FOOTER_FACET);
    boolean hasColumnFooters = colData.getPhysicalIndexOfFirstFooter() >= 0;

    // If there's a table footer, or column footers, we've got work to do
    if ((footer != null) || hasColumnFooters)
    {
      // also split content and footer in case of scrollable tables
      ResponseWriter writer = context.getResponseWriter();
      String height = getHeight(component, getFacesBean(component));
      final boolean useScroll = (height != null);
      if (useScroll)
      {
        String tableId = tContext.getTableId();
        writer.endElement(XhtmlConstants.TABLE_ELEMENT); // end content table
        writer.endElement(XhtmlConstants.DIV_ELEMENT);
        writer.startElement(XhtmlConstants.TABLE_ELEMENT, null); // start footer table
        renderStyleClass(context, rc, SkinSelectors.AF_TABLE_CONTENT_STYLE);
        writer.writeAttribute(XhtmlConstants.ID_ATTRIBUTE, tableId + "_foot", null);
        OutputUtils.renderLayoutTableAttributes(context, rc, "0", // cell spacing
                                                "0", // border
                                                "100%"); // table width
      }

      writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);
      // total rows may need an ID. see bug 3211593:
      /* Need new scheme for generateUniqueId()?
      String rowID = XhtmlLafUtils.generateUniqueID(tContext);
      writer.writeAttribute(XhtmlLafConstants.ID_ATTRIBUTE, rowID, null);
      tContext.getRowData().setCurrentRowHeaderID(rowID);
      */
      final int firstFooterPhysicalIndex = colData.getPhysicalIndexOfFirstFooter();
      // By default, we try to render the table footer in the same row
      // as the column footers;  this is to save on screen real-estate,
      // and do something with that space in the table if the first N
      // columns have no footer content.
      // When the first column does have a footer, we'll need to push
      // the table footer down to an extra row

      // If there isn't a column footer in the first row, render a TH
      // with a sufficient colspan - and put the table footer in there
      // if it exists.
      // (Note this does need to be != 0, not > 0.  Negative numbers
      // mean there's no column footers, in which case we'll handle
      // outputting the table footer right here)
      if (firstFooterPhysicalIndex != 0)
      {
        writer.startElement(XhtmlConstants.TABLE_HEADER_ELEMENT, null);
        final int colSpan = (firstFooterPhysicalIndex > 0)? firstFooterPhysicalIndex: tContext.getActualColumnCount();
        writer.writeAttribute(XhtmlConstants.COLSPAN_ATTRIBUTE, IntegerUtils.getString(colSpan), null);
        renderStyleClass(context, rc, SkinSelectors.AF_TABLE_COLUMN_FOOTER_STYLE);
        if (footer != null)
          encodeChild(context, footer);
        writer.endElement(XhtmlConstants.TABLE_HEADER_ELEMENT);
      }

      if (firstFooterPhysicalIndex >= 0)
      {
        colData.setColumnIndex(tContext.getSpecialColumnCount(), 0 /*logicalColumnIndex*/);

        for (UIComponent child: (List<UIComponent>) component.getChildren())
        {
          if (child.isRendered())
          {
            encodeChild(context, child);
          }
        }
      }
      writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);

      // OK, we need to put the table footer at the end in its own row,
      // because the first column is already taken
      if ((firstFooterPhysicalIndex == 0) && (footer != null))
      {
        writer.startElement(XhtmlConstants.TABLE_ROW_ELEMENT, null);

        writer.startElement(XhtmlConstants.TABLE_HEADER_ELEMENT, null);
        // Make it span the whole table
        writer.writeAttribute(XhtmlConstants.COLSPAN_ATTRIBUTE, tContext.getActualColumnCount(), null);

        renderStyleClass(context, rc, SkinSelectors.AF_TABLE_COLUMN_FOOTER_STYLE);
        encodeChild(context, footer);
        writer.endElement(XhtmlConstants.TABLE_HEADER_ELEMENT);
        writer.endElement(XhtmlConstants.TABLE_ROW_ELEMENT);
      }
    }
  }

  protected String getControlLinkIconName(String translationKey)
  {
    if (translationKey == null)
      return null;

    return translationKey.equals(_SELECT_ALL_TEXT_KEY)? SkinSelectors.AF_TABLE_SELECT_ALL_ICON_NAME:
           SkinSelectors.AF_TABLE_SELECT_NONE_ICON_NAME;
  }

  protected String getSummary(UIComponent component, FacesBean bean)
  {
    return toString(bean.getProperty(_summaryKey));
  }

  protected String getHeight(UIComponent component, FacesBean bean)
  {
    return toString(bean.getProperty(_heightKey));
  }

  /**
   * Tells whether or not the autoSubmit attribute is set on the bean
   *
   * @param bean the bean
   */
  protected boolean isAutoSubmit(UIComponent component, FacesBean bean)
  {
    if (_autoSubmitKey == null)
      return false;

    return Boolean.TRUE.equals(bean.getProperty(_autoSubmitKey));
  }

  protected boolean getAllDetailsEnabled(UIComponent component, FacesBean bean)
  {
    Object o = bean.getProperty(_allDetailsEnabledKey);
    if (o == null)
      o = _allDetailsEnabledKey.getDefault();

    return Boolean.TRUE.equals(o);
  }

  static private class AllDetail
    extends ShowDetailRenderer
  {
    public AllDetail(FacesBean.Type type, boolean disclosed)
    {
      super(type);
      _disclosed = disclosed;
    }

    @Override
    protected void renderAllAttributes(FacesContext context, RenderingContext rc, UIComponent component, FacesBean bean)
    {
    }

    @Override
    protected boolean isTableAllDisclosure()
    {
      return true;
    }

    @Override
    protected boolean renderAsInline()
    {
      return true;
    }

    @Override
    protected String getValueParameter(UIComponent component)
    {
      return "all";
    }

    @Override
    protected boolean getDisclosed(UIComponent component, FacesBean bean)
    {
      return _disclosed;
    }

    @Override
    protected String getDisclosedText(UIComponent component, FacesBean bean)
    {
      RenderingContext arc = RenderingContext.getCurrentInstance();
      return arc.getTranslatedString(_HIDE_ALL_DETAILS_TEXT_KEY);
    }

    @Override
    protected String getUndisclosedText(UIComponent component, FacesBean bean)
    {
      RenderingContext arc = RenderingContext.getCurrentInstance();
      return arc.getTranslatedString(_SHOW_ALL_DETAILS_TEXT_KEY);
    }

    @Override
    protected String getLinkId(String rootId, boolean disclosed)
    {
      String suffix = (disclosed? "ha": "sa");
      return XhtmlUtils.getCompositeId(rootId, suffix);
    }

    @Override
    protected String getClientId(FacesContext context, UIComponent component)
    {
      TableRenderingContext tContext = TableRenderingContext.getCurrentInstance();
      return tContext.getTableId();
    }

    private boolean _disclosed;
  }

  //
  // Private variables
  //

  private CoreRenderer _allDisclosed;
  private CoreRenderer _allUndisclosed;
  // translation keys

  private static final String _SHOW_ALL_DETAILS_TEXT_KEY = "af_table.SHOW_ALL_DETAILS";
  private static final String _HIDE_ALL_DETAILS_TEXT_KEY = "af_table.HIDE_ALL_DETAILS";
  protected static final String _SELECT_ALL_TEXT_KEY = "af_tableSelectMany.SELECT_ALL";
  protected static final String _SELECT_NONE_TEXT_KEY = "af_tableSelectMany.SELECT_NONE";
  private static final Object _IE_SCROLL_KEY = new Object();
  public static final String LINKS_DIVIDER_TEXT = "\u00a0|\u00a0";

  private PropertyKey _autoSubmitKey;
  private PropertyKey _summaryKey;
  private PropertyKey _heightKey;
  private PropertyKey _allDetailsEnabledKey;
}

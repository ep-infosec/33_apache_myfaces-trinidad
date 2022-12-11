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
package org.apache.myfaces.trinidaddemo.tagDemos;


import java.io.Serializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.faces.component.UIComponent;
import javax.faces.component.visit.VisitCallback;
import javax.faces.component.visit.VisitContext;
import javax.faces.component.visit.VisitResult;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;

import org.apache.myfaces.trinidad.change.ChangeManager;
import org.apache.myfaces.trinidad.change.ReorderChildrenComponentChange;
import org.apache.myfaces.trinidad.context.RequestContext;
import org.apache.myfaces.trinidad.model.RowKeyPropertyModel;
import org.apache.myfaces.trinidad.model.SortCriterion;
import org.apache.myfaces.trinidad.model.SortableModel;
import org.apache.myfaces.trinidad.util.ComponentUtils;


public class ForEachBean
  implements Serializable
{
  public static class Person
    implements Serializable
  {
    public Person(
      String key,
      String firstName,
      String lastName)
    {
      _firstName = firstName;
      _lastName = lastName;
      _key = key;
    }

    public final String getKey()
    {
      return _key;
    }

    public final String getFirstName()
    {
      return _firstName;
    }

    public final String getLastName()
    {
      return _lastName;
    }

    @Override
    public String toString()
    {
      return ("Person[(" + _key + ") " + _lastName + ", " + _firstName + "]");
    }

    private final String _key;
    private final String _firstName;
    private final String _lastName;

    @SuppressWarnings("compatibility:-1476456984616362784")
    private static final long serialVersionUID = 1L;
  }

  public static class UpdatableItem
    implements Serializable
  {
    public UpdatableItem(
      String key,
      String value)
    {
      _key = key;
      _value = value;
    }

    public final String getKey()
    {
      return _key;
    }

    public final void setValue(String value)
    {
      _value = value;
    }

    public final String getValue()
    {
      return _value;
    }

    private final String _key;
    private String _value;

    @SuppressWarnings("compatibility:-1687851812834776335")
    private static final long serialVersionUID = 1L;
  }

  public static class SubFamily
  {
    private SubFamily(
      String    name,
      Genus ... members)
    {
      _name = name;
      _members = members;
    }

    public String getName()
    {
      return _name;
    }

    public Genus[] getMembers()
    {
      return _members;
    }

    private final String    _name;
    private final Genus[] _members;
  }

  public static class Genus
  {
    private Genus(
      String      name,
      Species ... members)
    {
      _name = name;
      _members = members;
    }

    public String getName()
    {
      return _name;
    }

    public Species[] getMembers()
    {
      return _members;
    }

    private final String    _name;
    private final Species[] _members;
  }

  public static class Species
  {
    private Species(
      String name,
      String scientificName)
    {
      _name = name;
      _scientificName = scientificName;
    }

    public String getName()
    {
      return _name;
    }

    public String getScientificName()
    {
      return _scientificName;
    }

    private final String _name;
    private final String _scientificName;
  }

  public ForEachBean()
  {
    _list = new ArrayList<Person>(
      Arrays.asList(
        new Person("a", "John", "Doe"),
        new Person("b", "Jane", "Doe"),
        new Person("c", "Bob", "Smith"),
        new Person("d", "Alice", "Jones")));

    _model = new RowKeyPropertyModel(new ArrayList<Person>(_list), "key");
    _simpleList = new ArrayList<Person>(_list);
    _map = new LinkedHashMap<String, Person>();
    _applySortToNonCollectionModelObjects();
    _arrangeMap = new LinkedHashMap<String, Person>(_map);

    _updatableItemMap = new LinkedHashMap<String, UpdatableItem>();
    for (int i = 1; i <= 5; ++i)
    {
      String key = Integer.toString(i);
      String value = String.format("Item %d", i);
      _updatableItemMap.put(key, new UpdatableItem(key, value));
    }
  }

  public void setNewPersonFirstName(String newPersonFirstName)
  {
    _newPersonFirstName = newPersonFirstName;
  }

  public String getNewPersonFirstName()
  {
    return _newPersonFirstName;
  }

  public void setNewPersonLastName(String newPersonLastName)
  {
    _newPersonLastName = newPersonLastName;
  }

  public String getNewPersonLastName()
  {
    return _newPersonLastName;
  }

  public final void setCurrentExample(String currentExample)
  {
    _currentExample = currentExample;
  }

  public final String getCurrentExample()
  {
    return _currentExample;
  }

  public final void setSortProperty(String sortProperty)
  {
    if ("".equals(sortProperty))
    {
      sortProperty = null;
    }
    _sortProperty = sortProperty;
  }

  public final String getSortProperty()
  {
    return _sortProperty;
  }

  public final void setSortAscending(boolean sortAscending)
  {
    _sortAscending = sortAscending;
  }

  public final boolean isSortAscending()
  {
    return _sortAscending;
  }

  public List<Person> getList()
  {
    return _list;
  }

  public List<Person> getSimpleList()
  {
    return _simpleList;
  }

  public void updateSortOrder(
    @SuppressWarnings("unused")
    ActionEvent evt)
  {
    RowKeyPropertyModel model = _getCollectionModel();

    if (_sortProperty == null)
    {
      model.setSortCriteria(null);
    }
    else
    {
      model.setSortCriteria(Collections.singletonList(new SortCriterion(_sortProperty,
              _sortAscending)));
    }

    // Now fire a component event to re-order the components
    final FacesContext facesContext = FacesContext.getCurrentInstance();
    final List<String> orderedKeys = _applySortToNonCollectionModelObjects();

    // Using visit tree is the recommended way to put the components in context
    VisitContext visitContext = VisitContext.createVisitContext(facesContext,
      Arrays.asList("r:personForEachMapParent", "r:personForEachModelParent"),
      null);
    facesContext.getViewRoot().visitTree(visitContext,
      new VisitCallback()
      {
        @Override
        public VisitResult visit(
          VisitContext visitContext,
          UIComponent  target)
        {
          String prefix = "personForEachMapParent".equals(target.getId()) ? "m_" : "cm_";
          _addReorderChange(target, orderedKeys, prefix);
          return VisitResult.ACCEPT;
        }
    });
  }

  public Map<String, ForEachBean.UpdatableItem> getUpdatableItemMap()
  {
    return _updatableItemMap;
  }

  public Map<String, Person> getMap()
  {
    return _map;
  }

  public Map<String, Person> getArrangeMap()
  {
    return _arrangeMap;
  }

  public SortableModel getModel()
  {
    return _getCollectionModel();
  }

  public final List<SubFamily> getFelineSubFamilies()
  {
    if (_felineFamily == null)
    {
      _felineFamily = Arrays.asList(
         new SubFamily("Pantherinae",
           new Genus("Panthera",
             new Species("Lion", "Panthera leo"),
             new Species("Jaguar", "Panthera onca"),
             new Species("Leopard", "Panthera pardus"),
             new Species("Tiger", "Panthera tigris")),
           new Genus("Uncia",
             new Species("Snow Leopard", "Uncia uncia")),
           new Genus("Neofelis",
             new Species("Clouded Leopard", "Neofelis nebulosa"),
             new Species("Sunda Clouded Leopard", "Neofelis diardi"))),
         new SubFamily("Felinae",
           new Genus("Pardofelis",
             new Species("Marbled Cat", "Pardofelis marmorata")),
           new Genus("Catopuma",
             new Species("Bay Cat", "Catopuma badia"),
             new Species("Asian Golden Cat", "Catopuma temminckii")),
           new Genus("Leptailurus",
             new Species("Serval", "Leptailurus serval")),
           new Genus("Caracal",
             new Species("Caracal", "Caracal caracal")),
           new Genus("Profelis",
             new Species("African Golden Cat", "Profelis aurata")),
           new Genus("Leopardus",
             new Species("Pantanal Cat", "Leopardus braccatus"),
             new Species("Colocolo", "Leopardus colocolo"),
             new Species("Geoffroy's Cat", "Leopardus geoffroyi"),
             new Species("Kodkod", "Leopardus guigna"),
             new Species("Andean Mountain Cat", "Leopardus jacobitus"),
             new Species("Pampas Cat", "Leopardus pajeros"),
             new Species("Ocelot", "Leopardus pardalis"),
             new Species("Oncilla", "Leopardus tigrinus"),
             new Species("Margay", "Leopardus wiedii")),
           new Genus("Lynx",
             new Species("Canadian Lynx", "Lynx canadensis"),
             new Species("Eurasian Lynx", "Lynx lynx"),
             new Species("Iberian Lynx", "Lynx pardinus"),
             new Species("Bobcat", "Lynx rufus")),
           new Genus("Puma",
             new Species("Cougar", "Puma concolor"),
             new Species("Jaguarundi", "Puma yagouaroundi")),
           new Genus("Acinonyx",
             new Species("Cheetah", "Acinonyx jubatus")),
           new Genus("Prionailurus",
             new Species("Leopard Cat", "Prionailurus bengalensis"),
             new Species("Iriomote Cat", "Prionailurus bengalensis iriomotensis"),
             new Species("Flat-headed Cat", "Prionailurus planiceps"),
             new Species("Rusty-spotted Cat", "Prionailurus rubiginosus"),
             new Species("Fishing Cat", "Prionailurus viverrinus")),
           new Genus("Otocolobus",
             new Species("Pallas's Cat", "Otocolobus manul")),
           new Genus("Felis",
             new Species("Chinese Mountain Cat", "Felis bieti"),
             new Species("Domestic Cat", "Felis catus"),
             new Species("Jungle Cat", "Felis chaus"),
             new Species("Sand Cat", "Felis margarita"),
             new Species("Black-footed Cat", "Felis nigripes"),
             new Species("Wildcat", "Felis silvestris"))));
    }

    return _felineFamily;
  }

  public void handleArrangeNewItem(ActionEvent evt)
  {
    UIComponent target = evt.getComponent();
    String forEachKey = (String)target.getAttributes().get("forEachKey");
    String newKey = "new" + (_nextArrangePersonKey++);

    if (forEachKey == null)
    {
      // Append use case, just add the person to the map, no need to re-order
      _arrangeMap.put(newKey,
        new Person(newKey, _newPersonFirstName, _newPersonLastName));
    }
    else
    {
      LinkedHashMap<String, Person> mapCopy = new LinkedHashMap<String, Person>(_arrangeMap);
      _arrangeMap.clear();

      boolean added = false;

      for (Map.Entry<String, Person> entry : mapCopy.entrySet())
      {
        String key = entry.getKey();
        if (added == false && forEachKey.equals(key))
        {
          _arrangeMap.put(newKey,
            new Person(newKey, _newPersonFirstName, _newPersonLastName));
          added = true;
        }

        _arrangeMap.put(key, entry.getValue());
      }

      _sortArrangedDemoChildren(target);
    }

    _newPersonFirstName = null;
    _newPersonLastName = null;
  }

  public void handleArrangeRemoveItem(ActionEvent evt)
  {
    UIComponent target = evt.getComponent();
    String forEachKey = (String)target.getAttributes().get("forEachKey");

    _arrangeMap.remove(forEachKey);

    // No need to re-order the components as the mark-and-sweep will remove the unmatched component
    // during tag execution

    RequestContext.getCurrentInstance().addPartialTargets(target, "::forEachParent");
  }

  public void handleArrangeMoveItemUp(ActionEvent evt)
  {
    UIComponent target = evt.getComponent();
    String forEachKey = (String)target.getAttributes().get("forEachKey");

    _moveItem(forEachKey, true);

    _sortArrangedDemoChildren(target);
  }

  public void handleArrangeMoveItemDown(ActionEvent evt)
  {
    UIComponent target = evt.getComponent();
    String forEachKey = (String)target.getAttributes().get("forEachKey");

    _moveItem(forEachKey, false);

    _sortArrangedDemoChildren(target);
  }

  private void _moveItem(
    String  keyToMove,
    boolean moveUp)
  {
    int index = 0;
    for (String key : _arrangeMap.keySet())
    {
      if (keyToMove.equals(key))
      {
        break;
      }

      ++index;
    }

    index += moveUp ? -1 : 1;

    Person personToMove = _arrangeMap.remove(keyToMove);
    if (index == _arrangeMap.size())
    {
      _arrangeMap.put(keyToMove, personToMove);
    }
    else
    {
      LinkedHashMap<String, Person> mapCopy = new LinkedHashMap<String, Person>(_arrangeMap);
      _arrangeMap.clear();
      int i = 0;
      for (Map.Entry<String, Person> entry : mapCopy.entrySet())
      {
        if (i++ == index)
        {
          _arrangeMap.put(keyToMove, personToMove);
        }

        _arrangeMap.put(entry.getKey(), entry.getValue());
      }
    }
  }

  private void _sortArrangedDemoChildren(
    UIComponent target)
  {
    UIComponent forEachParent = ComponentUtils.findRelativeComponent(target,
      "::forEachParent");

    _addReorderChange(forEachParent, new ArrayList<String>(_arrangeMap.keySet()), "sv_");
  }

  private void _addReorderChange(
    UIComponent  forEachParentComponent,
    List<String> desiredKeyOrder,
    String       subviewPrefix)
  {
    List<String> orderedIds = new ArrayList<String>(desiredKeyOrder.size());
    // Note, since there are use cases in the demo that add items to the collections, the
    // components may not have been created yet, we cannot use the components
    // to build the ReorderChildrenComponentChange. So instead, we use prefixes for the subviews.
    // Therefore, the backing bean code must be tied to the IDs built by the page.
    for (String key : desiredKeyOrder)
    {
      orderedIds.add(subviewPrefix + key);
    }

    ReorderChildrenComponentChange componentChange = new ReorderChildrenComponentChange(orderedIds);
    RequestContext requestContext = RequestContext.getCurrentInstance();
    ChangeManager cm = requestContext.getChangeManager();
    cm.addComponentChange(FacesContext.getCurrentInstance(), forEachParentComponent,
      componentChange);
    requestContext.addPartialTarget(forEachParentComponent);
  }

  /**
   * Applies the sort to the person list and the person map based on the sort order of the
   * collection model.
   * @return the sorted keys of the people to use for component reordering
   */
  private List<String> _applySortToNonCollectionModelObjects()
  {
    RowKeyPropertyModel model = _getCollectionModel();

    Object origRowKey = model.getRowKey();
    List<String> orderedKeys = new ArrayList<String>(model.getRowCount());
    _list.clear();
    _map.clear();
    try
    {
      for (model.setRowIndex(0);
        model.isRowAvailable();
        model.setRowIndex(_model.getRowIndex() + 1))
      {
        Person person = (Person)model.getRowData();
        orderedKeys.add(person.getKey());
        _list.add(person);
        _map.put(person.getKey(), person);
      }
    }
    finally
    {
      try
      {
        model.setRowKey(origRowKey);
      }
      catch (Throwable t)
      {
        ;
      }
    }

    return orderedKeys;
  }

  private RowKeyPropertyModel _getCollectionModel()
  {
    if (_model == null)
    {
      // Model will be null as it is not serializable and the view state is not preserved in
      // the server memory
      _model = new RowKeyPropertyModel(new ArrayList<Person>(_list), "key");

      if (_sortProperty == null)
      {
        _model.setSortCriteria(null);
      }
      else
      {
        _model.setSortCriteria(Collections.singletonList(new SortCriterion(_sortProperty,
                _sortAscending)));
      }
    }

    return _model;
  }

  private final List<Person> _simpleList;
  private final List<Person> _list;
  private transient RowKeyPropertyModel _model;
  private final Map<String, Person> _map;
  private final Map<String, UpdatableItem> _updatableItemMap;
  private final Map<String, Person> _arrangeMap;
  private String _currentExample = null;
  private String _sortProperty;
  private String _newPersonFirstName;
  private String _newPersonLastName;
  private boolean _sortAscending;
  private int _nextArrangePersonKey = 1;
  private transient List<SubFamily> _felineFamily;

  @SuppressWarnings("compatibility:-5886088173109562862")
  private static final long serialVersionUID = 3L;
}

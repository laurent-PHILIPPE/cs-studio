/*******************************************************************************
 * Copyright (c) 2010 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.diag.epics.pvtree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Tree;
import org.diirt.vtype.AlarmSeverity;

/** The PV Tree Model
 *  <p>
 *  Unfortunately, this is not a generic model of the PV Tree data.
 *  It is tightly coupled to the TreeViewer, acting as the content provider,
 *  and directly updating/refreshing the tree GUI.
 *  <p>
 *  Note that most of the logic is actually inside the PVTreeItem.
 *  @see PVTreeItem
 *  @author Kay Kasemir
 */
class PVTreeModel implements IStructuredContentProvider, ITreeContentProvider
{
    /** The view to which we are connected. */
    final private TreeViewer viewer;

    /** Root PV of the tree */
    private PVTreeItem root;

    /** Map from record type to fields to read for that type */
    final private Map<String, List<String>> field_info;

    private volatile boolean freeze_on_alarm = false;

    /** 'frozen' = value updates should be ignored */
    private volatile boolean frozen = false;

    /** @param view
     *  @throws Exception on error in preferences
     */
    PVTreeModel(final TreeViewer viewer) throws Exception
    {
        this.viewer = viewer;
        field_info = Preferences.getFieldInfo();
        root = null;
    }

    /** @return Field info for all record types
     *  @see FieldParser
     */
    Map<String, List<String>> getFieldInfo()
    {
        return field_info;
    }

    /** @return Is model configured to freeze on alarm? */
    public boolean isFreezingOnAlarm()
    {
        return freeze_on_alarm;
    }

    /** @param yes_no Should updates freeze on alarm? */
    public void freezeOnAlarm(final boolean yes_no)
    {
        freeze_on_alarm = yes_no;
        final boolean was_frozen = frozen;
        frozen = false;
        if (was_frozen)
        {
            final Tree tree = viewer.getTree();
            tree.setBackground(tree.getDisplay().getSystemColor(SWT.COLOR_WHITE));
            updateValues(root);
        }
    }

    /** @param item Item where to start updating the value,
     *              recursively, in case the item has
     *              updates that were 'frozen' and now
     *              need to reflect the current value
     */
    private void updateValues(final PVTreeItem item)
    {
        item.updateValue();
        for (PVTreeItem child : item.getLinks())
            updateValues(child);
    }

    /** @return <code>true</code> if value updates should be ignored */
    public boolean isFrozen()
    {
        return frozen;
    }

    /** Re-initialize the model with a new root PV. */
    public void setRootPV(final String name)
    {
        final boolean was_frozen = frozen;
        frozen = false;
        if (was_frozen)
        {
            final Tree tree = viewer.getTree();
            tree.setBackground(tree.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        }
        if (root != null)
        {
            root.dispose();
            root = null;
        }
        root = new PVTreeItem(this, null, Messages.PV, name);
        itemChanged(root);
    }

    /** @return Returns a model item with given PV name or <code>null</code>. */
    public PVTreeItem findPV(final String pv_name)
    {
        return findPV(pv_name, root);
    }

    /** Searches for item from given item on down. */
    private PVTreeItem findPV(final String pv_name, final PVTreeItem item)
    {
        // Dead end?
        if (item == null)
            return null;
        // Is it this one?
        if (item.getPVName().equals(pv_name))
            return item;
        // Check each child recursively
        for (PVTreeItem child : item.getLinks())
        {
            final PVTreeItem found = findPV(pv_name, child);
            if (found != null)
                return found;
        }
        return null;
    }

    /** @return Leaf items that are in alarm */
    public List<PVTreeItem> getAlarmPVs()
    {
        final List<PVTreeItem> alarms = new ArrayList<>();
        addAlarmPVs(alarms, root);
        return alarms;
    }

    /** Recursively add leaf items that are in alarm
     *  @param alarms List to extend
     *  @param item Where to start looking for alarm leafs
     */
    private void addAlarmPVs(final List<PVTreeItem> alarms, final PVTreeItem item)
    {
        if (item.getSeverity() != AlarmSeverity.NONE)
            alarms.add(item);
        for (PVTreeItem sub : item.getLinks())
            addAlarmPVs(alarms, sub);
    }

    // IStructuredContentProvider
    @Override
    public void inputChanged(Viewer v, Object oldInput, Object newInput)
    {
        // NOP
    }

    @Override
    public void dispose()
    {
        if (root != null)
        {
            Plugin.getLogger().fine("PVTreeModel disposed"); //$NON-NLS-1$
            root.dispose();
            root = null;
        }
    }

    // IStructuredContentProvider
    @Override
    public Object[] getElements(final Object parent)
    {
        if (parent instanceof PVTreeItem)
            return getChildren(parent);
        if (root != null)
            return new Object[] { root };
        return new Object[0];
    }

    // ITreeContentProvider
    @Override
    public Object getParent(final Object child)
    {
        if (child instanceof PVTreeItem)
            return ((PVTreeItem) child).getParent();
        return null;
    }

    // ITreeContentProvider
    @Override
    public Object[] getChildren(final Object parent)
    {
        if (parent instanceof PVTreeItem)
            return ((PVTreeItem) parent).getLinks();
        return new Object[0];
    }

    // ITreeContentProvider
    @Override
    public boolean hasChildren(final Object parent)
    {
        if (parent instanceof PVTreeItem)
            return ((PVTreeItem) parent).hasLinks();
        return false;
    }

    /** Used by item to fresh the tree from the item on down. */
    public void itemUpdated(final PVTreeItem item)
    {
        final boolean was_frozen = frozen;
        if (freeze_on_alarm  &&
            item == root  &&
            item.getSeverity() != AlarmSeverity.NONE)
            frozen = true;

        final Tree tree = viewer.getTree();
        if (tree.isDisposed())
            return;

        tree.getDisplay().asyncExec(() ->
        {
               if (tree.isDisposed())
                   return;

            viewer.update(item, null);
            if (frozen && !was_frozen)
                tree.setBackground(tree.getDisplay().getSystemColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND));
        });
    }

    /** Used by item to refresh the tree from the item on down. */
    public void itemChanged(final PVTreeItem item)
    {
        final Tree tree = viewer.getTree();
        if (tree.isDisposed())
            return;
        tree.getDisplay().asyncExec(() ->
        {
            if (tree.isDisposed())
                return;
            if (item == root)
                viewer.refresh();
            else
                viewer.refresh(item);
            viewer.expandAll();
        });
    }
}

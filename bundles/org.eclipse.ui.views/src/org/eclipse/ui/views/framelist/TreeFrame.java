/************************************************************************
Copyright (c) 2000, 2003 IBM Corporation and others.
All rights reserved.   This program and the accompanying materials
are made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html

Contributors:
    IBM - Initial implementation
************************************************************************/
package org.eclipse.ui.views.framelist;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.*;
import org.eclipse.ui.*;
import org.eclipse.ui.internal.IWorkbenchConstants;
import org.eclipse.ui.internal.WorkbenchPlugin;

/**
 * Frame for tree viewers.  This capture the viewer's input element, selection,
 * and expanded elements.
 */
public class TreeFrame extends Frame {
	private static final String TAG_SELECTION = "selection"; //$NON-NLS-1$
	private static final String TAG_EXPANDED = "expanded"; //$NON-NLS-1$
	private static final String TAG_ELEMENT = "element"; //$NON-NLS-1$
	private static final String TAG_FRAME_INPUT = "frameInput"; //$NON-NLS-1$
	
	private AbstractTreeViewer viewer;
	private Object input;
	private ISelection selection;
	private Object[] expandedElements;
	
	/**
	 * Constructs a frame for the specified tree viewer.
	 * The frame's input, name and tool tip text are not set.
	 * 
	 * @param viewer the tree viewer
	 */
	public TreeFrame(AbstractTreeViewer viewer) {
		this.viewer = viewer;
	}

	/**
	 * Constructs a frame for the specified tree viewer.
	 * The frame's input element is set to the specified input element.
	 * The frame's name and tool tip text are set to the text for the input 
	 * element, as provided by the viewer's label provider.
	 * 
	 * @param viewer the tree viewer
	 * @param input the input element
	 */
	public TreeFrame(AbstractTreeViewer viewer, Object input) {
		this(viewer);
		setInput(input);
		ILabelProvider provider = (ILabelProvider) viewer.getLabelProvider();
		String name = provider.getText(input);
		setName(name);
		setToolTipText(name);
	}
	
	/**
	 * Returns the expanded elements.
	 * 
	 * @return the expanded elements
	 */
	public Object[] getExpandedElements() {
		return expandedElements;
	}
	
	/**
	 * Returns the input element.
	 * 
	 * @return the input element
	 */
	public Object getInput() {
		return input;
	}
	
	/**
	 * Returns the selection.
	 * 
	 * @return the selection
	 */
	public ISelection getSelection() {
		return selection;
	}
	
	/**
	 * Returns the tree viewer.
	 * 
	 * @return the tree viewer
	 */
	public AbstractTreeViewer getViewer() {
		return viewer;
	}
	
	/**
	 * Restore IPersistableElements from the specified memento.
	 * 
	 * @param memento memento to restore elements from
	 * @return list of restored elements. May be empty.
	 */
	private List restoreElements(IMemento memento) {
		IMemento[] elementMem = memento.getChildren(TAG_ELEMENT);
		List elements = new ArrayList(elementMem.length);
		
		for (int i = 0; i < elementMem.length; i++) {
			String factoryID = elementMem[i].getString(IWorkbenchConstants.TAG_FACTORY_ID);
			if (factoryID != null) {
				IElementFactory factory = WorkbenchPlugin.getDefault().getElementFactory(factoryID);
				if (factory != null)
					elements.add(factory.createElement(elementMem[i]));
			}
		}
		return elements;
	}
	
	/**
	 * Restore the frame from the specified memento.
	 * 
	 * @param memento memento to restore frame from
	 */
	public void restoreState(IMemento memento) {
		IMemento childMem = memento.getChild(TAG_FRAME_INPUT);
		
		if (childMem == null)
			return;
		
		String factoryID = childMem.getString(IWorkbenchConstants.TAG_FACTORY_ID);
		IAdaptable frameInput = null;
		if (factoryID != null) {
			IElementFactory factory = WorkbenchPlugin.getDefault().getElementFactory(factoryID);
			if (factory != null)
				frameInput = factory.createElement(childMem);
		}
		if (frameInput != null) {
			input = frameInput;
		}
		IMemento expandedMem = memento.getChild(TAG_EXPANDED);
		if (expandedMem != null) {
			List elements = restoreElements(expandedMem);
			expandedElements = (Object[]) elements.toArray(new Object[elements.size()]);
		}
		else {
			expandedElements = new Object[0];
		}
		IMemento selectionMem = memento.getChild(TAG_SELECTION);
		if (selectionMem != null) {
			List elements = restoreElements(selectionMem);
			selection = new StructuredSelection(elements);
		}
		else {
			selection = StructuredSelection.EMPTY;
		}		
	}
	
	/**
	 * Save the specified elements to the given memento.
	 * The elements have to be adaptable to IPersistableElement.
	 * 
	 * @param elements elements to persist
	 * @param memento memento to persist elements in
	 */
	private void saveElements(Object[] elements, IMemento memento) {
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] instanceof IAdaptable) {
				IPersistableElement persistable = (IPersistableElement) ((IAdaptable) elements[i]).getAdapter(IPersistableElement.class);
				if (persistable != null) {
					IMemento elementMem = memento.createChild(TAG_ELEMENT);
					elementMem.putString(IWorkbenchConstants.TAG_FACTORY_ID, persistable.getFactoryId());
					persistable.saveState(elementMem);								
				}							
			}
		}
	}
	
	/**
	 * Save the frame state in the given memento.
	 * 
	 * @param memento memento to persist the frame state in.
	 */
	public void saveState(IMemento memento) {
		if (!(input instanceof IAdaptable))
			return;
			
		IPersistableElement persistable = (IPersistableElement) ((IAdaptable) input).getAdapter(IPersistableElement.class);
		if (persistable != null) {
			IMemento frameMemento = memento.createChild(TAG_FRAME_INPUT);
			
			frameMemento.putString(IWorkbenchConstants.TAG_FACTORY_ID, persistable.getFactoryId());
			persistable.saveState(frameMemento);
			
			if (expandedElements.length > 0) {
				IMemento expandedMem = memento.createChild(TAG_EXPANDED);
				saveElements(expandedElements, expandedMem);
			}
			// always IStructuredSelection since we only deal with tree viewers
			if (selection instanceof IStructuredSelection) {
				Object[] elements = ((IStructuredSelection) selection).toArray();
				if (elements.length > 0) {
					IMemento selectionMem = memento.createChild(TAG_SELECTION);
					saveElements(elements, selectionMem);
				}
			}
		}	
	}
	
	/**
	 * Sets the input element.
	 * 
	 * @param input the input element
	 */
	public void setInput(Object input) {
		this.input = input;
	}
	
	/**
	 * Sets the expanded elements.
	 * 
	 * @param expandedElements the expanded elements
	 */
	public void setExpandedElements(Object[] expandedElements) {
		this.expandedElements = expandedElements;
	}
	
	/**
	 * Sets the selection.
	 * 
	 * @param selection the selection
	 */
	public void setSelection(ISelection selection) {
		this.selection = selection;
	}
}
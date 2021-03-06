/*******************************************************************************
 * Copyright (c) 2015, 2015 Andrew Gvozdev and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Gvozdev - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.includebrowser;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * Action to remove items from the C++ Include Browser View.
 */
public class IBRemoveFromView extends Action {
	private IBViewPart fView;

	/**
	 * Constructs a Remove From View action.
	 * 
	 * @param view the Include Browser view
	 */
	public IBRemoveFromView(IBViewPart view) {
		super(IBMessages.IBViewPart_RemoveFromView_label);
		fView= view;
		ISharedImages images = PlatformUI.getWorkbench().getSharedImages();
		setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_ELCL_REMOVE));
		setDisabledImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE_DISABLED));
	}

	@Override
	public void run() {
		TreeViewer tree = fView.getTreeViewer();
		ITreeSelection selection = (ITreeSelection) tree.getSelection();
		tree.setSelection(null); // should stay before removal
		tree.remove(selection.toArray());
	}
}

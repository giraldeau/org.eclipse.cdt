package org.eclipse.cdt.ui;
/***********************************************************************
 * Copyright (c) 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * QNX Software Systems - Initial API and implementation
***********************************************************************/

import java.util.ArrayList;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.internal.ui.CPluginImages;
import org.eclipse.cdt.utils.ui.controls.ControlFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

public class ReferenceBlock extends AbstractCOptionPage {

	private static final String PREFIX = "ReferenceBlock"; // $NON-NLS-1$
	private static final String LABEL = PREFIX + ".label"; // $NON-NLS-1$
	private static final String DESC = PREFIX + ".desc"; // $NON-NLS-1$

	private CheckboxTableViewer referenceProjectsViewer;
	
	public ReferenceBlock() {
		super(CUIPlugin.getResourceString(LABEL));
		setDescription(CUIPlugin.getResourceString(DESC));
	}

	public Image getImage() {
		return CPluginImages.get(CPluginImages.IMG_OBJS_PROJECT);
	}

	/**
	 * Returns a content provider for the reference project
	 * viewer. It will return all projects in the workspace.
	 *
	 * @return the content provider
	 */
	protected IStructuredContentProvider getContentProvider() {
		return new WorkbenchContentProvider() {
			public Object[] getChildren(Object element) {
				if (!(element instanceof IWorkspace))
					return new Object[0];
				ArrayList aList = new ArrayList(15);
				final IProject[] projects = ((IWorkspace)element).getRoot().getProjects();
				for (int i = 0; i < projects.length; i++) {
					if (CoreModel.getDefault().hasCNature(projects[i])) {
						// Do not show the actual project being look at
						if ((getContainer().getProject() != null) && getContainer().getProject().equals(projects[i])) {
							continue;
						}
						aList.add(projects[i]);
					}
				}
				return aList.toArray();
			}
		};
	}

	protected void initializeValues () {
		if (getContainer().getProject() != null) {
			try {
				IProject[] referenced = getContainer().getProject().getReferencedProjects();
				referenceProjectsViewer.setCheckedElements(referenced);
			} catch (CoreException e) {
			}
		}
	}

	/**
	 * Returns the referenced projects selected by the user.
	 *
	 * @return the referenced projects
	 */
	public IProject[] getReferencedProjects() {
		Object[] elements = referenceProjectsViewer.getCheckedElements();
		IProject[] projects = new IProject[elements.length];
		System.arraycopy(elements, 0, projects, 0, elements.length);
		return projects;	
	}

	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label label = new Label(composite, SWT.LEFT);
		label.setText(CUIPlugin.getResourceString(DESC));
		GridData lbldata = new GridData(GridData.FILL_HORIZONTAL);
		lbldata.horizontalSpan = 1;
		label.setLayoutData(lbldata);

		referenceProjectsViewer = ControlFactory.createListViewer
			(composite, null, SWT.DEFAULT, SWT.DEFAULT, GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);

		referenceProjectsViewer.setLabelProvider(new WorkbenchLabelProvider());
		referenceProjectsViewer.setContentProvider(getContentProvider());
		referenceProjectsViewer.setInput(ResourcesPlugin.getWorkspace());

		initializeValues();
		setControl(composite);
	}

	public void performApply(IProgressMonitor monitor) throws CoreException {
		IProject[] refProjects = getReferencedProjects();
		if (refProjects != null) {
			IProject project = getContainer().getProject();
			if (monitor == null) {
				monitor = new NullProgressMonitor();
			}
			monitor.beginTask("Reference Projects", 1);
			try {
				IProjectDescription description = project.getDescription();
				description.setReferencedProjects(refProjects);
				project.setDescription(description, new SubProgressMonitor(monitor, 1));
			} catch (CoreException e) {
			}
		}
		
	}

	public void performDefaults() {
		initializeValues();
	}
}

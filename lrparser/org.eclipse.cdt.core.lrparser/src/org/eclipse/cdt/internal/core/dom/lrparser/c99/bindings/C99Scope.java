/*******************************************************************************
 * Copyright (c) 2006, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.core.dom.lrparser.c99.bindings;

import org.eclipse.cdt.core.dom.IName;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.lrparser.action.c99.C99SymbolTable;
import org.eclipse.cdt.internal.core.dom.parser.IASTInternalScope;

/**
 * @author Mike Kucera
 *
 */
public class C99Scope implements IC99Scope, IASTInternalScope {

	
	
	private IScope parent;
	private IASTNode physicalNode;
	private IName scopeName;
	
	
	
	
	
	public IScope getParent() {
		return parent;
	}

	public void setParent(IScope parent) {
		this.parent = parent;
	}
	
	public IASTNode getPhysicalNode() {
		return physicalNode;
	}

	public void setPhysicalNode(IASTNode physicalNode) {
		this.physicalNode = physicalNode;
	}

	public IName getScopeName() {
		return scopeName;
	}

	public void setScopeName(IName scopeName) {
		this.scopeName = scopeName;
	}
	
	
	public IBinding[] find(String name) {
		throw new UnsupportedOperationException();
	}

	public IBinding getBinding(IASTName name, boolean resolve) {
		throw new UnsupportedOperationException();
	}

	public IBinding[] getBindings(IASTName name, boolean resolve, boolean prefixLookup) {
		throw new UnsupportedOperationException();
	}

	
	
	
	public void addBinding(IBinding binding) {
		throw new UnsupportedOperationException();
	}

	public void addName(IASTName name) {
		throw new UnsupportedOperationException();
	}

	public void flushCache() {
		
	}

	public boolean isFullyCached() {
		return true;
	}

	public void removeBinding(IBinding binding) {

	}

	public void setFullyCached(boolean b) {
		
	}

	

}

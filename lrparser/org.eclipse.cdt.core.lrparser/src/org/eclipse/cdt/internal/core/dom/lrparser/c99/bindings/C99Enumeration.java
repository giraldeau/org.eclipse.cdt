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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.dom.ILinkage;
import org.eclipse.cdt.core.dom.ast.IEnumeration;
import org.eclipse.cdt.core.dom.ast.IEnumerator;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.ITypedef;
import org.eclipse.cdt.internal.core.dom.Linkage;
import org.eclipse.core.runtime.PlatformObject;

public class C99Enumeration extends PlatformObject implements IC99Binding, IEnumeration, ITypeable {

	private List<IEnumerator> enumerators = new ArrayList<IEnumerator>();	
	private String name;
	
	private IScope scope;
	
	
	public C99Enumeration() {
	}
	
	public C99Enumeration(String name) {
		this.name = name;
	}
	
	public void addEnumerator(IEnumerator e) {
		enumerators.add(e);
	}
	
	public IEnumerator[] getEnumerators() {
		return enumerators.toArray(new IEnumerator[enumerators.size()]);
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}

	public char[] getNameCharArray() {
		return name.toCharArray();
	}


	public IType getType() {
		return this;
	}
	
	public boolean isSameType(IType type) {
		 if( type == this )
            return true;
        if( type instanceof ITypedef)
            return type.isSameType( this );

        return false;
	}

	public C99Enumeration clone() {
		try {
			C99Enumeration clone = (C99Enumeration) super.clone();
			clone.enumerators = new ArrayList<IEnumerator>();	
			for(IEnumerator e : enumerators) {
				// TODO this is wrong, 
				// IEnumerator is not Cloneable so we are not returning a deep copy here
				clone.addEnumerator(e); 
			}
			return clone;
		} catch (CloneNotSupportedException e1) {
			assert false;
			return null;
		}
		
	}
	
	public ILinkage getLinkage()  {
		return Linkage.C_LINKAGE;
	}

	public IScope getScope() {
		return scope;
	}

	public void setScope(IScope scope) {
		this.scope = scope;
	}

	

}

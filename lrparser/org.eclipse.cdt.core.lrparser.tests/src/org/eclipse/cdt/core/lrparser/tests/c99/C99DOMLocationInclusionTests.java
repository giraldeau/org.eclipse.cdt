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
package org.eclipse.cdt.core.lrparser.tests.c99;

import java.util.Collections;

import org.eclipse.cdt.core.dom.ast.IASTComment;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.lrparser.BaseExtensibleLanguage;
import org.eclipse.cdt.core.dom.lrparser.c99.C99Language;
import org.eclipse.cdt.core.parser.CodeReader;
import org.eclipse.cdt.core.parser.ExtendedScannerInfo;
import org.eclipse.cdt.core.parser.IExtendedScannerInfo;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.ParserUtil;
import org.eclipse.cdt.core.parser.tests.ast2.DOMLocationInclusionTests;
import org.eclipse.cdt.internal.core.dom.SavedCodeReaderFactory;
import org.eclipse.core.resources.IFile;

public class C99DOMLocationInclusionTests extends DOMLocationInclusionTests {
	
	public C99DOMLocationInclusionTests() {
	}

	public C99DOMLocationInclusionTests(String name, Class className) {
		super(name, className);
	}

	public C99DOMLocationInclusionTests(String name) {
		super(name);
	}

	protected IASTTranslationUnit parse(IFile code, IScannerInfo s)
			throws Exception {
		
		CodeReader codeReader = new CodeReader(code.getLocation().toOSString());
		BaseExtensibleLanguage lang = getLanguage();
		IASTTranslationUnit tu = lang.getASTTranslationUnit(codeReader, s, SavedCodeReaderFactory.getInstance(), null, BaseExtensibleLanguage.OPTION_ADD_COMMENTS, ParserUtil.getParserLogService());

		return tu;
	}

	protected IASTTranslationUnit parse(IFile code) throws Exception {
	
		return parse(code, new ExtendedScannerInfo());
	}

	protected BaseExtensibleLanguage getLanguage() {
		return C99Language.getDefault();
	}


}

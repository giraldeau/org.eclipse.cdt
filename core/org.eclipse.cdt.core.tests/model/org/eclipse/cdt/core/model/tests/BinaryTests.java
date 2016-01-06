/*******************************************************************************
 * Copyright (c) 2000, 2010 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *******************************************************************************/

package org.eclipse.cdt.core.model.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.IPDOMManager;
import org.eclipse.cdt.core.model.CModelException;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.IBinary;
import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.testplugin.CProjectHelper;
import org.eclipse.cdt.core.testplugin.CTestPlugin;
import org.eclipse.cdt.core.testplugin.util.ExpectedStrings;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;

import junit.framework.JUnit4TestAdapter;

/**
 * @author Peter Graves
 * @author Francis Giraldeau
 *
 *         Tests for the ELF reader.
 */
public class BinaryTests {

	IWorkspace workspace;
	IWorkspaceRoot root;
	ICProject testProject;
	NullProgressMonitor monitor;

	/**
	 * Sets up the test fixture.
	 *
	 * Called before every test case method.
	 */
	@Before
	public void setUp() throws Exception {
		/***
		 * The tests assume that they have a working workspace and workspace
		 * root object to use to create projects/files in, so we need to get
		 * them setup first.
		 */
		workspace = ResourcesPlugin.getWorkspace();
		root = workspace.getRoot();
		monitor = new NullProgressMonitor();
		assertNotNull("Workspace was not setup", workspace);
		assertNotNull("Workspace root was not setup", root);

		/***
		 * Setup the various files, paths and projects that are needed by the
		 * tests
		 */

		testProject = CProjectHelper.createCProject("filetest", "none", IPDOMManager.ID_NO_INDEXER);
		assertNotNull("Unable to create project", testProject);

		// since our test require that we can read the debug info from the exe
		// we must set the GNU elf binary parser since the default (generic elf
		// binary parser) does not do this.
		ICProjectDescription projDesc = CoreModel.getDefault().getProjectDescription(testProject.getProject(), true);
		ICConfigurationDescription defaultConfig = projDesc.getDefaultSettingConfiguration();
		defaultConfig.remove(CCorePlugin.BINARY_PARSER_UNIQ_ID);
		defaultConfig.create(CCorePlugin.BINARY_PARSER_UNIQ_ID, "org.eclipse.cdt.core.GNU_ELF");
		CoreModel.getDefault().setProjectDescription(testProject.getProject(), projDesc);

		// @formatter:off
		String[][] toCopy = new String[][] {
			{ "resources/exe/main.c", "exetest.c" },
			{ "resources/exe/x86/o.g/main.o", "exetest.o" },
			{ "resources/exe/x86/o.g/exe_g", "test_g" },
			{ "resources/exe/ppc/be.g/exe_g", "ppctest_g" },
			{ "resources/exe/x86/o/exe", "exetest" },
			{ "resources/exebig/x86/o.g/exebig_g", "exebig_g" },
			{ "resources/testlib/x86/a.g/libtestlib_g.a", "libtestlib_g.a" },
			{ "resources/testlib/x86/so.g/libtestlib_g.so", "libtestlib_g.so" },
		};
		// @formatter:on

		for (String[] entry : toCopy) {
			copyResourceToProject(entry[0], entry[1]);
		}
	}

	private void copyResourceToProject(String src, String dst) throws IOException, CoreException {
		IFile file = testProject.getProject().getFile(dst);
		if (!file.exists()) {
			Bundle bundle = CTestPlugin.getDefault().getBundle();
			InputStream istream = bundle.getResource(src).openStream();
			file.create(istream, false, monitor);
		}
	}

	private IFile getProjectFile(String name) {
		return testProject.getProject().getFile(name);
	}

	/**
	 * Tears down the test fixture.
	 *
	 * Called after every test case method.
	 */
	@After
	public void tearDown() throws CoreException, InterruptedException {
		System.gc();
		System.runFinalization();
		CProjectHelper.delete(testProject);
	}

	/**
	 * Return test suite (backward compatibility for JUnit3 test suite)
	 * 
	 * @return test suite
	 */
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(BinaryTests.class);
	}

	/****
	 * Simple tests to make sure we can get all of a binarys children
	 */
	@Test
	public void testGetChildren() throws CoreException, FileNotFoundException {
		IBinary myBinary;
		ICElement[] elements;
		ExpectedStrings expSyms;
		// String[] myStrings = {"test.c", "_init","main.c", "_start",
		// "test2.c", "_btext"};
		// On Windows at least, it appears the .c files aren't included in the
		// binary
		String[] myStrings = { "_init", "_start", "_btext" };

		expSyms = new ExpectedStrings(myStrings);

		/***
		 * Grab the IBinary we want to test, and find all the elements in all
		 * the binarie and make sure we get everything we expect.
		 */
		myBinary = CProjectHelper.findBinary(testProject, "test_g");
		elements = myBinary.getChildren();
		for (int i = 0; i < elements.length; i++) {
			expSyms.foundString(elements[i].getElementName());
		}

		assertTrue(expSyms.getMissingString(), expSyms.gotAll());
		// assertTrue(expSyms.getExtraString(), !expSyms.gotExtra());
	}

	/***
	 * A quick check to make sure the getBSS function works as expected.
	 */
	@Test
	public void testGetBss() throws CModelException {
		IBinary bigBinary, littleBinary;
		bigBinary = CProjectHelper.findBinary(testProject, "exebig_g");
		littleBinary = CProjectHelper.findBinary(testProject, "test_g");

		assertEquals(432, bigBinary.getBSS());
		assertEquals(4, littleBinary.getBSS());
	}

	/***
	 * A quick check to make sure the getBSS function works as expected.
	 */
	@Test
	public void testGetData() throws CModelException {
		IBinary bigBinary, littleBinary;
		bigBinary = CProjectHelper.findBinary(testProject, "exebig_g");
		littleBinary = CProjectHelper.findBinary(testProject, "test_g");
		/* These two test used to fail due to pr 23602 */
		assertEquals(256, bigBinary.getData());
		assertEquals(196, littleBinary.getData());
	}

	/***
	 * A very small set of tests to make usre Binary.getCPU() seems to return
	 * something sane for the most common exe type (x86) and one other (ppc)
	 * This is not a in depth test at all.
	 */
	@Test
	public void testGetCpu() throws CModelException {
		IBinary myBinary;
		myBinary = CProjectHelper.findBinary(testProject, "exebig_g");
		assertEquals("x86", myBinary.getCPU());

		IFile ppcexefile = getProjectFile("ppctest_g");
		myBinary = CProjectHelper.findBinary(testProject, ppcexefile.getLocation().lastSegment());
		assertEquals("ppc", myBinary.getCPU());
	}

	/****
	 * A set of simple tests to make sute getNeededSharedLibs seems to be sane
	 */
	@Test
	public void testGetNeededSharedLibs() throws CModelException {
		IBinary myBinary;
		String[] exelibs = { "libsocket.so.2", "libc.so.2" };
		String[] bigexelibs = { "libc.so.2" };
		String[] gotlibs;
		ExpectedStrings exp;
		int x;

		exp = new ExpectedStrings(exelibs);
		myBinary = CProjectHelper.findBinary(testProject, "test_g");
		gotlibs = myBinary.getNeededSharedLibs();
		for (x = 0; x < gotlibs.length; x++) {
			exp.foundString(gotlibs[x]);
		}
		assertTrue(exp.getMissingString(), exp.gotAll());
		assertTrue(exp.getExtraString(), !exp.gotExtra());

		exp = new ExpectedStrings(bigexelibs);
		myBinary = CProjectHelper.findBinary(testProject, "exebig_g");
		gotlibs = myBinary.getNeededSharedLibs();
		for (x = 0; x < gotlibs.length; x++) {
			exp.foundString(gotlibs[x]);
		}
		assertTrue(exp.getMissingString(), exp.gotAll());
		assertTrue(exp.getExtraString(), !exp.gotExtra());

		exp = new ExpectedStrings(bigexelibs);
		myBinary = CProjectHelper.findBinary(testProject, "libtestlib_g.so");
		gotlibs = myBinary.getNeededSharedLibs();
		for (x = 0; x < gotlibs.length; x++) {
			exp.foundString(gotlibs[x]);
		}
		assertTrue(exp.getMissingString(), exp.gotAll());
		assertTrue(exp.getExtraString(), !exp.gotExtra());
	}

	/****
	 * Simple tests for the getSoname method;
	 */
	@Test
	public void testGetSoname() throws CModelException {
		IBinary myBinary;
		String name;
		myBinary = CProjectHelper.findBinary(testProject, "test_g");
		assertEquals("", myBinary.getSoname());

		myBinary = CProjectHelper.findBinary(testProject, "libtestlib_g.so");
		name = myBinary.getSoname();
		assertNotNull(name);
		assertEquals("libtestlib_g.so.1", name);
	}

	/***
	 * Simple tests for getText
	 */
	@Test
	public void testGetText() throws CModelException {
		IBinary bigBinary, littleBinary;
		IFile bigexe = getProjectFile("exebig_g");
		IFile exefile = getProjectFile("test_g");
		bigBinary = CProjectHelper.findBinary(testProject, bigexe.getLocation().lastSegment());
		littleBinary = CProjectHelper.findBinary(testProject, exefile.getLocation().lastSegment());
		/* These two asserts used to fail due to pr 23602 */
		assertEquals(886, bigBinary.getText());
		assertEquals(1223, littleBinary.getText());
	}

	/***
	 * Simple tests for the hadDebug call
	 */
	@Test
	public void testHasDebug() throws CModelException {
		IBinary myBinary;
		myBinary = CProjectHelper.findBinary(testProject, "test_g");
		assertEquals(true, myBinary.hasDebug());
		myBinary = CProjectHelper.findBinary(testProject, "libtestlib_g.so");
		assertEquals(true, myBinary.hasDebug());
		myBinary = CProjectHelper.findBinary(testProject, "exetest");
		assertEquals(false, myBinary.hasDebug());
	}

	/***
	 * Sanity - isBinary and isReadonly should always return true;
	 */
	@Test
	public void testisBinRead() throws CModelException {
		IBinary myBinary;
		myBinary = CProjectHelper.findBinary(testProject, "test_g");
		assertNotNull(myBinary);
		assertEquals(true, myBinary.isReadOnly());
	}

	/***
	 * Quick tests to make sure isObject works as expected.
	 */
	@Test
	public void testIsObject() throws CModelException {
		IBinary myBinary;
		myBinary = CProjectHelper.findObject(testProject, "exetest.o");
		assertEquals(true, myBinary.isObject());

		myBinary = CProjectHelper.findBinary(testProject, "test_g");
		assertEquals(false, myBinary.isObject());

		myBinary = CProjectHelper.findBinary(testProject, "libtestlib_g.so");
		assertEquals(false, myBinary.isObject());

		myBinary = CProjectHelper.findBinary(testProject, "exetest");
		assertEquals(false, myBinary.isObject());

	}

	/***
	 * Quick tests to make sure isSharedLib works as expected.
	 */
	@Test
	public void testIsSharedLib() throws CModelException {
		IBinary myBinary;

		myBinary = CProjectHelper.findObject(testProject, "exetest.o");
		assertEquals(false, myBinary.isSharedLib());

		myBinary = CProjectHelper.findBinary(testProject, "libtestlib_g.so");
		assertEquals(true, myBinary.isSharedLib());

		myBinary = CProjectHelper.findBinary(testProject, "test_g");
		assertEquals(false, myBinary.isSharedLib());

		myBinary = CProjectHelper.findBinary(testProject, "exetest");
		assertEquals(false, myBinary.isSharedLib());
	}

	/***
	 * Quick tests to make sure isExecutable works as expected.
	 */
	@Test
	public void testIsExecutable() throws InterruptedException, CModelException {
		IBinary myBinary;
		myBinary = CProjectHelper.findObject(testProject, "exetest.o");
		assertEquals(false, myBinary.isExecutable());

		myBinary = CProjectHelper.findBinary(testProject, "test_g");
		assertEquals(true, myBinary.isExecutable());

		myBinary = CProjectHelper.findBinary(testProject, "libtestlib_g.so");
		assertEquals(false, myBinary.isExecutable());

		myBinary = CProjectHelper.findBinary(testProject, "exetest");
		assertEquals(true, myBinary.isExecutable());
	}

	/***
	 * Simple sanity test to make sure Binary.isBinary returns true
	 * 
	 */
	@Test
	public void testIsBinary() throws CoreException, FileNotFoundException, Exception {
		IBinary myBinary = CProjectHelper.findBinary(testProject, "exebig_g");
		assertNotNull(myBinary);
	}

}

/*******************************************************************************
 * Copyright (c) 2002, 2007 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX Software Systems - Initial API and implementation
 * Anton Leherbauer (Wind River Systems)
 *******************************************************************************/

package org.eclipse.cdt.internal.ui.text.c.hover;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.information.IInformationProviderExtension2;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;

import org.eclipse.cdt.core.dom.IName;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTFunctionStyleMacroParameter;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorFunctionStyleMacroDefinition;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorMacroDefinition;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.ICompositeType;
import org.eclipse.cdt.core.dom.ast.IEnumeration;
import org.eclipse.cdt.core.dom.ast.IEnumerator;
import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.core.dom.ast.IMacroBinding;
import org.eclipse.cdt.core.dom.ast.IParameter;
import org.eclipse.cdt.core.dom.ast.IProblemBinding;
import org.eclipse.cdt.core.dom.ast.ITypedef;
import org.eclipse.cdt.core.dom.ast.IVariable;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPTemplateDefinition;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.index.IIndexName;
import org.eclipse.cdt.core.model.CModelException;
import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ISourceRange;
import org.eclipse.cdt.core.model.ISourceReference;
import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.core.model.IWorkingCopy;
import org.eclipse.cdt.core.parser.KeywordSetKey;
import org.eclipse.cdt.core.parser.ParserFactory;
import org.eclipse.cdt.core.parser.ParserLanguage;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.cdt.ui.IWorkingCopyManager;
import org.eclipse.cdt.ui.text.ICPartitions;

import org.eclipse.cdt.internal.core.model.ASTCache.ASTRunnable;
import org.eclipse.cdt.internal.core.model.ext.ICElementHandle;

import org.eclipse.cdt.internal.ui.editor.ASTProvider;
import org.eclipse.cdt.internal.ui.text.CCodeReader;
import org.eclipse.cdt.internal.ui.text.CHeuristicScanner;
import org.eclipse.cdt.internal.ui.util.Strings;
import org.eclipse.cdt.internal.ui.viewsupport.CElementLabels;
import org.eclipse.cdt.internal.ui.viewsupport.IndexUI;

/**
 * A text hover presenting the source of the element under the cursor.
 */
public class CSourceHover extends AbstractCEditorTextHover implements ITextHoverExtension, IInformationProviderExtension2 {

	private static final boolean DEBUG = false;

	/**
	 * Computes the source location for a given identifier.
	 */
	private static class ComputeSourceRunnable implements ASTRunnable {

		private final ITranslationUnit fTU;
		private final IRegion fTextRegion;
		private final IProgressMonitor fMonitor;
		private String fSource;

		/**
		 * @param tUnit
		 * @param textRegion 
		 */
		public ComputeSourceRunnable(ITranslationUnit tUnit, IRegion textRegion) {
			fTU= tUnit;
			fTextRegion= textRegion;
			fMonitor= new NullProgressMonitor();
			fSource= null;
		}

		/*
		 * @see org.eclipse.cdt.internal.core.model.ASTCache.ASTRunnable#runOnAST(org.eclipse.cdt.core.dom.ast.IASTTranslationUnit)
		 */
		public IStatus runOnAST(IASTTranslationUnit ast) {
			if (ast != null) {
				try {
					IASTName[] names;
					names = fTU.getLanguage().getSelectedNames(ast, fTextRegion.getOffset(), fTextRegion.getLength());
					if (names != null && names.length == 1) {
						IBinding binding= names[0].resolveBinding();
						if (binding != null) {
							if (binding instanceof IProblemBinding) {
								if (DEBUG) fSource= "Cannot resolve " + new String(names[0].toCharArray()); //$NON-NLS-1$
							} else if (binding instanceof IMacroBinding) {
								fSource= computeSourceForMacro(ast, names[0], binding);
							} else {
								fSource= computeSourceForBinding(ast, binding);
							}
							if (fSource != null) {
								return Status.OK_STATUS;
							}
						}
					}
				} catch (CoreException exc) {
					return exc.getStatus();
				} catch (DOMException exc) {
					return new Status(IStatus.ERROR, CUIPlugin.PLUGIN_ID, "Internal Error", exc); //$NON-NLS-1$
				}
			}
			return Status.CANCEL_STATUS;
		}

		/**
		 * Compute the source for a macro. If the source location of the macro definition can be determined,
		 * the source is taken from there, otherwise the source is constructed as a <code>#define</code> directive.
		 * 
		 * @param ast  the AST of the translation unit
		 * @param name  the macro occurrence in the AST
		 * @param binding   the binding of the macro name
		 * @return the source or <code>null</code>
		 * @throws CoreException 
		 */
		private String computeSourceForMacro(IASTTranslationUnit ast, IASTName name, IBinding binding) throws CoreException {
			IASTPreprocessorMacroDefinition macroDef= null;
			final char[] macroName= name.toCharArray();

			// search for macro definition, there should be a more efficient way
			IASTPreprocessorMacroDefinition[] macroDefs;
			final IASTPreprocessorMacroDefinition[] localMacroDefs= ast.getMacroDefinitions();
			for (macroDefs= localMacroDefs; macroDefs != null; macroDefs= (macroDefs == localMacroDefs) ? ast.getBuiltinMacroDefinitions() : null) {
				for (int i = 0; i < macroDefs.length; i++) {
					if (Arrays.equals(macroDefs[i].getName().toCharArray(), macroName)) {
						macroDef= macroDefs[i];
						break;
					}
				}
			}
			if (macroDef != null) {
				String source= computeSourceForName(macroDef.getName(), binding);
				if (source != null) {
					return source;
				}
				IASTFunctionStyleMacroParameter[] parameters= {};
				if (macroDef instanceof IASTPreprocessorFunctionStyleMacroDefinition) {
					parameters= ((IASTPreprocessorFunctionStyleMacroDefinition)macroDef).getParameters();
				}
				StringBuffer buf= new StringBuffer(macroName.length + macroDef.getExpansion().length() + parameters.length*5 + 10);
				buf.append("#define ").append(macroName); //$NON-NLS-1$
				if (parameters.length > 0) {
					buf.append('(');
					for (int i = 0; i < parameters.length; i++) {
						if (i > 0) {
							buf.append(", "); //$NON-NLS-1$
						}
						IASTFunctionStyleMacroParameter parameter = parameters[i];
						buf.append(parameter.getParameter());
					}
					buf.append(')');
				}
				String expansion= macroDef.getExpansion();
				if (expansion != null) {
					buf.append(' ').append(expansion);
				}
				return buf.toString();
			}
			return null;
		}

		/**
		 * Find a definition or declaration for the given binding and returns the source for it.
		 * Definitions are preferred over declarations. In case of multiple definitions or declarations,
		 * and the first name which yields source is taken.
		 * 
		 * @param ast  the AST of the translation unit
		 * @param binding  the binding
		 * @return a source string or <code>null</code>, if no source could be computed
		 * @throws CoreException  if the source file could not be loaded or if there was a
		 *                        problem with the index
		 * @throws DOMException  if there was an internal problem with the DOM
		 */
		private String computeSourceForBinding(IASTTranslationUnit ast, IBinding binding) throws CoreException, DOMException {
			IName[] names= findDefinitions(ast, binding);
			if (names.length == 0) {
				names= findDeclarations(ast, binding);
			}
			if (names.length > 0) {
				for (int i = 0; i < names.length; i++) {
					String source= computeSourceForName(names[0], binding);
					if (source != null) {
						return source;
					}
				}
				// fallback: return signature only
				ICElementHandle cElement= null;
				if (names[0] instanceof IIndexName) {
					cElement= IndexUI.getCElementForName(fTU.getCProject(), ast.getIndex(), (IIndexName)names[0]);
				} else if (names[0] instanceof IASTName) {
					cElement= IndexUI.getCElementForName(fTU.getCProject(), ast.getIndex(), (IASTName)names[0]);
				}
				if (cElement != null) {
					return CElementLabels.getTextLabel(cElement, CElementLabels.DEFAULT_QUALIFIED);
				}
			}
			return null;
		}

		/**
		 * Get the source for the given name from the underlying file.
		 * 
		 * @param name  the name to get the source for
		 * @param binding  the binding of the name
		 * @return the source string or <code>null</code>, if the source could not be computed
		 * @throws CoreException  if the file could not be loaded
		 */
		private String computeSourceForName(IName name, IBinding binding) throws CoreException {
			IASTFileLocation fileLocation= name.getFileLocation();
			if (fileLocation == null) {
				return null;
			}
			String fileName= fileLocation.getFileName();
			if (DEBUG) System.out.println("[CSourceHover] Computing source for " + new String(name.toCharArray()) + " in " + fileName);  //$NON-NLS-1$//$NON-NLS-2$
			IPath location= Path.fromOSString(fileName);
			LocationKind locationKind= LocationKind.LOCATION;
			if (name instanceof IASTName) {
				IASTName astName= (IASTName)name;
				if (astName.getContainingFilename().equals(fileName)) {
					// reuse editor buffer for names local to the translation unit
					location= fTU.getPath();
					locationKind= LocationKind.IFILE;
				}
			}
			ITextFileBufferManager mgr= FileBuffers.getTextFileBufferManager();
			mgr.connect(location, locationKind, fMonitor);
			ITextFileBuffer buffer= mgr.getTextFileBuffer(location, locationKind);
			String source;
			try {
				IRegion nameRegion= new Region(fileLocation.getNodeOffset(), fileLocation.getNodeLength());
				final int nameOffset= nameRegion.getOffset();
				int sourceStart= nameOffset;
				int sourceEnd= nameOffset + nameRegion.getLength();
				IDocument doc= buffer.getDocument();
				// expand source range to include preceding comment, if any
				CHeuristicScanner scanner= new CHeuristicScanner(doc);
				int commentBound;
				if (binding instanceof IParameter) {
					sourceStart= scanner.scanBackward(nameOffset, CHeuristicScanner.UNBOUND, new char[] { '(', ',' });
					if (sourceStart == CHeuristicScanner.NOT_FOUND) {
						return null;
					}
					sourceStart= scanner.findNonWhitespaceForward(sourceStart + 1, nameOffset);
					if (sourceStart == CHeuristicScanner.NOT_FOUND) {
						sourceStart = nameOffset;
					}
				} else if (binding instanceof IEnumerator) {
					sourceStart= scanner.scanBackward(nameOffset, CHeuristicScanner.UNBOUND, new char[] { '{', ',' });
					if (sourceStart == CHeuristicScanner.NOT_FOUND) {
						return null;
					}
					sourceStart= scanner.findNonWhitespaceForward(sourceStart + 1, nameOffset);
					if (sourceStart == CHeuristicScanner.NOT_FOUND) {
						sourceStart = nameOffset;
					}
				} else {
					final int nameLine= doc.getLineOfOffset(nameOffset);
					sourceStart= doc.getLineOffset(nameLine);
					commentBound= scanner.scanBackward(sourceStart, CHeuristicScanner.UNBOUND, new char[] { '{', '}', ';' });
					if (commentBound == CHeuristicScanner.NOT_FOUND) {
						commentBound= -1;
					}
					int commentStart= searchCommentBackward(doc, sourceStart, commentBound);
					if (commentStart >= 0) {
						sourceStart= commentStart;
					}
				}
				// expand forward to the end of the definition/declaration
				if (binding instanceof IMacroBinding) {
					ITypedRegion partition= TextUtilities.getPartition(doc, ICPartitions.C_PARTITIONING, nameOffset, false);
					if (ICPartitions.C_PREPROCESSOR.equals(partition.getType())) {
						sourceStart= partition.getOffset();
						sourceEnd= sourceStart + partition.getLength();
					}
 				} else {
					boolean searchBrace= false;
					boolean searchSemi= false;
					boolean searchComma= false;
					if (!name.isDefinition()) {
						searchSemi= true;
					} else if (binding instanceof ICompositeType || binding instanceof IEnumeration || binding instanceof IFunction) {
						searchBrace= true;
					} else if (binding instanceof ICPPTemplateDefinition) {
						searchBrace= true;
					} else if (binding instanceof IParameter || binding instanceof IEnumerator) {
						searchComma= true;
					} else if (binding instanceof IVariable || binding instanceof ITypedef) {
						searchSemi= true;
					}
					if (searchBrace) {
						int brace= scanner.scanForward(nameOffset, CHeuristicScanner.UNBOUND, '{');
						if (brace != CHeuristicScanner.NOT_FOUND) {
							sourceEnd= scanner.findClosingPeer(brace + 1, '{', '}');
							if (sourceEnd == CHeuristicScanner.NOT_FOUND) {
								sourceEnd= doc.getLength();
							}
						}
						// expand region to include whole line
						IRegion lineRegion= doc.getLineInformationOfOffset(sourceEnd);
						sourceEnd= lineRegion.getOffset() + lineRegion.getLength();
					} else if (searchSemi) {
						int semi= scanner.scanForward(nameOffset, CHeuristicScanner.UNBOUND, ';');
						if (semi != CHeuristicScanner.NOT_FOUND) {
							sourceEnd= semi+1;
						}
						// expand region to include whole line
						IRegion lineRegion= doc.getLineInformationOfOffset(sourceEnd);
						sourceEnd= lineRegion.getOffset() + lineRegion.getLength();
					} else if (searchComma) {
						int bound;
						if (binding instanceof IParameter) {
							bound= scanner.findClosingPeer(nameOffset, '(', ')');
						} else {
							bound= scanner.findClosingPeer(nameOffset, '{', '}');
						}
						if (bound == CHeuristicScanner.NOT_FOUND) {
							bound= Math.min(doc.getLength(), nameOffset + 100);
						}
						int comma= scanner.scanForward(nameOffset, bound, ',');
						if (comma == CHeuristicScanner.NOT_FOUND) {
							sourceEnd= bound;
						} else {
							sourceEnd= comma;
						}
						// expand region to include whole line if rest is white space or comment
						int nextNonWS= scanner.findNonWhitespaceForward(sourceEnd + 1, CHeuristicScanner.UNBOUND);
						if (doc.getLineOfOffset(nextNonWS) > doc.getLineOfOffset(sourceEnd)) {
							IRegion lineRegion= doc.getLineInformationOfOffset(sourceEnd);
							sourceEnd= lineRegion.getOffset() + lineRegion.getLength();
						}
					}
				}
				
				source= buffer.getDocument().get(sourceStart, sourceEnd - sourceStart);
				return source;

			} catch (BadLocationException exc) {
				// ignore - should not happen anyway
				if (DEBUG) exc.printStackTrace();
			} finally {
				mgr.disconnect(location, LocationKind.LOCATION, fMonitor);
			}
			return null;
		}

		/**
		 * Search for definitions for the given binding.
		 * 
		 * @param ast  the AST of the translation unit
		 * @param binding  the binding
		 * @return an array of definitions, never <code>null</code>
		 * @throws CoreException
		 */
		private IName[] findDefinitions(IASTTranslationUnit ast,
				IBinding binding) throws CoreException {
			IName[] declNames= ast.getDefinitionsInAST(binding);
			if (declNames.length == 0 && ast.getIndex() != null) {
				// search definitions in index
				declNames = ast.getIndex().findDefinitions(binding);
			}
			return declNames;
		}

		/**
		 * Search for declarations for the given binding.
		 * 
		 * @param ast  the AST of the translation unit
		 * @param binding  the binding
		 * @return an array of declarations, never <code>null</code>
		 * @throws CoreException
		 */
		private IName[] findDeclarations(IASTTranslationUnit ast,
				IBinding binding) throws CoreException {
			IName[] declNames= ast.getDeclarationsInAST(binding);
			if (declNames.length == 0 && ast.getIndex() != null) {
				// search declarations in index
				declNames= ast.getIndex().findNames(binding, IIndex.FIND_DECLARATIONS);
			}
			return declNames;
		}

		/**
		 * @return the computed source or <code>null</code>, if no source could be computed
		 */
		public String getSource() {
			return fSource;
		}

	}

	/**
	 * 
	 */
	public CSourceHover() {
		super();
	}

	/*
	 * @see ITextHover#getHoverInfo(ITextViewer, IRegion)
	 */
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		IEditorPart editor = getEditor();
		if (editor != null) {
			IEditorInput input= editor.getEditorInput();
			IWorkingCopyManager manager= CUIPlugin.getDefault().getWorkingCopyManager();				
			IWorkingCopy copy = manager.getWorkingCopy(input);
			if (copy == null) {
				return null;
			}
			
			String expression;
			try {
				expression = textViewer.getDocument().get(hoverRegion.getOffset(), hoverRegion.getLength());
				expression = expression.trim();
				if (expression.length() == 0)
					return null;

				String source= null;

				ICElement curr = copy.getElement(expression);
				if (curr == null) {
					// Try with the indexer
					source= searchInIndex(expression, hoverRegion);
				} else {
					source= getSourceForCElement(textViewer.getDocument(), curr);
				}
				if (source == null || source.trim().length() == 0)
					return null;

				// we are actually interested in the comments, too.
//				source= removeLeadingComments(source);

				String delim= System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$

				String[] sourceLines= Strings.convertIntoLines(source);
				String firstLine= sourceLines[0];
				if (!Character.isWhitespace(firstLine.charAt(0)))
					sourceLines[0]= ""; //$NON-NLS-1$
				Strings.trimIndentation(sourceLines, getTabWidth());

				if (!Character.isWhitespace(firstLine.charAt(0)))
					sourceLines[0]= firstLine;

				source = Strings.concatenate(sourceLines, delim);
				return source;

			} catch (BadLocationException e) {
			} catch (CModelException e) {
			}
		}
		return null;
	}

	/**
	 * Return the source for the given element including the preceding comment.
	 * 
	 * @param document  the document of the current editor
	 * @param cElement  the element to compute the source from
	 * @return  the source or <code>null</code>
	 * @throws CModelException 
	 * @throws BadLocationException 
	 */
	private String getSourceForCElement(IDocument doc, ICElement cElement) throws CModelException, BadLocationException {
		if (!(cElement instanceof ISourceReference)) {
			return null;
		}
		ISourceRange sourceRange= ((ISourceReference)cElement).getSourceRange();
		int sourceStart= sourceRange.getStartPos();
		int sourceEnd= sourceStart + sourceRange.getLength();
		CHeuristicScanner scanner= new CHeuristicScanner(doc);
		int commentBound= scanner.scanBackward(sourceStart, CHeuristicScanner.UNBOUND, new char[] { '{', '}', ';' });
		if (commentBound == CHeuristicScanner.NOT_FOUND) {
			commentBound= -1;
		}
		int commentStart= searchCommentBackward(doc, sourceStart, commentBound);
		if (commentStart >= 0) {
			sourceStart= commentStart;
		}
		return doc.get(sourceStart, sourceEnd - sourceStart + 1);
	}

	/**
	 * Searches the start of the comment preceding the given source offset.
	 * Continuous line comments are considered as one comment until a block
	 * comment is reached or a non-comment partition.
	 * 
	 * @param doc  the document
	 * @param start  the start of the backward search
	 * @param bound  search boundary (exclusive)
	 * @return the comment start offset or <code>-1</code>, if no suitable comment was found
	 * @throws BadLocationException 
	 */
	private static int searchCommentBackward(IDocument doc, int start, int bound) throws BadLocationException {
		int firstLine= doc.getLineOfOffset(start);
		if (firstLine == 0) {
			return -1;
		}
		int currentOffset= doc.getLineOffset(firstLine - 1);
		int commentOffset= -1;
		while (currentOffset > bound) {
			ITypedRegion partition= TextUtilities.getPartition(doc, ICPartitions.C_PARTITIONING, currentOffset, false);
			currentOffset= partition.getOffset() - 1;
			if (ICPartitions.C_MULTI_LINE_COMMENT.equals(partition.getType())) {
				int previousCommentOffset= commentOffset;
				commentOffset = partition.getOffset();
				final int startLine= doc.getLineOfOffset(commentOffset);
				final int lineOffset= doc.getLineOffset(startLine);
				if (commentOffset == lineOffset || 
						doc.get(lineOffset, commentOffset - lineOffset).trim().length() == 0) {
					return lineOffset;
				}
				return previousCommentOffset;
			} else if (ICPartitions.C_SINGLE_LINE_COMMENT.equals(partition.getType())) {
				commentOffset = partition.getOffset();
				final int startLine= doc.getLineOfOffset(commentOffset);
				final int lineOffset= doc.getLineOffset(startLine);
				if (commentOffset == lineOffset || 
						doc.get(lineOffset, commentOffset - lineOffset).trim().length() == 0) {
					continue;
				}
				return commentOffset;
			} else if (IDocument.DEFAULT_CONTENT_TYPE.equals(partition.getType())) {
				if (commentOffset >= 0) {
					break;
				}
			} else {
				break;
			}
		}
		return commentOffset;
	}

	private static int getTabWidth() {
		return 4;
	}


	/**
	 * Strip the leading comment from the given source string.
	 * 
	 * @param source
	 * @return  the source string without leading comments
	 */
	protected static String removeLeadingComments(String source) {
		CCodeReader reader= new CCodeReader();
		IDocument document= new Document(source);
		int i;
		try {
			reader.configureForwardReader(document, 0, document.getLength(), true, false);
			int c= reader.read();
			while (c != -1 && (c == '\r' || c == '\n')) {
				c= reader.read();
			}
			i= reader.getOffset();
			reader.close();
		} catch (IOException ex) {
			i= 0;
		} finally {
			try {
				if (reader != null)
					reader.close();
			} catch (IOException ex) {
				CUIPlugin.getDefault().log(ex);
			}
		}

		if (i < 0)
			return source;
		return source.substring(i);
	}

	private String searchInIndex(String name, IRegion textRegion) {
		//Before trying a search lets make sure that the user is not hovering over a keyword 
		if (selectionIsKeyword(name))
			return null;

		IEditorPart editor = getEditor();
		if (editor != null) {
			IEditorInput input= editor.getEditorInput();
			IWorkingCopyManager manager= CUIPlugin.getDefault().getWorkingCopyManager();				
			IWorkingCopy copy= manager.getWorkingCopy(input);
			
			if (copy != null) {
				IProgressMonitor monitor= new NullProgressMonitor();
				ComputeSourceRunnable computer= new ComputeSourceRunnable(copy, textRegion);
				ASTProvider.getASTProvider().runOnAST(copy, ASTProvider.WAIT_ACTIVE_ONLY, monitor, computer);
				return computer.getSource();
			}
		}
		return null;
	}


	/**
	 * Test whether the given name is a known keyword.
	 * 
	 * @param name
	 * @return <code>true</code> if the name is a known keyword or <code>false</code> if the
	 *         name is not considered a keyword
	 */
	private boolean selectionIsKeyword(String name) {
		Set keywords= ParserFactory.getKeywordSet(KeywordSetKey.KEYWORDS, ParserLanguage.CPP);
		return keywords.contains(name);
	}

	/*
	 * @see org.eclipse.jface.text.ITextHoverExtension#getHoverControlCreator()
	 * @since 3.0
	 */
	public IInformationControlCreator getHoverControlCreator() {
		return new IInformationControlCreator() {
			public IInformationControl createInformationControl(Shell parent) {
				return new SourceViewerInformationControl(parent, getTooltipAffordanceString());
			}
		};
	}

	/*
	 * @see IInformationProviderExtension2#getInformationPresenterControlCreator()
	 * @since 3.0
	 */
	public IInformationControlCreator getInformationPresenterControlCreator() {
		return new IInformationControlCreator() {
			public IInformationControl createInformationControl(Shell parent) {
				int shellStyle= SWT.RESIZE;
				int style= SWT.V_SCROLL | SWT.H_SCROLL;				
				return new SourceViewerInformationControl(parent, shellStyle, style);
			}
		};
	}
}

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
package org.eclipse.cdt.core.dom.lrparser.action;

import java.util.Collections;
import java.util.List;

import lpg.lpgjavaruntime.IToken;

import org.eclipse.cdt.core.dom.ast.IASTArrayDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTArrayModifier;
import org.eclipse.cdt.core.dom.ast.IASTArraySubscriptExpression;
import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTBreakStatement;
import org.eclipse.cdt.core.dom.ast.IASTCaseStatement;
import org.eclipse.cdt.core.dom.ast.IASTCastExpression;
import org.eclipse.cdt.core.dom.ast.IASTCompletionNode;
import org.eclipse.cdt.core.dom.ast.IASTCompoundStatement;
import org.eclipse.cdt.core.dom.ast.IASTConditionalExpression;
import org.eclipse.cdt.core.dom.ast.IASTContinueStatement;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTDefaultStatement;
import org.eclipse.cdt.core.dom.ast.IASTDoStatement;
import org.eclipse.cdt.core.dom.ast.IASTEnumerationSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpressionList;
import org.eclipse.cdt.core.dom.ast.IASTExpressionStatement;
import org.eclipse.cdt.core.dom.ast.IASTForStatement;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTGotoStatement;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTInitializer;
import org.eclipse.cdt.core.dom.ast.IASTInitializerExpression;
import org.eclipse.cdt.core.dom.ast.IASTInitializerList;
import org.eclipse.cdt.core.dom.ast.IASTLabelStatement;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTNullStatement;
import org.eclipse.cdt.core.dom.ast.IASTPointerOperator;
import org.eclipse.cdt.core.dom.ast.IASTProblem;
import org.eclipse.cdt.core.dom.ast.IASTProblemHolder;
import org.eclipse.cdt.core.dom.ast.IASTReturnStatement;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IASTTypeId;
import org.eclipse.cdt.core.dom.ast.IASTTypeIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTEnumerationSpecifier.IASTEnumerator;
import org.eclipse.cdt.core.dom.ast.c.ICASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTCastExpression;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTSimpleDeclSpecifier;
import org.eclipse.cdt.core.dom.lrparser.IParserActionTokenProvider;
import org.eclipse.cdt.core.dom.lrparser.util.DebugUtil;
import org.eclipse.cdt.internal.core.dom.parser.ASTNode;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTNode;


/**
 * Parser semantic actions that are common to both C and C++.
 * 
 * @author Mike Kucera
 */
@SuppressWarnings("restriction")
public abstract class BuildASTParserAction {


	/**
	 * Used with very simple optional rules that just say
	 * that some particular token or keyword is optional.
	 * The presence of the PLACE_HOLDER on the stack means that the keyword
	 * was parsed, the presence of null means the keyword wasn't parsed. 
	 * 
	 * @see BuildASTParserAction#consumePlaceHolder()
	 * @see BuildASTParserAction#consumeEmpty()
	 */
	protected static final Object PLACE_HOLDER = Boolean.TRUE;
	
	
	// turn debug tracing on and off
	// TODO move this into an AspectJ project
	protected static final boolean TRACE_ACTIONS = true;
	protected static final boolean TRACE_AST_STACK = true;
	
	
	/** Stack that holds the intermediate nodes as the AST is being built */
	protected final ScopedStack<Object> astStack = new ScopedStack<Object>();
	
	/** Provides an interface to the token stream */
	protected final IParserActionTokenProvider parser;
	
	/** The completion node, only generated during a completion parse */
	protected ASTCompletionNode completionNode;
	
	/** The root node is created outside the parser because it is also needed by the preprocessor */
	protected final IASTTranslationUnit tu;

	/** Abstract factory for creating AST node objects */
	private final IASTNodeFactory nodeFactory;
	
	
	
	/**
	 * Completion tokens are represented by different kinds by different parsers.
	 */
	protected abstract boolean isCompletionToken(IToken token);
	
	
	/**
	 * Create a new parser action.
	 * @param tu Root node of the AST, its list of declarations should be empty.
	 * @throws NullPointerException if any of the parameters are null
	 */
	public BuildASTParserAction(IASTNodeFactory nodeFactory, IParserActionTokenProvider parser, IASTTranslationUnit tu) {
		if(nodeFactory == null)
			throw new NullPointerException("nodeFactory is null"); //$NON-NLS-1$
		if(parser == null)
			throw new NullPointerException("parser is null"); //$NON-NLS-1$
		if(tu == null)
			throw new NullPointerException("tu is null"); //$NON-NLS-1$
		
		this.nodeFactory = nodeFactory;
		this.parser = parser;
		this.tu = tu;
	}

	
	/**
	 * Creates a completion node if one does not yet exist and adds the 
	 * given name to it.
	 */
	protected void addNameToCompletionNode(IASTName name, String prefix) {
		if(completionNode == null) {
			prefix = (prefix == null || prefix.length() == 0) ? null : prefix;
			completionNode = nodeFactory.newCompletionNode(prefix, tu);
		}
		
		completionNode.addName(name);
	}
	
	
	/**
	 * Returns the completion node if this is a completion parse, null otherwise.
	 */
	public IASTCompletionNode getASTCompletionNode() {
		return completionNode;
	}
	
	
	
	
	protected static int offset(IToken token) {
		return token.getStartOffset();
	}

	protected static int offset(IASTNode node) {
		return ((ASTNode)node).getOffset();
	}

	protected static int length(IToken token) {
		return endOffset(token) - offset(token);
	}

	protected static int length(IASTNode node) {
		return ((ASTNode)node).getLength();
	}

	protected static int endOffset(IASTNode node) {
		return offset(node) + length(node);
	}

	protected static int endOffset(IToken token) {
		return token.getEndOffset();
	}


	protected void setOffsetAndLength(IASTNode node) {
		int ruleOffset = parser.getLeftIToken().getStartOffset();
		int ruleLength = parser.getRightIToken().getEndOffset() - ruleOffset;
		((ASTNode)node).setOffsetAndLength(ruleOffset, ruleLength);
	}

	protected static void setOffsetAndLength(IASTNode node, IToken token) {
		((ASTNode)node).setOffsetAndLength(offset(token), length(token));
	}

	protected static void setOffsetAndLength(IASTNode node, int offset, int length) {
		((ASTNode)node).setOffsetAndLength(offset, length);
	}
	
	
	
	/**
	 * Creates a IASTName node from an identifier token.
	 * If the token is a completion token then it is added to the completion node.
	 */
	protected IASTName createName(IToken token) {
		IASTName name = nodeFactory.newName(token.toString().toCharArray()); // TODO, token.toCharArray();
		setOffsetAndLength(name, token); 
		
		if(isCompletionToken(token))
			addNameToCompletionNode(name, token.toString());
		
		return name;
	}
	
	
	
	
	/*************************************************************************************************************
	 * Start of actions.
	 ************************************************************************************************************/
	
	

	/**
	 * Method that is called by the special <openscope> production
	 * in order to create a new scope in the AST stack.
	 */
	public void openASTScope() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		astStack.openScope();
	}
	
	
	
	/**
	 * Place null on the stack.
	 * Usually called for optional element to indicate the element
	 * was not parsed.
	 */
	public void consumeEmpty() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		astStack.push(null);
	}

	
	/**
	 * Place a marker on the stack.
	 * Usually used for very simple optional elements to indicate
	 * the element was parsed. Usually the existence of an AST node
	 * on the stack is used instead of the marker, but for simple
	 * cases like an optional keyword this action is useful. 
	 */
	public void consumePlaceHolder() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		astStack.push(PLACE_HOLDER);
	}
	
	
	
	/**
	 * Gets the current token and places it on the stack for later consumption.
	 */
	public void consumeDeclSpecToken() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		astStack.push(parser.getRightIToken());
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	

	
	public void consumeTranslationUnit() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		// can't close the outermost scope
		// the outermost scope may be empty if there are no tokens in the file
		for(Object o : astStack.topScope()) {
			tu.addDeclaration((IASTDeclaration)o);
		}

		// this is the same way that the DOM parser computes the length
		IASTDeclaration[] declarations = tu.getDeclarations();
        if (declarations.length != 0) {
            CASTNode d = (CASTNode) declarations[declarations.length-1];
            setOffsetAndLength(tu, 0, d.getOffset() + d.getLength());
        } 

        if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	
  	/**
  	 * Consumes a single identifier token.
  	 */
  	public void consumeIdentifierName() {
  		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
  		
  		IToken token = parser.getRightIToken();
  		IASTName name = createName(token);
  		astStack.push(name);
  		
  		if(TRACE_AST_STACK) System.out.println(astStack);
  	}
  	
  	
	
	/**
	 * @param kind One of the kind flags from IASTLiteralExpression or ICPPASTLiteralExpression
	 * @see IASTLiteralExpression
	 * @see ICPPASTLiteralExpression
	 */
	public void consumeExpressionLiteral(int kind) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IToken token = parser.getRightIToken();
		String rep = token.toString();
		
		// Strip the quotes from string literals, this is just to be consistent
		// with the dom parser (i.e. to make a test pass)
//		if(kind == IASTLiteralExpression.lk_string_literal && 
//				rep.startsWith("\"") && rep.endsWith("\"")) {
//			rep = rep.substring(1, rep.length()-1);			
//		}
		
		IASTLiteralExpression expr = nodeFactory.newLiteralExpression(kind, rep);
		setOffsetAndLength(expr, token);
		astStack.push(expr);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	

	public void consumeExpressionBracketed() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression operand = (IASTExpression) astStack.pop();
		IASTUnaryExpression expr = nodeFactory.newUnaryExpression(IASTUnaryExpression.op_bracketedPrimary, operand);
        setOffsetAndLength(expr);
		astStack.push(expr);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	

	public void consumeExpressionID() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTName name = createName(parser.getRightIToken());
		IASTIdExpression expr = nodeFactory.newIdExpression(name);
        setOffsetAndLength(expr);
        astStack.push(expr);
        
        if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	public void consumeExpressionName() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTName name = (IASTName) astStack.pop();
		IASTIdExpression expr = nodeFactory.newIdExpression(name);
        setOffsetAndLength(expr);
        astStack.push(expr);
        
        if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * expression ::= <openscope-ast> expression_list_actual
	 */
	public void consumeExpressionList() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		List<Object> expressions = astStack.closeScope();
		if(expressions.size() == 1) {
			astStack.push(expressions.get(0));
		}
		else {
			IASTExpressionList exprList = nodeFactory.newExpressionList();
			for(Object o : expressions) {
				exprList.addExpression((IASTExpression)o);
			}
			astStack.push(exprList);
		}
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * postfix_expression ::= postfix_expression '[' expression ']'
	 */
	public void consumeExpressionArraySubscript() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression subscript = (IASTExpression) astStack.pop();
		IASTExpression arrayExpr = (IASTExpression) astStack.pop();
		IASTArraySubscriptExpression expr = nodeFactory.newArraySubscriptExpression(arrayExpr, subscript);
		setOffsetAndLength(expr);
		astStack.push(expr);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * postfix_expression ::= postfix_expression '(' expression_list_opt ')'
	 */
	public void consumeExpressionFunctionCall() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression argList = (IASTExpression) astStack.pop(); // may be null
		IASTExpression idExpr  = (IASTExpression) astStack.pop();
		
		IASTFunctionCallExpression expr = nodeFactory.newFunctionCallExpression(idExpr, argList);
		setOffsetAndLength(expr);
		astStack.push(expr);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}

	
	/**
	 * @param operator constant for {@link ICPPASTCastExpression}
	 */
	public void consumeExpressionCast(int operator) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression operand = (IASTExpression) astStack.pop();
		IASTTypeId typeId = (IASTTypeId) astStack.pop();
		IASTCastExpression expr = nodeFactory.newCastExpression(operator, typeId, operand);
		setOffsetAndLength(expr);
		astStack.push(expr);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * Lots of rules, no need to list them.
	 * @param operator From IASTUnaryExpression
	 */
	public void consumeExpressionUnaryOperator(int operator) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression operand = (IASTExpression) astStack.pop();
		IASTUnaryExpression expr = nodeFactory.newUnaryExpression(operator, operand);
		setOffsetAndLength(expr);
		astStack.push(expr);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	
	/**
	 * unary_operation ::= 'sizeof' '(' type_name ')'
	 * @see consumeExpressionUnaryOperator For the other use of sizeof
	 */
	public void consumeExpressionTypeId(int operator) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTTypeId typeId = (IASTTypeId) astStack.pop();
		IASTTypeIdExpression expr = nodeFactory.newTypeIdExpression(operator, typeId);
		setOffsetAndLength(expr);
		astStack.push(expr);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	
	/**
	 * Lots of rules, no need to list them all.
	 * @param op Field from IASTBinaryExpression
	 */
	public void consumeExpressionBinaryOperator(int op) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression expr2 = (IASTExpression) astStack.pop();
		IASTExpression expr1 = (IASTExpression) astStack.pop();
		IASTBinaryExpression binExpr = nodeFactory.newBinaryExpression(op, expr1, expr2);
		setOffsetAndLength(binExpr);
		astStack.push(binExpr);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * conditional_expression ::= logical_OR_expression '?' expression ':' conditional_expression
	 */
	public void consumeExpressionConditional() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression expr3 = (IASTExpression) astStack.pop();
		IASTExpression expr2 = (IASTExpression) astStack.pop();
		IASTExpression expr1 = (IASTExpression) astStack.pop();
		IASTConditionalExpression condExpr = nodeFactory.newConditionalExpession(expr1, expr2, expr3);
		setOffsetAndLength(condExpr);
		astStack.push(condExpr);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * labeled_statement ::= label_identifier ':' statement
	 * label_identifier ::= identifier 
	 */
	public void consumeStatementLabeled(/*IBinding binding*/) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTStatement body = (IASTStatement) astStack.pop();
		IASTName label = createName(parser.getLeftIToken());
		//label.setBinding(binding);
		IASTLabelStatement stat = nodeFactory.newLabelStatement(label, body);
		setOffsetAndLength(stat);
		astStack.push(stat);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * labeled_statement ::= case constant_expression ':'
	 */
	public void consumeStatementCase() { 
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression expr = (IASTExpression) astStack.pop();
		IASTCaseStatement caseStatement = nodeFactory.newCaseStatement(expr);
		setOffsetAndLength(caseStatement);
		astStack.push(caseStatement);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * labeled_statement ::= default ':'
	 */
	public void consumeStatementDefault() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTDefaultStatement stat = nodeFactory.newDefaultStatement();
		setOffsetAndLength(stat);
		astStack.push(stat);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	
	/**
	 * expression_statement ::= ';'
	 */
	public void consumeStatementNull() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTNullStatement stat = nodeFactory.newNullStatement();
		setOffsetAndLength(stat);
		astStack.push(stat);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * expression_statement ::= expression ';'
	 */
	public void consumeStatementExpression() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression expr = (IASTExpression) astStack.pop();
		IASTExpressionStatement stat = nodeFactory.newExpressionStatement(expr);
		setOffsetAndLength(stat);
		astStack.push(stat);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * compound_statement ::= <openscope> '{' block_item_list '}'
	 * 
	 * block_item_list ::= block_item | block_item_list block_item
	 */
	public void consumeStatementCompoundStatement(boolean hasStatementsInBody) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTCompoundStatement block = nodeFactory.newCompoundStatement();
		
		if(hasStatementsInBody) {
			for(Object o : astStack.closeScope()) {
				block.addStatement((IASTStatement)o);
			}
		}
		
		setOffsetAndLength(block);
		astStack.push(block);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}

	
	/**
	 * iteration_statement_matched
	 *     ::= 'do' statement 'while' '(' expression ')' ';'
	 */
	public void consumeStatementDoLoop() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression condition = (IASTExpression) astStack.pop();
		IASTStatement body = (IASTStatement) astStack.pop();
		IASTDoStatement stat = nodeFactory.newDoStatement(body, condition);
		setOffsetAndLength(stat);
		astStack.push(stat);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	

	/**
	 * jump_statement ::= goto goto_identifier ';'
	 */
	public void consumeStatementGoto(/*IBinding binding*/) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTName name = createName(parser.getRuleTokens().get(1));
		//name.setBinding(binding);
		IASTGotoStatement gotoStat = nodeFactory.newGotoStatement(name);
		setOffsetAndLength(gotoStat);
		astStack.push(gotoStat);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * jump_statement ::= continue ';'
	 */
	public void consumeStatementContinue() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTContinueStatement stat = nodeFactory.newContinueStatement();
		setOffsetAndLength(stat);
		astStack.push(stat);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * jump_statement ::= break ';'
	 */
	public void consumeStatementBreak() {   
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTBreakStatement stat = nodeFactory.newBreakStatement();
		setOffsetAndLength(stat);
		astStack.push(stat);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * jump_statement ::= return ';'
	 * jump_statement ::= return expression ';'
	 */
	public void consumeStatementReturn(boolean hasExpr) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression expr = hasExpr ? (IASTExpression) astStack.pop() : null;
		IASTReturnStatement returnStat = nodeFactory.newReturnStatement(expr);
		setOffsetAndLength(returnStat);
		astStack.push(returnStat);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}

	
	
	/**
	 * iteration_statement_matched
	 *     ::= 'for' '(' expression_opt ';' expression_opt ';' expression_opt ')' statement
	 */
	public void consumeStatementForLoop() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTStatement body = (IASTStatement) astStack.pop();
		// these two expressions may be null, see consumeExpressionOptional()
		IASTExpression expr3 = (IASTExpression) astStack.pop();
		IASTExpression expr2 = (IASTExpression) astStack.pop();
		
		IASTNode node = (IASTNode) astStack.pop(); // may be an expression or a declaration
		
		IASTStatement initializer;
		if(node instanceof IASTExpression)
			initializer = nodeFactory.newExpressionStatement((IASTExpression)node);
		else if(node instanceof IASTDeclaration)
			initializer = nodeFactory.newDeclarationStatement((IASTDeclaration)node);
		else // its null
			initializer = nodeFactory.newNullStatement();
		
		IASTForStatement forStat = nodeFactory.newForStatement(initializer, expr2, expr3, body);
		setOffsetAndLength(forStat);
		astStack.push(forStat);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}

	
	/**
	 * type_name ::= specifier_qualifier_list
     *             | specifier_qualifier_list abstract_declarator
	 */
	public void consumeTypeId(boolean hasDeclarator) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTDeclarator declarator;
		if(hasDeclarator)
			declarator = (IASTDeclarator) astStack.pop();
		else
			declarator = nodeFactory.newDeclarator(nodeFactory.newName());
			
		IASTDeclSpecifier declSpecifier = (IASTDeclSpecifier) astStack.pop();
		IASTTypeId typeId = nodeFactory.newTypeId(declSpecifier, declarator);
		setOffsetAndLength(typeId);
		astStack.push(typeId);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * TODO: do I really want to share declaration rules between the two parsers.
	 * Even if there is potential for reuse it still may be cleaner to leave the 
	 * common stuff to just simple expressions and statements.
	 * 
	 * declaration ::= declaration_specifiers <openscope> init_declarator_list ';'
	 * declaration ::= declaration_specifiers  ';'
	 */
	public void consumeDeclarationSimple(boolean hasDeclaratorList) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		List<Object> declarators = (hasDeclaratorList) ? astStack.closeScope() : Collections.emptyList();
		ICASTDeclSpecifier declSpecifier = (ICASTDeclSpecifier) astStack.pop(); // may be null
		
		IASTSimpleDeclaration declaration = nodeFactory.newSimpleDeclaration(declSpecifier);
		
		for(Object declarator : declarators)
			declaration.addDeclarator((IASTDeclarator)declarator);

		setOffsetAndLength(declaration);
		astStack.push(declaration);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * direct_declarator ::= '(' declarator ')'
	 */
	public void consumeDirectDeclaratorBracketed() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTDeclarator nested = (IASTDeclarator) astStack.pop();
		IASTDeclarator declarator = nodeFactory.newDeclarator(nodeFactory.newName());
		declarator.setNestedDeclarator(nested);
		setOffsetAndLength(declarator);
		astStack.push(declarator);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * direct_declarator ::= declarator_id_name
	 */
	public void consumeDirectDeclaratorIdentifier() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTName name = (IASTName) astStack.pop();
		IASTDeclarator declarator = nodeFactory.newDeclarator(name);
		setOffsetAndLength(declarator);
		astStack.push(declarator);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 *  array_modifier 
	 *      ::= '[' ']' 
     *        | '[' assignment_expression ']'
     */        
	public void consumeDirectDeclaratorArrayModifier(boolean hasAssignmentExpr) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();

		IASTExpression expr = hasAssignmentExpr ? (IASTExpression)astStack.pop() : null;
		IASTArrayModifier arrayModifier = nodeFactory.newArrayModifier(expr);
		setOffsetAndLength(arrayModifier);
		astStack.push(arrayModifier);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * When the identifier part of a declarator is parsed it will put a plain IASTDeclarator on the stack.
	 * When the array modifier part is parsed we will need to throw away the plain
	 * declarator and replace it with an array declarator. If its a multidimensional array then
	 * the additional array modifiers will need to be added to the array declarator.
	 * Special care is taken for nested declarators.
	 */
	protected void consumeDeclaratorArray(IASTArrayModifier arrayModifier) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTDeclarator node = (IASTDeclarator) astStack.pop();
		
		// Its a nested declarator so create an new ArrayDeclarator
		if(node.getNestedDeclarator() != null) {  //node.getPropertyInParent() == IASTDeclarator.NESTED_DECLARATOR) {
			IASTArrayDeclarator declarator = nodeFactory.newArrayDeclarator(nodeFactory.newName());
			IASTDeclarator nested = node;
			declarator.setNestedDeclarator(nested);
			
			int offset = offset(nested);
			int length = endOffset(arrayModifier) - offset;
			setOffsetAndLength(declarator, offset, length);
			
			declarator.addArrayModifier(arrayModifier);
			astStack.push(declarator);
		}
		// There is already an array declarator so just add the modifier to it
		else if(node instanceof IASTArrayDeclarator) {
			IASTArrayDeclarator decl = (IASTArrayDeclarator) node;
			((ASTNode)decl).setLength(endOffset(arrayModifier) - offset(decl));
			
			decl.addArrayModifier(arrayModifier);
			astStack.push(decl);
		}
		// The declarator is an identifier so create a new array declarator
		else  {
			IASTName name = node.getName();
			IASTArrayDeclarator decl = nodeFactory.newArrayDeclarator(name);
			
			int offset = offset(name);
			int length = endOffset(arrayModifier) - offset;
			setOffsetAndLength(decl, offset, length);
			
			decl.addArrayModifier(arrayModifier);
			astStack.push(decl);
		}
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * Pops a simple declarator from the stack, converts it into 
	 * a FunctionDeclator, then pushes it.
	 * TODO: is this the best way of doing this?
	 */
	protected void consumeDirectDeclaratorFunctionDeclarator(IASTFunctionDeclarator declarator, int endOffset) {
		IASTDeclarator decl = (IASTDeclarator) astStack.pop();
		 
		if(decl.getNestedDeclarator() != null) { 
			decl = decl.getNestedDeclarator(); // need to remove one level of nesting for function pointers
			declarator.setNestedDeclarator(decl);
			declarator.setName(nodeFactory.newName());
			int offset = offset(decl);
			setOffsetAndLength(declarator, offset, endOffset - offset);
			astStack.push(declarator);
		}
		else  {
			IASTName name = decl.getName();
			if(name == null) {
				name = nodeFactory.newName();
			}
			declarator.setName(name);
			
			IASTPointerOperator[] pointers = decl.getPointerOperators();
			for(int i = 0; i < pointers.length; i++) {
				declarator.addPointerOperator(pointers[i]);
			}
			
			int offset = offset(name); // TODO
			setOffsetAndLength(declarator, offset, endOffset - offset);
			astStack.push(declarator);
		}

		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	/**
	 * direct_declarator ::= direct_declarator array_modifier
	 * consume the direct_declarator part and add the array modifier
	 */
	public void consumeDirectDeclaratorArrayDeclarator() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTArrayModifier arrayModifier = (IASTArrayModifier) astStack.pop();
		consumeDeclaratorArray(arrayModifier);
	}
	
	
	/**
	 * enum_specifier ::= 'enum' '{' <openscope> enumerator_list_opt '}'
     *                  | 'enum' enum_identifier '{' <openscope> enumerator_list_opt '}'
	 */
	public void consumeTypeSpecifierEnumeration(boolean hasIdent) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTName name = (hasIdent) ? createName(parser.getRuleTokens().get(1)) : nodeFactory.newName();
		
		IASTEnumerationSpecifier enumSpec = nodeFactory.newEnumerationSpecifier(name);

		for(Object o : astStack.closeScope())
			enumSpec.addEnumerator((IASTEnumerator)o);

		setOffsetAndLength(enumSpec);
		astStack.push(enumSpec);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * enumerator ::= enum_identifier
     *              | enum_identifier '=' constant_expression
	 */
	public void consumeEnumerator(boolean hasInitializer) {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTName name = createName(parser.getLeftIToken());
		
		IASTExpression value = null;
		if(hasInitializer)
			value = (IASTExpression) astStack.pop();
		
		IASTEnumerator enumerator = nodeFactory.newEnumerator(name, value);
		setOffsetAndLength(enumerator);
		astStack.push(enumerator);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	/**
	 * initializer ::= assignment_expression
	 */
	public void consumeInitializer() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTExpression assignmentExpr = (IASTExpression) astStack.pop();
		IASTInitializerExpression expr = nodeFactory.newInitializerExpression(assignmentExpr);
        setOffsetAndLength(expr);
        astStack.push(expr);
        
        if(TRACE_AST_STACK) System.out.println(astStack);
	}

	
	/**
	 * initializer ::= '{' <openscope> initializer_list '}'
     *               | '{' <openscope> initializer_list ',' '}'
	 */
	public void consumeInitializerList() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		IASTInitializerList list = nodeFactory.newInitializerList();

		for(Object o : astStack.closeScope())
			list.addInitializer((IASTInitializer)o);
		
		setOffsetAndLength(list);
		astStack.push(list);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}
	
	
	
	
	/**
	 * statement ::= ERROR_TOKEN
	 */
	public void consumeStatementProblem() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		consumeProblem(nodeFactory.newProblemStatement());
	}

	/**
	 * assignment_expression ::= ERROR_TOKEN
	 * constant_expression ::= ERROR_TOKEN
	 */
	public void consumeExpressionProblem() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		consumeProblem(nodeFactory.newProblemExpression());
	}

	/**
	 * external_declaration ::= ERROR_TOKEN
	 */
	public void consumeDeclarationProblem() {
		if(TRACE_ACTIONS) DebugUtil.printMethodTrace();
		
		consumeProblem(nodeFactory.newProblemDeclaration());
	}

	
	private void consumeProblem(IASTProblemHolder problemHolder) {
		IASTProblem problem = nodeFactory.newProblem(IASTProblem.SYNTAX_ERROR, new char[0], false, true);
		problemHolder.setProblem(problem);		
		setOffsetAndLength(problem);
		setOffsetAndLength((ASTNode)problemHolder);
		astStack.push(problemHolder);
		
		if(TRACE_AST_STACK) System.out.println(astStack);
	}

}
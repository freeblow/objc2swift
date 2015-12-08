/**
 * This file is part of objc2swift.
 * https://github.com/yahoojapan/objc2swift
 *
 * Copyright (c) 2015 Yahoo Japan Corporation
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package org.objc2swift.converter

import org.antlr.v4.runtime.ParserRuleContext
import org.objc2swift.converter.ObjCParser._
import org.objc2swift.util.antlr._

import scala.collection.JavaConversions._

/**
 * Implements visit methods for method-contexts.
 */
protected trait MethodVisitor {
  this: ObjC2SwiftConverter =>

  /**
   * Returns translated text of instance method declaration context.
   *
   * @param ctx the parse tree
   * @return Strings of Swift's instance method code
   */
  override def visitInstanceMethodDeclaration(ctx: InstanceMethodDeclarationContext): String =
    Option(ctx.methodDeclaration()).map { c =>
      s"${indent(ctx)}${optional(ctx)}${visit(c)}".stripSuffix(" ")
    }.getOrElse("")

  /**
   * Returns translated text of class method declaration context.
   *
   * @param ctx the parse tree
   **/
  override def visitClassMethodDeclaration(ctx: ClassMethodDeclarationContext): String =
    Option(ctx.methodDeclaration()).map { c =>
      s"${indent(ctx)}${optional(ctx)}class ${visit(c)}".stripSuffix(" ")
    }.getOrElse("")

  /**
   * Returns translated text of method definition context.
   *
   * @param ctx the parse tree
   * @return Strings of Swift code
   */
  override def visitInstanceMethodDefinition(ctx: InstanceMethodDefinitionContext): String =
    ctx.methodDefinition() match {
      case c if !isVisited(c) => s"${indent(ctx)}${visit(c)}".stripSuffix(" ")
      case _ => "" // Already printed
    }

  /**
   * Returns translated text of class method definition context.
   *
   * @param ctx the parse tree
   **/
  override def visitClassMethodDefinition(ctx: ClassMethodDefinitionContext): String =
    ctx.methodDefinition() match {
      case c if !isVisited(c) => s"${indent(ctx)}class ${visit(c)}".stripSuffix(" ")
      case _ => "" // Already printed
    }

  /**
   * Returns translated text of method declaration context.
   *
   * @param ctx the parse tree
   * @return Strings of Swift's method code
   */
  override def visitMethodDeclaration(ctx: MethodDeclarationContext): String =
    findCorrespondingMethodDefinition(ctx) match {
      case Some(impl: MethodDefinitionContext) => visit(impl)
      case _ =>
        // Has no definition
        val slct = ctx.methodSelector()
        val tp = Option(ctx.methodType())
        val hd = createMethodHeader(slct, tp)

        // Check ancestor is protocol or not
        ctx.parent.parent.parent match {
          case _: ProtocolDeclarationContext => hd
          case _ => s"$hd {\n${indent(ctx)}}"
        }
    }

  /**
   * Returns translated text of method definition context.
   *
   * @param ctx the parse tree
   **/
  override def visitMethodDefinition(ctx: MethodDefinitionContext): String = {
    val slct = ctx.methodSelector()
    val tp = Option(ctx.methodType())
    val hd = createMethodHeader(slct, tp)

    s"""|$hd {
        |${visit(ctx.compoundStatement())}${indent(ctx)}}""".stripMargin
  }

  /**
   * Returns translated text of method selector context.
   * @param ctx the parse tree
   **/
  override def visitMethodSelector(ctx: MethodSelectorContext): String =
    Option(ctx.selector()) match {
      case Some(s) => s"${visit(s)}()" // No parameters
      case None =>
        // Method name(selector)
        val selector = Option(ctx.keywordDeclarator(0).selector()).map(visit).getOrElse("")

        // First parameter
        val head = visitKeywordDeclarator(ctx.keywordDeclarator(0), isHead = true)

        // Other parameters
        val tail =
          ctx.keywordDeclarator().tail
            .map(c => ", " + visitKeywordDeclarator(c))
            .mkString

        s"$selector($head$tail)"
    }

  /**
   * Returns translated text of keyword declarator
   *
   * @param ctx the parse tree
   **/
  override def visitKeywordDeclarator(ctx: KeywordDeclaratorContext): String =
    visitKeywordDeclarator(ctx, isHead = false)

  /**
   * Returns translated text of keyword declarator
   *
   * @param ctx the parse tree
   * @param isHead node index in branches
   * @return parameter code
   */
  private def visitKeywordDeclarator(ctx: KeywordDeclaratorContext, isHead: Boolean): String = {
    // Parameter's Internal name
    val paramName = ctx.IDENTIFIER().getText

    // Method name(idx = 0) or Parameter's External name
    val selector = Option(ctx.selector()).map(visit).getOrElse("")

    // Parameter's Type
    val it = ctx.methodType().toIterator
    val paramType = it.map(visit).find(_.nonEmpty).getOrElse("")

    selector match {
      case ""           => s"$paramName: $paramType" // No external name
      case _ if isHead  => s"$paramName: $paramType" // head param has no external name
      case `paramName`  => s"$paramName: $paramType" // external name equals internal one
      case _            => s"$selector $paramName: $paramType"
    }
  }

  /**
   * Return method/parameter type on Swift rule.
   *
   * @param ctx the parse tree
   * @return Swift method type
   */
  override def visitMethodType(ctx: MethodTypeContext): String = {
    val retType = {
      for {
        x <- Option(ctx.typeName().specifierQualifierList())
        y <- Option(x.typeSpecifier())
      } yield y
    }.map(concatType(_)).getOrElse("AnyObject")

    if (retType == "void") "" else retType
  }

  /**
   * Returns method header text.
   * @param sctx methodSelectorContext tree
   * @param tctx methodTypeContext tree (Optional)
   * @return Translated text of method header contexts.
   */
  private def createMethodHeader(sctx: MethodSelectorContext, tctx: Option[MethodTypeContext]): String =
    tctx.map(visit).map {
      case "IBAction" => s"@IBAction func ${visit(sctx)}" // IBAction
      case ""         => s"func ${visit(sctx)}" // void
      case s          => s"func ${visit(sctx)} -> $s"
    }.getOrElse(s"func ${visit(sctx)} -> AnyObject") // Default

  def findCorrespondingMethodDefinition(declCtx: MethodDeclarationContext): Option[MethodDefinitionContext] = {
    val selector = declCtx.methodSelector.getText

    {
      declCtx.parent.parent.parent match {
        case classCtx: ClassInterfaceContext =>
          classCtx.correspondingClassImplementation(root)
            .map(_.implementationDefinitionList)
        case catCtx: CategoryInterfaceContext =>
          catCtx.correspondingCategoryImplementation(root)
            .map(_.implementationDefinitionList)
        case _ => None
      }
    }.flatMap { implDefList =>
      declCtx.parent match {
        case _: InstanceMethodDeclarationContext =>
          implDefList.instanceMethodDefinition.map(_.methodDefinition())
            .find(_.methodSelector.getText == selector)
        case _: ClassMethodDeclarationContext =>
          implDefList.classMethodDefinition.map(_.methodDefinition())
            .find(_.methodSelector.getText == selector)
        case _ => None
      }
    }
  }

  private def optional(ctx: ParserRuleContext): String = {
    // TODO: check if the method is declared in a protocol, and marked as @optional
    ""
  }
}
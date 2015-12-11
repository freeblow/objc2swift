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

import org.antlr.v4.runtime._
import org.objc2swift.converter.ObjCParser._
import scala.collection.JavaConversions._

/**
 * Implements visit methods for expression contexts.
 */
trait BlockVisitor {
  this: ObjC2SwiftBaseConverter =>

  import org.objc2swift.converter.util._

  override def visitBlockExpression(ctx: BlockExpressionContext): String = {
    val blockType = (ctx.blockParameters(), ctx.typeSpecifier()) match {
      case (Some(b), Some(t)) =>
        s"${visit(b)} -> ${visit(t)} in"
      case (Some(b), None) =>
        s"${visit(b)} in"
      case (None, Some(t)) =>
        s"() -> ${visit(t)} in"
      case _ => ""
    }

    s"""|{$blockType
        |${indent(visit(ctx.compoundStatement()))}
        |}""".stripMargin
  }

  override def visitBlockParameters(ctx: BlockParametersContext): String =
    s"(${
      visitList(ctx.typeVariableDeclarator(), ", ")
    })"

  override def visitBlockType(ctx: BlockTypeContext): String = "" // TODO
}

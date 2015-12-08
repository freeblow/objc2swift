package org.objc2swift.converter

import org.objc2swift.converter.ObjCParser.{ConstantContext, IdentifierContext, SelectorContext}

/**
 * Created by takesano on 15/11/13.
 */
protected trait TerminalNodeVisitor {
  this: ObjC2SwiftConverter =>

  override def visitIdentifier(ctx: IdentifierContext): String = ctx.getText

  override def visitConstant(ctx: ConstantContext): String = ctx.getText

  override def visitSelector(ctx: SelectorContext): String = ctx.getText
}
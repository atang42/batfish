package org.batfish.minesweeper.communities;

import java.util.Set;
import java.util.stream.Collectors;

import org.batfish.datamodel.routing_policy.communities.*;
import org.batfish.datamodel.routing_policy.expr.IntComparison;
import org.batfish.datamodel.routing_policy.expr.IntMatchAll;
import org.batfish.datamodel.routing_policy.expr.IntMatchExprVisitor;
import org.batfish.minesweeper.CommunityVar;

public class CommunityVarExprCollector {

  public static Set<CommunityVar> collectMatchExpr(CommunityMatchExpr expr, CommunityContext ctx) {
    return expr.accept(new CommunityVarMatchExprVisitor(), ctx);
  }

  public static Set<CommunityVar> collectSetExpr(CommunitySetExpr expr, CommunityContext ctx) {
    return expr.accept(new CommunityVarSetExprVisitor(), ctx);
  }

  public static Set<CommunityVar> collectSetMatchExpr(
      CommunitySetMatchExpr expr, CommunityContext ctx) {
    return expr.accept(new CommunityVarSetMatchExprVisitor(), ctx);
  }

}

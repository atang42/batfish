package org.batfish.minesweeper.communities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.batfish.datamodel.routing_policy.communities.CommunityContext;
import org.batfish.datamodel.routing_policy.communities.CommunityExprVisitor;
import org.batfish.datamodel.routing_policy.communities.RouteTargetExtendedCommunityExpr;
import org.batfish.datamodel.routing_policy.communities.StandardCommunityHighLowExprs;
import org.batfish.datamodel.routing_policy.expr.IntExprVisitor;
import org.batfish.datamodel.routing_policy.expr.LiteralInt;
import org.batfish.datamodel.routing_policy.expr.VarInt;
import org.batfish.minesweeper.CommunityVar;

public class CommunityVarExprVisitor
    implements CommunityExprVisitor<Set<CommunityVar>, CommunityContext> {

  /*
  Takes a IntExpr and express as regular expression
   */
  public class CommunityVarIntExprVisitor implements IntExprVisitor<String, CommunityContext> {

    @Override public String visitLiteralInt(LiteralInt literalInt, CommunityContext arg) {
      return Integer.toString(literalInt.getValue());
    }

    @Override public String visitVarInt(VarInt varInt, CommunityContext arg) {
      return varInt.getVar();
    }
  }

  @Override public Set<CommunityVar> visitRouteTargetExtendedCommunityExpr(
      RouteTargetExtendedCommunityExpr extendedCommunityTypeGlobalHighLowLocal,
      CommunityContext arg) {
    System.err.println(
        "Unsupported operation in CommunityVarExprVisitor: RouteTargetExtendedCommunityExpr");
    return new HashSet<>();
  }

  @Override public Set<CommunityVar> visitStandardCommunityHighLowExprs(
      StandardCommunityHighLowExprs standardCommunityHighLowExprs, CommunityContext arg) {
    String high = standardCommunityHighLowExprs.getHighExpr().accept(new CommunityVarIntExprVisitor(), arg);
    String low  = standardCommunityHighLowExprs.getLowExpr().accept(new CommunityVarIntExprVisitor(), arg);
    return Collections.singleton(CommunityVar.from(high + ":" + low));
  }
}

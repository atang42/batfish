package org.batfish.minesweeper.communities;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.batfish.datamodel.routing_policy.communities.CommunityContext;
import org.batfish.datamodel.routing_policy.communities.CommunityExpr;
import org.batfish.datamodel.routing_policy.communities.CommunityExprsSet;
import org.batfish.datamodel.routing_policy.communities.CommunitySetDifference;
import org.batfish.datamodel.routing_policy.communities.CommunitySetExprReference;
import org.batfish.datamodel.routing_policy.communities.CommunitySetExprVisitor;
import org.batfish.datamodel.routing_policy.communities.CommunitySetReference;
import org.batfish.datamodel.routing_policy.communities.CommunitySetUnion;
import org.batfish.datamodel.routing_policy.communities.InputCommunities;
import org.batfish.datamodel.routing_policy.communities.LiteralCommunitySet;
import org.batfish.minesweeper.CommunityVar;

final class CommunityVarSetExprVisitor
    implements CommunitySetExprVisitor<Set<CommunityVar>, CommunityContext> {

  @Override
  public Set<CommunityVar> visitCommunityExprsSet(
      CommunityExprsSet communityExprsSet, CommunityContext arg) {
    Set<CommunityVar> result = new HashSet<>();
    Set<CommunityExpr> exprs = communityExprsSet.getExprs();
    CommunityVarExprVisitor visitor = new CommunityVarExprVisitor();
    for (CommunityExpr expr: exprs) {
      result.addAll(expr.accept(visitor, arg));
    }
    return result;
  }

  @Override
  public Set<CommunityVar> visitCommunitySetDifference(
      CommunitySetDifference communitySetDifference, CommunityContext arg) {
    Set<CommunityVar> ret = new HashSet<>();
    ret.addAll(communitySetDifference.getInitial().accept(this, arg));
    ret.addAll(
        communitySetDifference.getRemovalCriterion().accept(new CommunityVarMatchExprVisitor(), arg));
    return ret;
  }

  @Override
  public Set<CommunityVar> visitCommunitySetExprReference(
      CommunitySetExprReference communitySetExprReference, CommunityContext arg) {
    return arg.getCommunitySetExprs().get(communitySetExprReference.getName()).accept(this, arg);
  }

  @Override
  public Set<CommunityVar> visitCommunitySetReference(
      CommunitySetReference communitySetReference, CommunityContext arg) {
    return arg.getCommunitySets().get(communitySetReference.getName()).getCommunities().stream()
        .map(CommunityVar::from)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<CommunityVar> visitCommunitySetUnion(
      CommunitySetUnion communitySetUnion, CommunityContext arg) {
    return communitySetUnion.getExprs().stream()
        .map(expr -> expr.accept(this, arg))
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<CommunityVar> visitInputCommunities(
      InputCommunities inputCommunities, CommunityContext arg) {
    return arg.getInputCommunitySet().getCommunities().stream()
        .map(CommunityVar::from)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<CommunityVar> visitLiteralCommunitySet(
      LiteralCommunitySet literalCommunitySet, CommunityContext arg) {
    return literalCommunitySet.getCommunitySet().getCommunities().stream()
        .map(CommunityVar::from)
        .collect(Collectors.toSet());
  }
}

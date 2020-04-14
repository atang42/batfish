package org.batfish.minesweeper.communities;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.batfish.datamodel.routing_policy.communities.CommunityContext;
import org.batfish.datamodel.routing_policy.communities.CommunitySetAcl;
import org.batfish.datamodel.routing_policy.communities.CommunitySetAclLine;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchAll;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchAny;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchExprReference;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchExprVisitor;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchRegex;
import org.batfish.datamodel.routing_policy.communities.CommunitySetNot;
import org.batfish.datamodel.routing_policy.communities.HasCommunity;
import org.batfish.minesweeper.CommunityVar;

final class CommunityVarSetMatchExprVisitor
    implements CommunitySetMatchExprVisitor<Set<CommunityVar>, CommunityContext> {

  @Override
  public Set<CommunityVar> visitCommunitySetAcl(
      CommunitySetAcl communitySetAcl, CommunityContext arg) {
    for (CommunitySetAclLine line :
            communitySetAcl.getLines()) {
      line.getCommunitySetMatchExpr().accept(this, arg);
    }
    return new HashSet<CommunityVar>();
  }

  @Override
  public Set<CommunityVar> visitCommunitySetMatchAll(
      CommunitySetMatchAll communitySetMatchAll, CommunityContext arg) {
    return communitySetMatchAll.getExprs().stream()
        .map(expr -> expr.accept(this, arg))
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<CommunityVar> visitCommunitySetMatchAny(
      CommunitySetMatchAny communitySetMatchAny, CommunityContext arg) {
    return communitySetMatchAny.getExprs().stream()
        .map(expr -> expr.accept(this, arg))
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<CommunityVar> visitCommunitySetMatchExprReference(
      CommunitySetMatchExprReference communitySetMatchExprReference, CommunityContext arg) {
    return arg.getCommunitySetMatchExprs()
        .get(communitySetMatchExprReference.getName())
        .accept(this, arg);
  }

  @Override
  public Set<CommunityVar> visitCommunitySetMatchRegex(
      CommunitySetMatchRegex communitySetMatchRegex, CommunityContext arg) {
    Set<CommunityVar> ret = new HashSet<>();
    ret.add(CommunityVar.from(communitySetMatchRegex.getRegex()));
    return ret;
  }

  @Override
  public Set<CommunityVar> visitCommunitySetNot(
      CommunitySetNot communitySetNot, CommunityContext arg) {
    return communitySetNot.getExpr().accept(this, arg);
  }

  @Override
  public Set<CommunityVar> visitHasCommunity(HasCommunity hasCommunity, CommunityContext arg) {
    return hasCommunity.getExpr().accept(new CommunityVarMatchExprVisitor(), arg);
  }
}

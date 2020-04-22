package org.batfish.minesweeper.communities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.batfish.datamodel.routing_policy.communities.AllExtendedCommunities;
import org.batfish.datamodel.routing_policy.communities.AllLargeCommunities;
import org.batfish.datamodel.routing_policy.communities.AllStandardCommunities;
import org.batfish.datamodel.routing_policy.communities.CommunityAcl;
import org.batfish.datamodel.routing_policy.communities.CommunityContext;
import org.batfish.datamodel.routing_policy.communities.CommunityIn;
import org.batfish.datamodel.routing_policy.communities.CommunityIs;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchAll;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchAny;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchExprReference;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchExprVisitor;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchRegex;
import org.batfish.datamodel.routing_policy.communities.CommunityNot;
import org.batfish.datamodel.routing_policy.communities.ExtendedCommunityGlobalAdministratorHighMatch;
import org.batfish.datamodel.routing_policy.communities.ExtendedCommunityGlobalAdministratorLowMatch;
import org.batfish.datamodel.routing_policy.communities.ExtendedCommunityGlobalAdministratorMatch;
import org.batfish.datamodel.routing_policy.communities.ExtendedCommunityLocalAdministratorMatch;
import org.batfish.datamodel.routing_policy.communities.RouteTargetExtendedCommunities;
import org.batfish.datamodel.routing_policy.communities.SiteOfOriginExtendedCommunities;
import org.batfish.datamodel.routing_policy.communities.StandardCommunityHighMatch;
import org.batfish.datamodel.routing_policy.communities.StandardCommunityLowMatch;
import org.batfish.datamodel.routing_policy.communities.VpnDistinguisherExtendedCommunities;
import org.batfish.datamodel.routing_policy.expr.IntComparator;
import org.batfish.datamodel.routing_policy.expr.IntComparison;
import org.batfish.datamodel.routing_policy.expr.IntExpr;
import org.batfish.datamodel.routing_policy.expr.IntMatchAll;
import org.batfish.datamodel.routing_policy.expr.IntMatchExpr;
import org.batfish.datamodel.routing_policy.expr.LiteralInt;
import org.batfish.minesweeper.CommunityVar;

final class CommunityVarMatchExprVisitor
    implements CommunityMatchExprVisitor<Set<CommunityVar>, CommunityContext> {

  @Override public Set<CommunityVar> visitAllExtendedCommunities(
      AllExtendedCommunities allExtendedCommunities, CommunityContext arg) {
    System.err.println(
        "Unsupported operation in CommunityVarMatchExprVisitor: AllExtendedCommunities");
    return new HashSet<CommunityVar>();
  }

  @Override public Set<CommunityVar> visitAllLargeCommunities(
      AllLargeCommunities allLargeCommunities, CommunityContext arg) {
    System.err.println("Unsupported operation in CommunityVarMatchExprVisitor: AllLargeCommunities");
    return new HashSet<CommunityVar>();
  }

  @Override public Set<CommunityVar> visitAllStandardCommunities(
      AllStandardCommunities allStandardCommunities, CommunityContext arg) {
    System.err.println(
        "Unsupported operation in CommunityVarMatchExprVisitor: AllStandardCommunities");
    return new HashSet<CommunityVar>();
  }

  @Override public Set<CommunityVar> visitCommunityAcl(CommunityAcl communityAcl,
      CommunityContext arg) {
    System.err.println("Unsupported operation in CommunityVarMatchExprVisitor: CommunityAcl");
    return new HashSet<CommunityVar>();
  }

  @Override public Set<CommunityVar> visitCommunityIn(CommunityIn communityIn,
      CommunityContext arg) {
    return communityIn.getCommunitySetExpr().accept(new CommunityVarSetExprVisitor(), arg);
  }

  @Override public Set<CommunityVar> visitCommunityIs(CommunityIs communityIs,
      CommunityContext arg) {
    Set<CommunityVar> ret = new HashSet<>();
    ret.add(CommunityVar.from(communityIs.getCommunity()));
    return ret;
  }

  @Override public Set<CommunityVar> visitCommunityMatchAll(CommunityMatchAll communityMatchAll,
      CommunityContext arg) {
    Set<CommunityVar> vars =
        communityMatchAll.getExprs()
        .stream()
        .map(expr -> expr.accept(this, arg))
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
    vars.add(CommunityVar.from(vars));
    return vars;
  }

  @Override public Set<CommunityVar> visitCommunityMatchAny(CommunityMatchAny communityMatchAny,
      CommunityContext arg) {
    return communityMatchAny.getExprs()
        .stream()
        .map(expr -> expr.accept(this, arg))
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
  }

  @Override public Set<CommunityVar> visitCommunityMatchExprReference(
      CommunityMatchExprReference communityMatchExprReference, CommunityContext arg) {
    return arg.getCommunityMatchExprs()
        .get(communityMatchExprReference.getName())
        .accept(this, arg);
  }

  @Override public Set<CommunityVar> visitCommunityMatchRegex(
      CommunityMatchRegex communityMatchRegex, CommunityContext arg) {
    Set<CommunityVar> ret = new HashSet<>();
    ret.add(CommunityVar.from(communityMatchRegex.getRegex()));
    return ret;
  }

  @Override public Set<CommunityVar> visitCommunityNot(CommunityNot communityNot,
      CommunityContext arg) {
    return communityNot.getExpr().accept(this, arg);
  }

  @Override public Set<CommunityVar> visitExtendedCommunityGlobalAdministratorHighMatch(
      ExtendedCommunityGlobalAdministratorHighMatch extendedCommunityGlobalAdministratorHighMatch,
      CommunityContext arg) {
    return null;
  }

  @Override public Set<CommunityVar> visitExtendedCommunityGlobalAdministratorLowMatch(
      ExtendedCommunityGlobalAdministratorLowMatch extendedCommunityGlobalAdministratorLowMatch,
      CommunityContext arg) {
    System.err.println(
        "Unsupported operation in CommunityVarMatchExprVisitor");
    return new HashSet<CommunityVar>();
  }

  @Override public Set<CommunityVar> visitExtendedCommunityGlobalAdministratorMatch(
      ExtendedCommunityGlobalAdministratorMatch extendedCommunityGlobalAdministratorMatch,
      CommunityContext arg) {
    System.err.println(
        "Unsupported operation in CommunityVarMatchExprVisitor");
    return new HashSet<CommunityVar>();
  }

  @Override public Set<CommunityVar> visitExtendedCommunityLocalAdministratorMatch(
      ExtendedCommunityLocalAdministratorMatch extendedCommunityLocalAdministratorMatch,
      CommunityContext arg) {
    System.err.println(
        "Unsupported operation in CommunityVarMatchExprVisitor");
    return new HashSet<CommunityVar>();
  }

  @Override public Set<CommunityVar> visitRouteTargetExtendedCommunities(
      RouteTargetExtendedCommunities routeTargetExtendedCommunities, CommunityContext arg) {
    System.err.println(
        "Unsupported operation in CommunityVarMatchExprVisitor");
    return new HashSet<CommunityVar>();
  }

  @Override public Set<CommunityVar> visitSiteOfOriginExtendedCommunities(
      SiteOfOriginExtendedCommunities siteOfOriginExtendedCommunities, CommunityContext arg) {
    System.err.println(
        "Unsupported operation in CommunityVarMatchExprVisitor: SiteOfOriginExtendedCommunities");
    return new HashSet<CommunityVar>();
  }

  @Nullable public String intComparisonToRegex(IntComparison expr) {
    IntComparator comparator = expr.getComparator();
    IntExpr intExpr = expr.getExpr();
    int result;
    if (intExpr instanceof LiteralInt) {
      result = ((LiteralInt) intExpr).getValue();
    } else {
      System.err.println(
          "Unsupported operation in CommunityVarMatchExpr: IntComparator: " + intExpr.getClass());
      return null;
    }
    switch (comparator) {
    case EQ:
      return Integer.toString(result);
    case GE:
    case GT:
    case LE:
    case LT:
    default:
      System.err.println(
          "Unsupported operation in CommunityVarMatchExpr: IntComparator: " + comparator.name());
    }
    return null;
  }

  public Set<String> intMatchExprToVars(IntMatchExpr expr) {
    if (expr instanceof IntComparison) {
      String regex = intComparisonToRegex((IntComparison) expr);
      if (regex == null) {
        return new HashSet<>();
      }
      return Collections.singleton(regex);
    } else if (expr instanceof IntMatchAll) {
      HashSet<String> result = new HashSet<>();
      for (IntMatchExpr ex : ((IntMatchAll) expr).getExprs()) {
        result.addAll(intMatchExprToVars(ex));
      }
      return result;
    }
    System.err.println(
        "Unsupported operation in CommunityVarMatchExpr: IntMatchExpr: " + expr.getClass());
    return new HashSet<>();
  }

  @Override public Set<CommunityVar> visitStandardCommunityHighMatch(
      StandardCommunityHighMatch standardCommunityHighMatch, CommunityContext arg) {
    IntMatchExpr expr = standardCommunityHighMatch.getExpr();

    return intMatchExprToVars(expr).stream()
        .map(x -> CommunityVar.from(x + ":.*"))
        .collect(Collectors.toSet());
  }

  @Override public Set<CommunityVar> visitStandardCommunityLowMatch(
      StandardCommunityLowMatch standardCommunityLowMatch, CommunityContext arg) {
    IntMatchExpr expr = standardCommunityLowMatch.getExpr();

    return intMatchExprToVars(expr).stream()
        .map(x -> CommunityVar.from(".*:" + x))
        .collect(Collectors.toSet());
  }

  @Override public Set<CommunityVar> visitVpnDistinguisherExtendedCommunities(
      VpnDistinguisherExtendedCommunities vpnDistinguisherExtendedCommunities,
      CommunityContext arg) {
    System.err.println(
        "Unsupported operation in CommunityVarMatchExprVisitor: VpnDistinguisherExtendedCommunities");
    return new HashSet<CommunityVar>();
  }
}

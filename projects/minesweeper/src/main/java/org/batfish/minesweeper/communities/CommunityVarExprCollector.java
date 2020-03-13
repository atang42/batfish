package org.batfish.minesweeper.communities;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.batfish.datamodel.routing_policy.communities.AllExtendedCommunities;
import org.batfish.datamodel.routing_policy.communities.AllLargeCommunities;
import org.batfish.datamodel.routing_policy.communities.AllStandardCommunities;
import org.batfish.datamodel.routing_policy.communities.CommunityAcl;
import org.batfish.datamodel.routing_policy.communities.CommunityContext;
import org.batfish.datamodel.routing_policy.communities.CommunityExprsSet;
import org.batfish.datamodel.routing_policy.communities.CommunityIn;
import org.batfish.datamodel.routing_policy.communities.CommunityIs;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchAll;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchAny;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchExpr;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchExprReference;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchExprVisitor;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchRegex;
import org.batfish.datamodel.routing_policy.communities.CommunityNot;
import org.batfish.datamodel.routing_policy.communities.CommunitySetAcl;
import org.batfish.datamodel.routing_policy.communities.CommunitySetDifference;
import org.batfish.datamodel.routing_policy.communities.CommunitySetExpr;
import org.batfish.datamodel.routing_policy.communities.CommunitySetExprReference;
import org.batfish.datamodel.routing_policy.communities.CommunitySetExprVisitor;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchAll;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchAny;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchExpr;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchExprReference;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchExprVisitor;
import org.batfish.datamodel.routing_policy.communities.CommunitySetMatchRegex;
import org.batfish.datamodel.routing_policy.communities.CommunitySetNot;
import org.batfish.datamodel.routing_policy.communities.CommunitySetReference;
import org.batfish.datamodel.routing_policy.communities.CommunitySetUnion;
import org.batfish.datamodel.routing_policy.communities.HasCommunity;
import org.batfish.datamodel.routing_policy.communities.InputCommunities;
import org.batfish.datamodel.routing_policy.communities.LiteralCommunitySet;
import org.batfish.datamodel.routing_policy.communities.RouteTargetExtendedCommunities;
import org.batfish.datamodel.routing_policy.communities.SiteOfOriginExtendedCommunities;
import org.batfish.datamodel.routing_policy.communities.StandardCommunityHighMatch;
import org.batfish.datamodel.routing_policy.communities.StandardCommunityLowMatch;
import org.batfish.datamodel.routing_policy.communities.VpnDistinguisherExtendedCommunities;
import org.batfish.minesweeper.CommunityVar;

public class CommunityVarExprCollector {

  public static Set<CommunityVar> collectMatchExpr(CommunityMatchExpr expr, CommunityContext ctx) {
    return expr.accept(new MatchExprCollector(), ctx);
  }

  public static Set<CommunityVar> collectSetExpr(CommunitySetExpr expr, CommunityContext ctx) {
    return expr.accept(new SetExprCollector(), ctx);
  }

  public static Set<CommunityVar> collectSetMatchExpr(
      CommunitySetMatchExpr expr, CommunityContext ctx) {
    return expr.accept(new SetMatchExprCollector(), ctx);
  }

  private static final class MatchExprCollector
      implements CommunityMatchExprVisitor<Set<CommunityVar>, CommunityContext> {

    @Override
    public Set<CommunityVar> visitAllExtendedCommunities(
        AllExtendedCommunities allExtendedCommunities, CommunityContext arg) {
      System.err.println(
          "Unsupported operation in CommunityVarExprCollector: AllExtendedCommunities");
      return new HashSet<CommunityVar>();
    }

    @Override
    public Set<CommunityVar> visitAllLargeCommunities(
        AllLargeCommunities allLargeCommunities, CommunityContext arg) {
      System.err.println("Unsupported operation in CommunityVarExprCollector: AllLargeCommunities");
      return new HashSet<CommunityVar>();
    }

    @Override
    public Set<CommunityVar> visitAllStandardCommunities(
        AllStandardCommunities allStandardCommunities, CommunityContext arg) {
      System.err.println(
          "Unsupported operation in CommunityVarExprCollector: AllStandardCommunities");
      return new HashSet<CommunityVar>();
    }

    @Override
    public Set<CommunityVar> visitCommunityAcl(CommunityAcl communityAcl, CommunityContext arg) {
      System.err.println("Unsupported operation in CommunityVarExprCollector: CommunityAcl");
      return new HashSet<CommunityVar>();
    }

    @Override
    public Set<CommunityVar> visitCommunityIn(CommunityIn communityIn, CommunityContext arg) {
      return communityIn.getCommunitySetExpr().accept(new SetExprCollector(), arg);
    }

    @Override
    public Set<CommunityVar> visitCommunityIs(CommunityIs communityIs, CommunityContext arg) {
      Set<CommunityVar> ret = new HashSet<>();
      ret.add(CommunityVar.from(communityIs.getCommunity()));
      return ret;
    }

    @Override
    public Set<CommunityVar> visitCommunityMatchAll(
        CommunityMatchAll communityMatchAll, CommunityContext arg) {
      return communityMatchAll.getExprs().stream()
          .map(expr -> expr.accept(this, arg))
          .flatMap(Set::stream)
          .collect(Collectors.toSet());
    }

    @Override
    public Set<CommunityVar> visitCommunityMatchAny(
        CommunityMatchAny communityMatchAny, CommunityContext arg) {
      return communityMatchAny.getExprs().stream()
          .map(expr -> expr.accept(this, arg))
          .flatMap(Set::stream)
          .collect(Collectors.toSet());
    }

    @Override
    public Set<CommunityVar> visitCommunityMatchExprReference(
        CommunityMatchExprReference communityMatchExprReference, CommunityContext arg) {
      return arg.getCommunityMatchExprs()
          .get(communityMatchExprReference.getName())
          .accept(this, arg);
    }

    @Override
    public Set<CommunityVar> visitCommunityMatchRegex(
        CommunityMatchRegex communityMatchRegex, CommunityContext arg) {
      Set<CommunityVar> ret = new HashSet<>();
      ret.add(CommunityVar.from(communityMatchRegex.getRegex()));
      return ret;
    }

    @Override
    public Set<CommunityVar> visitCommunityNot(CommunityNot communityNot, CommunityContext arg) {
      return communityNot.getExpr().accept(this, arg);
    }

    @Override
    public Set<CommunityVar> visitRouteTargetExtendedCommunities(
        RouteTargetExtendedCommunities routeTargetExtendedCommunities, CommunityContext arg) {
      System.err.println(
          "Unsupported operation in CommunityVarExprCollector: RouteTargetExtendedCommunities");
      return new HashSet<CommunityVar>();
    }

    @Override
    public Set<CommunityVar> visitSiteOfOriginExtendedCommunities(
        SiteOfOriginExtendedCommunities siteOfOriginExtendedCommunities, CommunityContext arg) {
      System.err.println(
          "Unsupported operation in CommunityVarExprCollector: SiteOfOriginExtendedCommunities");
      return new HashSet<CommunityVar>();
    }

    @Override
    public Set<CommunityVar> visitStandardCommunityHighMatch(
        StandardCommunityHighMatch standardCommunityHighMatch, CommunityContext arg) {
      System.err.println(
          "Unsupported operation in CommunityVarExprCollector: StandardCommunityHighMatch");
      return new HashSet<CommunityVar>();
    }

    @Override
    public Set<CommunityVar> visitStandardCommunityLowMatch(
        StandardCommunityLowMatch standardCommunityLowMatch, CommunityContext arg) {
      System.err.println(
          "Unsupported operation in CommunityVarExprCollector: StandardCommunityLowMatch");
      return new HashSet<CommunityVar>();
    }

    @Override
    public Set<CommunityVar> visitVpnDistinguisherExtendedCommunities(
        VpnDistinguisherExtendedCommunities vpnDistinguisherExtendedCommunities,
        CommunityContext arg) {
      System.err.println(
          "Unsupported operation in CommunityVarExprCollector: VpnDistinguisherExtendedCommunities");
      return new HashSet<CommunityVar>();
    }
  }

  private static final class SetExprCollector
      implements CommunitySetExprVisitor<Set<CommunityVar>, CommunityContext> {

    @Override
    public Set<CommunityVar> visitCommunityExprsSet(
        CommunityExprsSet communityExprsSet, CommunityContext arg) {
      System.err.println("Unsupported operation in CommunityVarExprCollector: CommunityExprsSet");
      return new HashSet<CommunityVar>();
    }

    @Override
    public Set<CommunityVar> visitCommunitySetDifference(
        CommunitySetDifference communitySetDifference, CommunityContext arg) {
      Set<CommunityVar> ret = new HashSet<>();
      ret.addAll(communitySetDifference.getInitial().accept(this, arg));
      ret.addAll(
          communitySetDifference.getRemovalCriterion().accept(new MatchExprCollector(), arg));
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

  private static final class SetMatchExprCollector
      implements CommunitySetMatchExprVisitor<Set<CommunityVar>, CommunityContext> {

    @Override
    public Set<CommunityVar> visitCommunitySetAcl(
        CommunitySetAcl communitySetAcl, CommunityContext arg) {
      System.err.println("Unsupported operation in CommunityVarExprCollector: CommunitySetAcl");
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
      return hasCommunity.getExpr().accept(new MatchExprCollector(), arg);
    }
  }
}

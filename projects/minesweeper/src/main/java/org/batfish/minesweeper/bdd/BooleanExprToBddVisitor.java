package org.batfish.minesweeper.bdd;

import net.sf.javabdd.BDD;
import org.batfish.datamodel.routing_policy.communities.MatchCommunities;
import org.batfish.datamodel.routing_policy.expr.BooleanExprVisitor;
import org.batfish.datamodel.routing_policy.expr.BooleanExprs.StaticBooleanExpr;
import org.batfish.datamodel.routing_policy.expr.CallExpr;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.ConjunctionChain;
import org.batfish.datamodel.routing_policy.expr.Disjunction;
import org.batfish.datamodel.routing_policy.expr.FirstMatchChain;
import org.batfish.datamodel.routing_policy.expr.HasRoute;
import org.batfish.datamodel.routing_policy.expr.HasRoute6;
import org.batfish.datamodel.routing_policy.expr.MatchAsPath;
import org.batfish.datamodel.routing_policy.expr.MatchColor;
import org.batfish.datamodel.routing_policy.expr.MatchCommunitySet;
import org.batfish.datamodel.routing_policy.expr.MatchEntireCommunitySet;
import org.batfish.datamodel.routing_policy.expr.MatchIp6AccessList;
import org.batfish.datamodel.routing_policy.expr.MatchIpv4;
import org.batfish.datamodel.routing_policy.expr.MatchIpv6;
import org.batfish.datamodel.routing_policy.expr.MatchLocalPreference;
import org.batfish.datamodel.routing_policy.expr.MatchLocalRouteSourcePrefixLength;
import org.batfish.datamodel.routing_policy.expr.MatchMetric;
import org.batfish.datamodel.routing_policy.expr.MatchPrefix6Set;
import org.batfish.datamodel.routing_policy.expr.MatchPrefixSet;
import org.batfish.datamodel.routing_policy.expr.MatchProcessAsn;
import org.batfish.datamodel.routing_policy.expr.MatchProtocol;
import org.batfish.datamodel.routing_policy.expr.MatchRouteType;
import org.batfish.datamodel.routing_policy.expr.MatchSourceVrf;
import org.batfish.datamodel.routing_policy.expr.MatchTag;
import org.batfish.datamodel.routing_policy.expr.NeighborIsAsPath;
import org.batfish.datamodel.routing_policy.expr.Not;
import org.batfish.datamodel.routing_policy.expr.OriginatesFromAsPath;
import org.batfish.datamodel.routing_policy.expr.PassesThroughAsPath;
import org.batfish.datamodel.routing_policy.expr.RouteIsClassful;
import org.batfish.datamodel.routing_policy.expr.WithEnvironmentExpr;
import org.batfish.minesweeper.TransferParam;
import org.batfish.minesweeper.TransferResult;

public class BooleanExprToBddVisitor implements BooleanExprVisitor<TransferResult<TransferReturn, BDD>, TransferParam<BDDRoute>> {
  @Override public TransferResult<TransferReturn, BDD> visitBooleanExprs(
      StaticBooleanExpr staticBooleanExpr, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitCallExpr(CallExpr callExpr,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitConjunction(Conjunction conjunction,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitConjunctionChain(
      ConjunctionChain conjunctionChain, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitDisjunction(Disjunction disjunction,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitFirstMatchChain(
      FirstMatchChain firstMatchChain, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitHasRoute(HasRoute hasRoute,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitHasRoute6(HasRoute6 hasRoute6,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchAsPath(MatchAsPath matchAsPath,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchColor(MatchColor matchColor,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchCommunities(
      MatchCommunities matchCommunities, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchCommunitySet(
      MatchCommunitySet matchCommunitySet, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchEntireCommunitySet(
      MatchEntireCommunitySet matchEntireCommunitySet, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchIp6AccessList(
      MatchIp6AccessList matchIp6AccessList, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchIpv4(MatchIpv4 matchIpv4,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchIpv6(MatchIpv6 matchIpv6,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchLocalPreference(
      MatchLocalPreference matchLocalPreference, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchLocalRouteSourcePrefixLength(
      MatchLocalRouteSourcePrefixLength matchLocalRouteSourcePrefixLength,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchMetric(MatchMetric matchMetric,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchPrefix6Set(
      MatchPrefix6Set matchPrefix6Set, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchPrefixSet(
      MatchPrefixSet matchPrefixSet, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchProcessAsn(
      MatchProcessAsn matchProcessAsn, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchProtocol(
      MatchProtocol matchProtocol, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchRouteType(
      MatchRouteType matchRouteType, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchSourceVrf(
      MatchSourceVrf matchSourceVrf, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitMatchTag(MatchTag matchTag,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitNeighborIsAsPath(
      NeighborIsAsPath neighborIsAsPath, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitNot(Not not,
      TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitOriginatesFromAsPath(
      OriginatesFromAsPath originatesFromAsPath, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitPassesThroughAsPath(
      PassesThroughAsPath passesThroughAsPath, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitRouteIsClassful(
      RouteIsClassful routeIsClassful, TransferParam<BDDRoute> arg) {
    return null;
  }

  @Override public TransferResult<TransferReturn, BDD> visitWithEnvironmentExpr(
      WithEnvironmentExpr withEnvironmentExpr, TransferParam<BDDRoute> arg) {
    return null;
  }
}

package org.batfish.minesweeper.bdd;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.Configuration;
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
import org.batfish.minesweeper.CommunityVar;
import org.batfish.minesweeper.TransferParam;

class CommunityMatchExprToBddVisitor
    implements CommunityMatchExprVisitor<BDD, CommunityContext> {

  private final TransferBDD _transferBDD;
  private final TransferParam<BDDRoute> _param;
  private final Configuration _conf;
  private final BDDRoute _record;
  private final BDDFactory _factory;

  public CommunityMatchExprToBddVisitor(
      TransferBDD transferBDD, TransferParam<BDDRoute> p, Configuration conf, BDDRoute other) {
    _transferBDD = transferBDD;
    _param = p;
    _conf = conf;
    _record = other;
    _factory = BDDRoute.getFactory();
  }

  @Override
  public BDD visitAllExtendedCommunities(
      AllExtendedCommunities allExtendedCommunities, CommunityContext arg) {
    System.err.println("Unsupported Operation: AllExtendedCommunities");
    return _factory.one();
  }

  @Override
  public BDD visitAllLargeCommunities(
      AllLargeCommunities allLargeCommunities, CommunityContext arg) {
    System.err.println("Unsupported Operation: AllLargeCommunities");
    return _factory.one();
  }

  @Override
  public BDD visitAllStandardCommunities(
      AllStandardCommunities allStandardCommunities, CommunityContext arg) {
    System.err.println("Unsupported Operation: AllStandardCommunities");
    return _factory.one();
  }

  @Override
  public BDD visitCommunityAcl(CommunityAcl communityAcl, CommunityContext arg) {
    System.err.println("Unsupported Operation: CommunityAcl");
    return _factory.one();
  }

  @Override
  public BDD visitCommunityIn(CommunityIn communityIn, CommunityContext arg) {
    System.err.println("Unsupported Operation: CommunityIn");
    return _factory.one();
  }

  @Override
  public BDD visitCommunityIs(CommunityIs communityIs, CommunityContext arg) {
    CommunityVar comm = CommunityVar.from(communityIs.getCommunity());
    return _transferBDD.matchSingleCommunityVar(_param, comm, true, _record);
  }

  @Override
  public BDD visitCommunityMatchAll(CommunityMatchAll communityMatchAll, CommunityContext arg) {
    return communityMatchAll
        .getExprs()
        .stream()
        .map(expr -> expr.accept(this, arg))
        .reduce(BDD::and)
        .orElse(_factory.zero());
  }

  @Override
  public BDD visitCommunityMatchAny(CommunityMatchAny communityMatchAny, CommunityContext arg) {
    return communityMatchAny
        .getExprs()
        .stream()
        .map(expr -> expr.accept(this, arg))
        .reduce(BDD::or)
        .orElse(_factory.zero());
  }

  @Override
  public BDD visitCommunityMatchExprReference(
      CommunityMatchExprReference communityMatchExprReference, CommunityContext arg) {
    return arg.getCommunityMatchExprs().get(arg).accept(this, arg);
  }

  @Override
  public BDD visitCommunityMatchRegex(
      CommunityMatchRegex communityMatchRegex, CommunityContext arg) {
    CommunityVar comm = CommunityVar.from(communityMatchRegex.getRegex());
    return _transferBDD.matchSingleCommunityVar(_param, comm, true, _record);
  }

  @Override
  public BDD visitCommunityNot(CommunityNot communityNot, CommunityContext arg) {
    return communityNot.getExpr().accept(this, arg).not();
  }

  @Override public BDD visitExtendedCommunityGlobalAdministratorHighMatch(
      ExtendedCommunityGlobalAdministratorHighMatch extendedCommunityGlobalAdministratorHighMatch,
      CommunityContext arg) {
    System.err.println("Unsupported Operation: ExtendedCommunityGlobalAdministratorHighMatch");
    return _factory.one();
  }

  @Override public BDD visitExtendedCommunityGlobalAdministratorLowMatch(
      ExtendedCommunityGlobalAdministratorLowMatch extendedCommunityGlobalAdministratorLowMatch,
      CommunityContext arg) {
    System.err.println("Unsupported Operation: ExtendedCommunityGlobalAdministratorLowMatch");
    return _factory.one();
  }

  @Override public BDD visitExtendedCommunityGlobalAdministratorMatch(
      ExtendedCommunityGlobalAdministratorMatch extendedCommunityGlobalAdministratorMatch,
      CommunityContext arg) {
    System.err.println("Unsupported Operation: ExtendedCommunityGlobalAdministratorMatch");
    return _factory.one();
  }

  @Override public BDD visitExtendedCommunityLocalAdministratorMatch(
      ExtendedCommunityLocalAdministratorMatch extendedCommunityLocalAdministratorMatch,
      CommunityContext arg) {
    System.err.println("Unsupported Operation: ExtendedCommunityLocalAdministratorMatch");
    return _factory.one();
  }

  @Override
  public BDD visitRouteTargetExtendedCommunities(
      RouteTargetExtendedCommunities routeTargetExtendedCommunities, CommunityContext arg) {
    System.err.println(
        "Unsupported Operation: RouteTargetExtendedCommunities");
    return _factory.one();
  }

  @Override
  public BDD visitSiteOfOriginExtendedCommunities(
      SiteOfOriginExtendedCommunities siteOfOriginExtendedCommunities, CommunityContext arg) {
    System.err.println(
        "Unsupported Operation: SiteOfOriginExtendedCommunities");
    return _factory.one();
  }

  @Override
  public BDD visitStandardCommunityHighMatch(
      StandardCommunityHighMatch standardCommunityHighMatch, CommunityContext arg) {
    System.err.println("Unsupported Operation: StandardCommunityHighMatch");
    return _factory.one();
  }

  @Override
  public BDD visitStandardCommunityLowMatch(
      StandardCommunityLowMatch standardCommunityLowMatch, CommunityContext arg) {
    System.err.println("Unsupported Operation: StandardCommunityLowMatch");
    return _factory.one();
  }

  @Override
  public BDD visitVpnDistinguisherExtendedCommunities(
      VpnDistinguisherExtendedCommunities vpnDistinguisherExtendedCommunities,
      CommunityContext arg) {
    System.err.println(
        "Unsupported Operation: VpnDistinguisherExtendedCommunities");
    return _factory.one();
  }
}

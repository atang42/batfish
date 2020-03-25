package org.batfish.minesweeper.bdd;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.routing_policy.communities.*;
import org.batfish.minesweeper.CommunityVar;
import org.batfish.minesweeper.TransferParam;

public class MatchCommunitiesToBDD
    implements CommunitySetMatchExprVisitor<BDD, CommunityContext> {

  private final TransferBDD _transferBDD;
  private final TransferParam<BDDRoute> _param;
  private final Configuration _conf;
  private final BDDRoute _record;
  private final BDDFactory _factory;

  public MatchCommunitiesToBDD(
      TransferBDD transferBDD, TransferParam<BDDRoute> p, Configuration conf, BDDRoute other) {
    _transferBDD = transferBDD;
    _param = p;
    _conf = conf;
    _record = other;
    _factory = BDDRoute.getFactory();
  }

  @Override
  public BDD visitCommunitySetAcl(CommunitySetAcl communitySetAcl, CommunityContext arg) {
    BDD result = _factory.zero();
    BDD soFar = _factory.zero();
    for (CommunitySetAclLine line : communitySetAcl.getLines()) {
      BDD matched = line.getCommunitySetMatchExpr().accept(this, arg).and(soFar.not());
      soFar = soFar.or(matched);
      if (line.getAction() == LineAction.PERMIT) {
        result = result.and(matched);
      }
    }
    return result;
  }

  @Override
  public BDD visitCommunitySetMatchAll(
      CommunitySetMatchAll communitySetMatchAll, CommunityContext arg) {
    return communitySetMatchAll
        .getExprs()
        .stream()
        .map(expr -> expr.accept(this, arg))
        .reduce(BDD::and)
        .orElse(_factory.zero());
  }

  @Override
  public BDD visitCommunitySetMatchAny(
      CommunitySetMatchAny communitySetMatchAny, CommunityContext arg) {
    return communitySetMatchAny
        .getExprs()
        .stream()
        .map(expr -> expr.accept(this, arg))
        .reduce(BDD::or)
        .orElse(_factory.zero());
  }

  @Override
  public BDD visitCommunitySetMatchExprReference(
      CommunitySetMatchExprReference communitySetMatchExprReference, CommunityContext arg) {
    return arg.getCommunitySetMatchExprs()
        .get(communitySetMatchExprReference.getName())
        .accept(this, arg);
  }

  @Override
  public BDD visitCommunitySetMatchRegex(
      CommunitySetMatchRegex communitySetMatchRegex, CommunityContext arg) {
    CommunityVar comm = CommunityVar.from(communitySetMatchRegex.getRegex());
    return _transferBDD.matchSingleCommunityVar(_param, comm, true, _record);
  }

  @Override
  public BDD visitCommunitySetNot(CommunitySetNot communitySetNot, CommunityContext arg) {
    return communitySetNot.getExpr().accept(this, arg).not();
  }

  @Override
  public BDD visitHasCommunity(HasCommunity hasCommunity, CommunityContext arg) {
    return hasCommunity.getExpr()
        .accept(new CommunityMatchExprToBDD(_transferBDD, _param, _conf, _record), arg);
  }

  private static class CommunityMatchExprToBDD
      implements CommunityMatchExprVisitor<BDD, CommunityContext> {

    private final TransferBDD _transferBDD;
    private final TransferParam<BDDRoute> _param;
    private final Configuration _conf;
    private final BDDRoute _record;
    private final BDDFactory _factory;

    public CommunityMatchExprToBDD(
        TransferBDD transferBDD, TransferParam<BDDRoute> p, Configuration conf, BDDRoute other) {
      _transferBDD = transferBDD;
      _param = p;
      _conf = conf;
      _record = other;
      _factory = BDDRoute.getFactory();
    }

    /*
     * Convert EXACT community vars to their REGEX equivalents.
     */
    private static CommunityVar toRegexCommunityVar(CommunityVar cvar) {
      switch (cvar.getType()) {
      case REGEX:
        return cvar;
      case EXACT:
        assert cvar.getLiteralValue() != null; // invariant of the EXACT type
        return CommunityVar.from(String.format("^%s$", cvar.getLiteralValue().matchString()));
      default:
        throw new BatfishException("Unexpected CommunityVar type: " + cvar.getType());
      }
    }

    @Override
    public BDD visitAllExtendedCommunities(
        AllExtendedCommunities allExtendedCommunities, CommunityContext arg) {
      throw new UnsupportedOperationException("Unsupported Operation: AllExtendedCommunities");
    }

    @Override
    public BDD visitAllLargeCommunities(
        AllLargeCommunities allLargeCommunities, CommunityContext arg) {
      throw new UnsupportedOperationException("Unsupported Operation: AllLargeCommunities");
    }

    @Override
    public BDD visitAllStandardCommunities(
        AllStandardCommunities allStandardCommunities, CommunityContext arg) {
      throw new UnsupportedOperationException("Unsupported Operation: AllStandardCommunities");
    }

    @Override
    public BDD visitCommunityAcl(CommunityAcl communityAcl, CommunityContext arg) {
      throw new UnsupportedOperationException("Unsupported Operation: CommunityAcl");
    }

    @Override
    public BDD visitCommunityIn(CommunityIn communityIn, CommunityContext arg) {
      throw new UnsupportedOperationException("Unsupported Operation: CommunityIn");
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

    @Override
    public BDD visitRouteTargetExtendedCommunities(
        RouteTargetExtendedCommunities routeTargetExtendedCommunities, CommunityContext arg) {
      throw new UnsupportedOperationException(
          "Unsupported Operation: RouteTargetExtendedCommunities");
    }

    @Override
    public BDD visitSiteOfOriginExtendedCommunities(
        SiteOfOriginExtendedCommunities siteOfOriginExtendedCommunities, CommunityContext arg) {
      throw new UnsupportedOperationException(
          "Unsupported Operation: SiteOfOriginExtendedCommunities");
    }

    @Override
    public BDD visitStandardCommunityHighMatch(
        StandardCommunityHighMatch standardCommunityHighMatch, CommunityContext arg) {
      throw new UnsupportedOperationException("Unsupported Operation: StandardCommunityHighMatch");
    }

    @Override
    public BDD visitStandardCommunityLowMatch(
        StandardCommunityLowMatch standardCommunityLowMatch, CommunityContext arg) {
      throw new UnsupportedOperationException("Unsupported Operation: StandardCommunityLowMatch");
    }

    @Override
    public BDD visitVpnDistinguisherExtendedCommunities(
        VpnDistinguisherExtendedCommunities vpnDistinguisherExtendedCommunities,
        CommunityContext arg) {
      throw new UnsupportedOperationException(
          "Unsupported Operation: VpnDistinguisherExtendedCommunities");
    }
  }
}

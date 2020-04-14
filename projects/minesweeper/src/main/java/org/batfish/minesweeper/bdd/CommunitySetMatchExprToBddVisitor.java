package org.batfish.minesweeper.bdd;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.routing_policy.communities.*;
import org.batfish.minesweeper.CommunityVar;
import org.batfish.minesweeper.TransferParam;

public class CommunitySetMatchExprToBddVisitor
    implements CommunitySetMatchExprVisitor<BDD, CommunityContext> {

  private final TransferBDD _transferBDD;
  private final TransferParam<BDDRoute> _param;
  private final Configuration _conf;
  private final BDDRoute _record;
  private final BDDFactory _factory;

  public CommunitySetMatchExprToBddVisitor(
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
        .accept(new CommunityMatchExprToBddVisitor(_transferBDD, _param, _conf, _record), arg);
  }

}

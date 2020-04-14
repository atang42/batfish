package org.batfish.minesweeper.bdd;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.datamodel.CommunityList;
import org.batfish.datamodel.CommunityListLine;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.RegexCommunitySet;
import org.batfish.datamodel.bgp.community.Community;
import org.batfish.datamodel.routing_policy.expr.CommunityHalvesExpr;
import org.batfish.datamodel.routing_policy.expr.EmptyCommunitySetExpr;
import org.batfish.datamodel.routing_policy.expr.LiteralCommunity;
import org.batfish.datamodel.routing_policy.expr.LiteralCommunityConjunction;
import org.batfish.datamodel.routing_policy.expr.LiteralCommunitySet;
import org.batfish.datamodel.routing_policy.expr.NamedCommunitySet;
import org.batfish.datamodel.visitors.CommunitySetExprVisitor;
import org.batfish.minesweeper.CommunityVar;

public class CommunitySetExprToBddVisitor implements CommunitySetExprVisitor<BDD> {

  private final Configuration _conf;
  private final BDDRoute _record;
  private final BDDFactory _factory;
  private final SortedMap<CommunityVar, List<CommunityVar>> _deps;

  public CommunitySetExprToBddVisitor(Configuration conf, BDDRoute record,
      SortedMap<CommunityVar, List<CommunityVar>> deps) {
    _conf = conf;
    _record = record;
    _factory = BDDRoute.getFactory();
    _deps = deps;
  }

  @Override public BDD visitCommunityHalvesExpr(CommunityHalvesExpr communityHalvesExpr) {
    System.err.println("Unsupported Operation in CommunitySetExprToBddVisitor: CommunityHalvesExpr");
    return _factory.one();
  }

  @Override public BDD visitCommunityList(CommunityList communityList) {

    List<CommunityListLine> lines = new ArrayList<>(communityList.getLines());
    BDD acc = _factory.zero();
    BDD notMatchedYet = _factory.one();
    for (CommunityListLine line : lines) {
      BDD action = (line.getAction() == LineAction.PERMIT) ? _factory.one() : _factory.zero();
      BDD match = line.getMatchCondition().accept(this);
      acc = acc.or(match.and(action));
      notMatchedYet.andWith(match.not());
    }
    return acc;
  }

  @Override public BDD visitEmptyCommunitySetExpr(EmptyCommunitySetExpr emptyCommunitySetExpr) {
    return _factory.one();
  }

  @Override public BDD visitLiteralCommunity(LiteralCommunity literalCommunity) {
    BDD acc = _factory.zero();
    BDD notMatchedYet = _factory.one();
    CommunityVar cvar = CommunityVar.from(literalCommunity.getCommunity());
    List<CommunityVar> deps = _deps.getOrDefault(cvar, new ArrayList<>());
    deps.add(cvar);
    for (CommunityVar dep : deps) {
      BDD c = _record.getCommunities().get(dep);
      acc = acc.or(c);
      notMatchedYet.andWith(c.not());
    }
    return acc;
  }

  @Override public BDD visitLiteralCommunityConjunction(
      LiteralCommunityConjunction literalCommunityConjunction) {
    BDD acc = _factory.one();
    for (Community comm : literalCommunityConjunction.getRequiredCommunities()) {
      CommunityVar cvar = CommunityVar.from(comm);
      List<CommunityVar> deps = _deps.getOrDefault(cvar, new ArrayList<>());
      deps.add(cvar);
      BDD disjunct = _factory.zero();
      for (CommunityVar dep : deps) {
        BDD c = _record.getCommunities().get(dep);
        disjunct = disjunct.or(c);
      }
      acc.andWith(disjunct);
    }
    return acc;
  }

  @Override public BDD visitLiteralCommunitySet(LiteralCommunitySet literalCommunitySet) {
    BDD acc = _factory.one();
    for (Community comm : literalCommunitySet.getCommunities()) {
      CommunityVar cvar = CommunityVar.from(comm);
      List<CommunityVar> deps = _deps.getOrDefault(cvar, new ArrayList<>());
      deps.add(cvar);
      for (CommunityVar dep : deps) {
        BDD c = _record.getCommunities().get(dep);
        acc = acc.or(c);
      }
    }
    return acc;
  }

  @Override public BDD visitNamedCommunitySet(NamedCommunitySet namedCommunitySet) {
    CommunityList cl = _conf.getCommunityLists().get(namedCommunitySet.getName());
    return cl.accept(this);
  }

  @Override public BDD visitRegexCommunitySet(RegexCommunitySet regexCommunitySet) {
    BDD acc = _factory.zero();
    BDD notMatchedYet = _factory.one();
    CommunityVar cvar = CommunityVar.from(regexCommunitySet.getRegex());
    List<CommunityVar> deps = _deps.getOrDefault(cvar, new ArrayList<>());
    deps.add(cvar);
    for (CommunityVar dep : deps) {
      BDD c = _record.getCommunities().get(dep);
      acc = acc.or(c);
      notMatchedYet.andWith(c.not());
    }
    return acc;
  }
}

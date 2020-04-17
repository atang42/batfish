package org.batfish.minesweeper.communities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.batfish.datamodel.CommunityList;
import org.batfish.datamodel.CommunityListLine;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.RegexCommunitySet;
import org.batfish.datamodel.routing_policy.Environment;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.communities.CommunityContext;
import org.batfish.datamodel.routing_policy.communities.MatchCommunities;
import org.batfish.datamodel.routing_policy.communities.SetCommunities;
import org.batfish.datamodel.routing_policy.expr.BooleanExprVisitor;
import org.batfish.datamodel.routing_policy.expr.BooleanExprs.StaticBooleanExpr;
import org.batfish.datamodel.routing_policy.expr.CallExpr;
import org.batfish.datamodel.routing_policy.expr.CommunityHalvesExpr;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.ConjunctionChain;
import org.batfish.datamodel.routing_policy.expr.Disjunction;
import org.batfish.datamodel.routing_policy.expr.EmptyCommunitySetExpr;
import org.batfish.datamodel.routing_policy.expr.FirstMatchChain;
import org.batfish.datamodel.routing_policy.expr.HasRoute;
import org.batfish.datamodel.routing_policy.expr.HasRoute6;
import org.batfish.datamodel.routing_policy.expr.LiteralCommunity;
import org.batfish.datamodel.routing_policy.expr.LiteralCommunityConjunction;
import org.batfish.datamodel.routing_policy.expr.LiteralCommunitySet;
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
import org.batfish.datamodel.routing_policy.expr.NamedCommunitySet;
import org.batfish.datamodel.routing_policy.expr.NeighborIsAsPath;
import org.batfish.datamodel.routing_policy.expr.Not;
import org.batfish.datamodel.routing_policy.expr.OriginatesFromAsPath;
import org.batfish.datamodel.routing_policy.expr.PassesThroughAsPath;
import org.batfish.datamodel.routing_policy.expr.RouteIsClassful;
import org.batfish.datamodel.routing_policy.expr.WithEnvironmentExpr;
import org.batfish.datamodel.routing_policy.statement.AddCommunity;
import org.batfish.datamodel.routing_policy.statement.BufferedStatement;
import org.batfish.datamodel.routing_policy.statement.CallStatement;
import org.batfish.datamodel.routing_policy.statement.Comment;
import org.batfish.datamodel.routing_policy.statement.DeleteCommunity;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.PrependAsPath;
import org.batfish.datamodel.routing_policy.statement.RetainCommunity;
import org.batfish.datamodel.routing_policy.statement.SetAdministrativeCost;
import org.batfish.datamodel.routing_policy.statement.SetCommunity;
import org.batfish.datamodel.routing_policy.statement.SetDefaultPolicy;
import org.batfish.datamodel.routing_policy.statement.SetEigrpMetric;
import org.batfish.datamodel.routing_policy.statement.SetIsisLevel;
import org.batfish.datamodel.routing_policy.statement.SetIsisMetricType;
import org.batfish.datamodel.routing_policy.statement.SetLocalPreference;
import org.batfish.datamodel.routing_policy.statement.SetMetric;
import org.batfish.datamodel.routing_policy.statement.SetNextHop;
import org.batfish.datamodel.routing_policy.statement.SetOrigin;
import org.batfish.datamodel.routing_policy.statement.SetOspfMetricType;
import org.batfish.datamodel.routing_policy.statement.SetTag;
import org.batfish.datamodel.routing_policy.statement.SetVarMetricType;
import org.batfish.datamodel.routing_policy.statement.SetWeight;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.StatementVisitor;
import org.batfish.datamodel.routing_policy.statement.Statements.StaticStatement;
import org.batfish.datamodel.visitors.CommunitySetExprVisitor;
import org.batfish.minesweeper.CommunityVar;

/*
Extracts all communities from a set of configurations and assigns them to a CommunityVa
 */
public class CommunityVarExtractor {

  @Nonnull private final Map<Configuration, Collection<RoutingPolicy>> _configsToPolicy;

  public CommunityVarExtractor(@Nonnull Collection<Configuration> configs) {
    _configsToPolicy = new HashMap<>();
    for (Configuration c : configs) {
      _configsToPolicy.put(c, c.getRoutingPolicies().values());
    }
  }

  public CommunityVarExtractor(@Nonnull Configuration config, @Nonnull Collection<RoutingPolicy> exports) {
    _configsToPolicy = new HashMap<>();
    _configsToPolicy.put(config, exports);
  }

  private static class BooleanExprExtractor
      implements BooleanExprVisitor<Set<CommunityVar>, CommunityContext> {

    @Nonnull private Configuration _config;

    private BooleanExprExtractor(@Nonnull Configuration config) {
      _config = config;
    }

    @Override
    public Set<CommunityVar> visitBooleanExprs(
        StaticBooleanExpr staticBooleanExpr, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitCallExpr(CallExpr callExpr, CommunityContext arg) {
      TreeSet<CommunityVar> result = new TreeSet<>();
      RoutingPolicy policy = _config.getRoutingPolicies().get(callExpr.getCalledPolicyName());
      for (Statement s :
          Optional.ofNullable(policy)
              .map(RoutingPolicy::getStatements)
              .orElse(Collections.emptyList())) {
        result.addAll(s.accept(new StatementVarExtractor(_config), arg));
      }
      return result;
    }

    @Override
    public Set<CommunityVar> visitConjunction(Conjunction conjunction, CommunityContext arg) {
      TreeSet<CommunityVar> result = new TreeSet<>();
      conjunction.getConjuncts().forEach(c -> result.addAll(c.accept(this, arg)));
      return result;
    }

    @Override
    public Set<CommunityVar> visitConjunctionChain(
        ConjunctionChain conjunctionChain, CommunityContext arg) {
      TreeSet<CommunityVar> result = new TreeSet<>();
      conjunctionChain.getSubroutines().forEach(c -> result.addAll(c.accept(this, arg)));
      return result;
    }

    @Override
    public Set<CommunityVar> visitDisjunction(Disjunction disjunction, CommunityContext arg) {
      TreeSet<CommunityVar> result = new TreeSet<>();
      disjunction.getDisjuncts().forEach(c -> result.addAll(c.accept(this, arg)));
      return result;
    }

    @Override
    public Set<CommunityVar> visitFirstMatchChain(
        FirstMatchChain firstMatchChain, CommunityContext arg) {
      TreeSet<CommunityVar> result = new TreeSet<>();
      firstMatchChain.getSubroutines().forEach(c -> result.addAll(c.accept(this, arg)));
      return result;
    }

    @Override
    public Set<CommunityVar> visitHasRoute(HasRoute hasRoute, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitHasRoute6(HasRoute6 hasRoute6, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchAsPath(MatchAsPath matchAsPath, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchColor(MatchColor matchColor, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchCommunities(
        MatchCommunities matchCommunities, CommunityContext arg) {
      TreeSet<CommunityVar> result = new TreeSet<>();
      result.addAll(
          CommunityVarExprCollector.collectSetMatchExpr(
              matchCommunities.getCommunitySetMatchExpr(), arg));
      result.addAll(
          CommunityVarExprCollector.collectSetExpr(matchCommunities.getCommunitySetExpr(), arg));
      return result;
    }

    @Override
    public Set<CommunityVar> visitMatchCommunitySet(
        MatchCommunitySet matchCommunitySet, CommunityContext arg) {
      return matchCommunitySet.getExpr().accept(new CommunitySetExprExtractor(_config));
    }

    @Override
    public Set<CommunityVar> visitMatchEntireCommunitySet(
        MatchEntireCommunitySet matchEntireCommunitySet, CommunityContext arg) {
      return matchEntireCommunitySet.getExpr().accept(new CommunitySetExprExtractor(_config));
    }

    @Override
    public Set<CommunityVar> visitMatchIp6AccessList(
        MatchIp6AccessList matchIp6AccessList, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchIpv4(MatchIpv4 matchIpv4, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchIpv6(MatchIpv6 matchIpv6, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchLocalPreference(
        MatchLocalPreference matchLocalPreference, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchLocalRouteSourcePrefixLength(
        MatchLocalRouteSourcePrefixLength matchLocalRouteSourcePrefixLength, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchMetric(MatchMetric matchMetric, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchPrefix6Set(
        MatchPrefix6Set matchPrefix6Set, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchPrefixSet(
        MatchPrefixSet matchPrefixSet, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchProcessAsn(
        MatchProcessAsn matchProcessAsn, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchProtocol(MatchProtocol matchProtocol, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchRouteType(
        MatchRouteType matchRouteType, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchSourceVrf(
        MatchSourceVrf matchSourceVrf, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitMatchTag(MatchTag matchTag, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitNeighborIsAsPath(
        NeighborIsAsPath neighborIsAsPath, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitNot(Not not, CommunityContext arg) {
      return not.getExpr().accept(this, arg);
    }

    @Override
    public Set<CommunityVar> visitOriginatesFromAsPath(
        OriginatesFromAsPath originatesFromAsPath, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitPassesThroughAsPath(
        PassesThroughAsPath passesThroughAsPath, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitRouteIsClassful(
        RouteIsClassful routeIsClassful, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitWithEnvironmentExpr(
        WithEnvironmentExpr withEnvironmentExpr, CommunityContext arg) {

      TreeSet<CommunityVar> result = new TreeSet<>();
      for (Statement s : withEnvironmentExpr.getPostStatements()) {
        result.addAll(s.accept(new StatementVarExtractor(_config), arg));
      }
      for (Statement s : withEnvironmentExpr.getPreStatements()) {
        result.addAll(s.accept(new StatementVarExtractor(_config), arg));
      }
      for (Statement s : withEnvironmentExpr.getPostTrueStatements()) {
        result.addAll(s.accept(new StatementVarExtractor(_config), arg));
      }
      result.addAll(withEnvironmentExpr.getExpr().accept(this, arg));
      return result;
    }
  }

  private static class CommunitySetExprExtractor
      implements CommunitySetExprVisitor<Set<CommunityVar>> {

    private Configuration _config;

    CommunitySetExprExtractor(Configuration config) {
      _config = config;
    }

    @Override
    public Set<CommunityVar> visitCommunityHalvesExpr(CommunityHalvesExpr communityHalvesExpr) {
      System.err.println("CommunityVarExtractor : visitCommunityHalvesExpr not implemented");
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitCommunityList(CommunityList communityList) {
      TreeSet<CommunityVar> result = new TreeSet<>();
      for (CommunityListLine line : communityList.getLines()) {
        result.addAll(line.getMatchCondition().accept(this));
      }
      return result;
    }

    @Override
    public Set<CommunityVar> visitEmptyCommunitySetExpr(
        EmptyCommunitySetExpr emptyCommunitySetExpr) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitLiteralCommunity(LiteralCommunity literalCommunity) {
      return Collections.singleton(CommunityVar.from(literalCommunity.getCommunity()));
    }

    @Override
    public Set<CommunityVar> visitLiteralCommunityConjunction(
        LiteralCommunityConjunction literalCommunityConjunction) {
      return literalCommunityConjunction.getRequiredCommunities().stream()
          .map(CommunityVar::from)
          .collect(Collectors.toSet());
    }

    @Override
    public Set<CommunityVar> visitLiteralCommunitySet(LiteralCommunitySet literalCommunitySet) {
      return literalCommunitySet.getCommunities().stream()
          .map(CommunityVar::from)
          .collect(Collectors.toSet());
    }

    @Override
    public Set<CommunityVar> visitNamedCommunitySet(NamedCommunitySet namedCommunitySet) {
      TreeSet<CommunityVar> result = new TreeSet<>();
      List<CommunityListLine> lineList =
          _config.getCommunityLists().get(namedCommunitySet.getName()).getLines();
      for (CommunityListLine line : lineList) {
        result.addAll(line.getMatchCondition().accept(this));
      }
      return result;
    }

    @Override
    public Set<CommunityVar> visitRegexCommunitySet(RegexCommunitySet regexCommunitySet) {
      return Collections.singleton(CommunityVar.from(regexCommunitySet.getRegex()));
    }
  }

  private static class StatementVarExtractor
      implements StatementVisitor<Set<CommunityVar>, CommunityContext> {

    @Nonnull private final Configuration _config;

    StatementVarExtractor(@Nonnull Configuration config) {
      _config = config;
    }

    @Override
    public Set<CommunityVar> visitAddCommunity(AddCommunity addCommunity, CommunityContext arg) {
      return addCommunity.getExpr().accept(new CommunitySetExprExtractor(_config));
    }

    @Override
    public Set<CommunityVar> visitBufferedStatement(
        BufferedStatement bufferedStatement, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitCallStatement(CallStatement callStatement, CommunityContext arg) {
      RoutingPolicy p = _config.getRoutingPolicies().get(callStatement.getCalledPolicyName());
      TreeSet<CommunityVar> result = new TreeSet<>();
      for (Statement s : p.getStatements()) {
        result.addAll(s.accept(this, arg));
      }
      return result;
    }

    @Override
    public Set<CommunityVar> visitComment(Comment comment, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitDeleteCommunity(
        DeleteCommunity deleteCommunity, CommunityContext arg) {
      return deleteCommunity.getExpr().accept(new CommunitySetExprExtractor(_config));
    }

    @Override
    public Set<CommunityVar> visitIf(If if1, CommunityContext arg) {
      TreeSet<CommunityVar> result = new TreeSet<>();

      for (Statement s : if1.getFalseStatements()) {
        result.addAll(s.accept(this, arg));
      }
      for (Statement s : if1.getTrueStatements()) {
        result.addAll(s.accept(this, arg));
      }
      result.addAll(if1.getGuard().accept(new BooleanExprExtractor(_config), arg));
      return result;
    }

    @Override
    public Set<CommunityVar> visitPrependAsPath(PrependAsPath prependAsPath, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitRetainCommunity(
        RetainCommunity retainCommunity, CommunityContext arg) {
      return retainCommunity.getExpr().accept(new CommunitySetExprExtractor(_config));
    }

    @Override
    public Set<CommunityVar> visitSetAdministrativeCost(
        SetAdministrativeCost setAdministrativeCost, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetCommunities(
        SetCommunities setCommunities, CommunityContext arg) {
      return CommunityVarExprCollector.collectSetExpr(setCommunities.getCommunitySetExpr(), arg);
    }

    @Override
    public Set<CommunityVar> visitSetCommunity(SetCommunity setCommunity, CommunityContext arg) {
      return setCommunity.getExpr().accept(new CommunitySetExprExtractor(_config));
    }

    @Override
    public Set<CommunityVar> visitSetDefaultPolicy(
        SetDefaultPolicy setDefaultPolicy, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetEigrpMetric(
        SetEigrpMetric setEigrpMetric, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetIsisLevel(SetIsisLevel setIsisLevel, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetIsisMetricType(
        SetIsisMetricType setIsisMetricType, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetLocalPreference(
        SetLocalPreference setLocalPreference, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetMetric(SetMetric setMetric, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetNextHop(SetNextHop setNextHop, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetOrigin(SetOrigin setOrigin, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetOspfMetricType(
        SetOspfMetricType setOspfMetricType, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetTag(SetTag setTag, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetVarMetricType(
        SetVarMetricType setVarMetricType, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitSetWeight(SetWeight setWeight, CommunityContext arg) {
      return Collections.emptySet();
    }

    @Override
    public Set<CommunityVar> visitStaticStatement(
        StaticStatement staticStatement, CommunityContext arg) {
      return Collections.emptySet();
    }
  }

  public @Nonnull CommunityVarSet getCommunityVars() {
    TreeSet<CommunityVar> result = new TreeSet<>();
    for (Configuration c : _configsToPolicy.keySet()) {
      CommunityContext ctx = CommunityContext.fromEnvironment(Environment.builder(c).build());
      for (RoutingPolicy policy : _configsToPolicy.get(c)) {
        for (Statement statement : policy.getStatements()) {
          result.addAll(statement.accept(new StatementVarExtractor(c), ctx));
        }
      }
    }
    return new CommunityVarSet(result);
  }
}

package org.batfish.minesweeper.policylocalize;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.Environment;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.communities.MatchCommunities;
import org.batfish.datamodel.routing_policy.communities.SetCommunities;
import org.batfish.datamodel.routing_policy.expr.*;
import org.batfish.datamodel.routing_policy.expr.BooleanExprs.StaticBooleanExpr;
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

public class RoutePolicyNamesExtractor {

  private static class NamesStatementVisitor implements StatementVisitor<List<String>, List<String>> {

    private Configuration _config;
    private Environment _env;

    public NamesStatementVisitor(Configuration config) {
      _config = config;
      _env = Environment.builder(_config).build();
    }

    @Override public List<String> visitAddCommunity(AddCommunity addCommunity, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitBufferedStatement(BufferedStatement bufferedStatement,
        List<String> arg) {
      return bufferedStatement.getStatement().accept(this, arg);
    }

    @Override public List<String> visitCallStatement(CallStatement callStatement,
        List<String> arg) {
      arg.add(callStatement.getCalledPolicyName());
      return arg;
    }

    @Override public List<String> visitComment(Comment comment, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitDeleteCommunity(DeleteCommunity deleteCommunity,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitIf(If if1, List<String> arg) {
      for (Statement statement : if1.getTrueStatements()) {
        arg = statement.accept(this, arg);
      }
      for (Statement statement : if1.getFalseStatements()) {
        arg = statement.accept(this, arg);
      }
      BooleanExpr expr = if1.getGuard();
      arg = expr.accept(new NamesBooleanExprVisitor(_config), arg);

      return arg;
    }

    @Override public List<String> visitPrependAsPath(PrependAsPath prependAsPath,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitRetainCommunity(RetainCommunity retainCommunity,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetAdministrativeCost(
        SetAdministrativeCost setAdministrativeCost, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetCommunities(SetCommunities setCommunities,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetCommunity(SetCommunity setCommunity, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetDefaultPolicy(SetDefaultPolicy setDefaultPolicy,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetEigrpMetric(SetEigrpMetric setEigrpMetric,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetIsisLevel(SetIsisLevel setIsisLevel, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetIsisMetricType(SetIsisMetricType setIsisMetricType,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetLocalPreference(SetLocalPreference setLocalPreference,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetMetric(SetMetric setMetric, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetNextHop(SetNextHop setNextHop, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetOrigin(SetOrigin setOrigin, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetOspfMetricType(SetOspfMetricType setOspfMetricType,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetTag(SetTag setTag, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetVarMetricType(SetVarMetricType setVarMetricType,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitSetWeight(SetWeight setWeight, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitStaticStatement(StaticStatement staticStatement,
        List<String> arg) {
      return arg;
    }
  }

  private static class NamesBooleanExprVisitor implements BooleanExprVisitor<List<String>, List<String>> {

    @Nonnull private final Configuration _config;
    @Nonnull private final Environment _env;

    public NamesBooleanExprVisitor(@Nonnull Configuration config) {
      _config = config;
      _env = Environment.builder(_config).build();
    }

    @Override public List<String> visitBooleanExprs(StaticBooleanExpr staticBooleanExpr,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitCallExpr(CallExpr callExpr, List<String> arg) {
      arg.add(callExpr.getCalledPolicyName());
      RoutingPolicy policy =_env.getRoutingPolicies().get(callExpr.getCalledPolicyName());
      for (Statement statement : policy.getStatements()) {
        arg = statement.accept(new NamesStatementVisitor(_config), arg);
      }
      return arg;
    }

    @Override public List<String> visitConjunction(Conjunction conjunction, List<String> arg) {
      for (BooleanExpr expr : conjunction.getConjuncts()) {
          arg = expr.accept(this, arg);
      }
      return arg;
    }

    @Override public List<String> visitConjunctionChain(ConjunctionChain conjunctionChain,
        List<String> arg) {
      for (BooleanExpr expr : conjunctionChain.getSubroutines()) {
        arg = expr.accept(this, arg);
      }
      return arg;
    }

    @Override public List<String> visitDisjunction(Disjunction disjunction, List<String> arg) {
      for (BooleanExpr expr : disjunction.getDisjuncts()) {
        arg = expr.accept(this, arg);
      }
      return arg;
    }

    @Override public List<String> visitFirstMatchChain(FirstMatchChain firstMatchChain,
        List<String> arg) {
      for (BooleanExpr expr : firstMatchChain.getSubroutines()) {
        arg = expr.accept(this, arg);
      }
      return arg;
    }

    @Override public List<String> visitHasRoute(HasRoute hasRoute, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitHasRoute6(HasRoute6 hasRoute6, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchAsPath(MatchAsPath matchAsPath, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchColor(MatchColor matchColor, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchCommunities(MatchCommunities matchCommunities,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchCommunitySet(MatchCommunitySet matchCommunitySet,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchEntireCommunitySet(
        MatchEntireCommunitySet matchEntireCommunitySet, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchIp6AccessList(MatchIp6AccessList matchIp6AccessList,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchIpv4(MatchIpv4 matchIpv4, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchIpv6(MatchIpv6 matchIpv6, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchLocalPreference(
        MatchLocalPreference matchLocalPreference, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchLocalRouteSourcePrefixLength(
        MatchLocalRouteSourcePrefixLength matchLocalRouteSourcePrefixLength, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchMetric(MatchMetric matchMetric, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchPrefix6Set(MatchPrefix6Set matchPrefix6Set,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchPrefixSet(MatchPrefixSet matchPrefixSet,
        List<String> arg) {
      if (matchPrefixSet.getPrefixSet() instanceof NamedPrefixSet) {
        arg.add(((NamedPrefixSet) matchPrefixSet.getPrefixSet()).getName());
      }
      return arg;
    }

    @Override public List<String> visitMatchProcessAsn(MatchProcessAsn matchProcessAsn,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchProtocol(MatchProtocol matchProtocol,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchRouteType(MatchRouteType matchRouteType,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchSourceVrf(MatchSourceVrf matchSourceVrf,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitMatchTag(MatchTag matchTag, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitNeighborIsAsPath(NeighborIsAsPath neighborIsAsPath,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitNot(Not not, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitOriginatesFromAsPath(
        OriginatesFromAsPath originatesFromAsPath, List<String> arg) {
      return arg;
    }

    @Override public List<String> visitPassesThroughAsPath(PassesThroughAsPath passesThroughAsPath,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitRouteIsClassful(RouteIsClassful routeIsClassful,
        List<String> arg) {
      return arg;
    }

    @Override public List<String> visitWithEnvironmentExpr(WithEnvironmentExpr withEnvironmentExpr,
        List<String> arg) {
      // TODO: Handle this case for redistribution
      return arg;
    }
  }

  public List<String> extractCalledPolicies(RoutingPolicy policy, Configuration config) {
    if (policy == null || config == null) {
      return new ArrayList<>();
    }
    List<String> acc = new ArrayList<>();
    acc.add(policy.getName());
    NamesStatementVisitor visitor = new NamesStatementVisitor(config);
    for (Statement statement : policy.getStatements()) {
      acc = statement.accept(visitor, acc);
    }
    return acc;
  }

  public List<String> extractCalledPoliciesWithoutGenerated(RoutingPolicy policy, Configuration config) {
    List<String> result = extractCalledPolicies(policy, config);
    result.removeIf(s -> s.startsWith("~"));
    return result;
  }

  public String getCombinedCalledPoliciesWithoutGenerated(RoutingPolicy policy, Configuration config) {
    String joined = String.join(" ", new RoutePolicyNamesExtractor()
        .extractCalledPoliciesWithoutGenerated(policy, config));
    if (joined.isEmpty()) {
      joined = "DEFAULT POLICY";
    }
    return joined;
  }

  public boolean hasNonDefaultPolicy(RoutingPolicy policy, Configuration config) {
    return extractCalledPoliciesWithoutGenerated(policy, config).size() > 0;
  }
}

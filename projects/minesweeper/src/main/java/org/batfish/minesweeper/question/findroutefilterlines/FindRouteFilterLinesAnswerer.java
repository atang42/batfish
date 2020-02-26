package org.batfish.minesweeper.question.findroutefilterlines;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;
import org.batfish.common.Answerer;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.StringAnswerElement;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.minesweeper.CommunityVar;
import org.batfish.minesweeper.Graph;
import org.batfish.minesweeper.GraphEdge;
import org.batfish.minesweeper.TransferResult;
import org.batfish.minesweeper.abstraction.InterfacePolicy;
import org.batfish.minesweeper.bdd.BDDNetwork;
import org.batfish.minesweeper.bdd.BDDRoute;
import org.batfish.minesweeper.bdd.PolicyQuotient;
import org.batfish.minesweeper.bdd.TransferBDD;
import org.batfish.minesweeper.bdd.TransferReturn;
import org.batfish.minesweeper.policylocalize.RouteToBDD;
import org.batfish.minesweeper.policylocalize.SymbolicResult;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.RoutingPolicySpecifier;
import org.batfish.specifier.SpecifierContext;

public class FindRouteFilterLinesAnswerer extends Answerer {

  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final RoutingPolicySpecifier _policySpecifier;
  @Nonnull private final PrefixRange _prefixRange;
  @Nullable private final Ip _includedPacket;
  private final boolean _accepted;

  public FindRouteFilterLinesAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    FindRouteFilterLinesQuestion findQuestion = (FindRouteFilterLinesQuestion) question;
    _nodeSpecifier = findQuestion.getNodeSpecifier();
    _policySpecifier = findQuestion.getPolicySpecifier();
    if (findQuestion.getPrefixRange() != null) {
      _prefixRange = PrefixRange.fromString(findQuestion.getPrefixRange());
    } else {
      _prefixRange = new PrefixRange(Prefix.ZERO, new SubRange(0,32));
    }
    _includedPacket = (findQuestion.getIncludedPacket() != null) ?
        Ip.parse(findQuestion.getIncludedPacket()) : null;
    _accepted = findQuestion.getAccepted() != null
        && findQuestion.getAccepted().equalsIgnoreCase("true");
  }

  @Override public AnswerElement answer() {
    printStaticRoutes();
    // test();
    printPaths();
    return new StringAnswerElement("DONE");
  }

  private void test() {
    BDDPacket pkt = new BDDPacket();
    Graph graph = new Graph(_batfish);
    BDDNetwork network = BDDNetwork.create(pkt, graph, NodesSpecifier.ALL);

    for (Entry<GraphEdge, InterfacePolicy> ent : network.getImportPolicyMap().entrySet()) {
      GraphEdge edge = ent.getKey();
      InterfacePolicy policy = ent.getValue();

      System.out.println(edge);
      System.out.println(policy);
      System.out.println((policy.getAcl() != null) + " " + (policy.getBgpPolicy() != null));
    }
  }

  private void printPaths() {
    SpecifierContext specifierContext = _batfish.specifierContext();
    Graph graph = new Graph(_batfish);
    PolicyQuotient policyQuotient = new PolicyQuotient();
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);
    for (String node : nodeSet) {
      Configuration config = specifierContext.getConfigs().get(node);

      Set<RoutingPolicy> policySet = _policySpecifier.resolve(node, specifierContext);
      for (RoutingPolicy policy : policySet) {
        TransferBDD transferBDD =
            new TransferBDD(graph, config, policy.getStatements(), policyQuotient);
        Set<CommunityVar> comms = graph.getAllCommunities();
        BDDRoute route = new BDDRoute(comms);
        BDDRoute record = route.deepCopy();
        TransferResult<TransferReturn, BDD> transferResult =
            transferBDD.compute(new TreeSet<>(), route);
        BDD accepted = transferResult.getReturnValue().getSecond();
        BDD rejected = accepted.not();

        /*
        List<Statement> stmts = transferBDD.getStatementsActingOnPrefix(_prefix, transferResult.getReturnValue().getFirst());
        System.out.println(policy.getName());
        System.out.println("Relevant Statements");
        stmts.stream().filter(stmt -> stmt.getText() != null)
            .forEach(stmt ->
                System.out.println(stmt.getText()));
        System.out.println(transferBDD.getAccepted(_prefix, record));
        */

        Map<BDD, List<Statement>> stmtmap = transferBDD.getBDDPolicyActionMap().getStatementMap();


        System.out.println(policy.getName());
        System.out.println();
        for (Entry<BDD, List<Statement>> entry : stmtmap.entrySet()) {
          BDD routeSubspace = entry.getKey();
          List<Statement> statements = entry.getValue();

          System.out.println("----------------------------------");
          BDD example = routeSubspace.fullSatOne();
          Long prefix = record.getPrefix().getValueSatisfying(example).get();
          Long prefixLen = record.getPrefixLength().getValueSatisfying(example).get();
          System.out.println(Prefix.create(Ip.create(prefix), Math.toIntExact(prefixLen)));
          for (Entry<CommunityVar, BDD> commEntry : record.getCommunities().entrySet()) {
            CommunityVar commVar = commEntry.getKey();
            BDD commBDD = commEntry.getValue();
            if (!commBDD.and(example).isZero()) {
              System.out.print(commVar.getRegex() + " ");
            }
          }
          System.out.println();
          statements.stream()
              .filter(stmt -> stmt.getText() != null && !stmt.getText().trim().isEmpty())
              .forEach(stmt ->
                  System.out.println(stmt.getText()));
          System.out.println(transferBDD.getAccepted(routeSubspace));
        }
        System.out.println("----------------------------------");
      }
    }
  }

  public void printAccepted() {
    SpecifierContext specifierContext = _batfish.specifierContext();
    Graph graph = new Graph(_batfish);
    PolicyQuotient policyQuotient = new PolicyQuotient();
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);
    for (String node : nodeSet) {
      Configuration config = specifierContext.getConfigs().get(node);

      Set<RoutingPolicy> policySet = _policySpecifier.resolve(node, specifierContext);
      for (RoutingPolicy policy : policySet) {
        TransferBDD transferBDD =
            new TransferBDD(graph, config, policy.getStatements(), policyQuotient);
        Set<CommunityVar> comms = graph.getAllCommunities();
        BDDRoute route = new BDDRoute(comms);
        BDDRoute record = route.deepCopy();
        TransferResult<TransferReturn, BDD> transferResult =
            transferBDD.compute(new TreeSet<>(), route);

        /*
        List<Statement> stmts = transferBDD.getStatementsActingOnPrefix(_prefix, transferResult.getReturnValue().getFirst());
        System.out.println(policy.getName());
        System.out.println("Relevant Statements");
        stmts.stream().filter(stmt -> stmt.getText() != null)
            .forEach(stmt ->
                System.out.println(stmt.getText()));
        System.out.println(transferBDD.getAccepted(_prefix, record));
        */

        Map<BDD, List<Statement>> stmtmap = transferBDD.getBDDPolicyActionMap().getStatementMap();


        System.out.println(policy.getName());
        System.out.println();
        for (Entry<BDD, List<Statement>> entry : stmtmap.entrySet()) {
          BDD routeSubspace = entry.getKey();
          List<Statement> statements = entry.getValue();
          if (!matchBDD(transferBDD, record, routeSubspace)) {
            continue;
          }

          System.out.println("----------------------------------");
          BDD example = routeSubspace.fullSatOne();
          Long prefix = record.getPrefix().getValueSatisfying(example).get();
          Long prefixLen = record.getPrefixLength().getValueSatisfying(example).get();
          System.out.println(Prefix.create(Ip.create(prefix), Math.toIntExact(prefixLen)));
          for (Entry<CommunityVar, BDD> commEntry : record.getCommunities().entrySet()) {
            CommunityVar commVar = commEntry.getKey();
            BDD commBDD = commEntry.getValue();
            if (!commBDD.and(example).isZero()) {
              System.out.print(commVar.getRegex() + " ");
            }
          }
          System.out.println();
          statements.stream()
              .filter(stmt -> stmt.getText() != null)
              .forEach(stmt ->
                  System.out.println(stmt.getText()));
          System.out.println(transferBDD.getAccepted(routeSubspace));
        }
        System.out.println("----------------------------------");
      }
    }
  }

  private boolean matchBDD(TransferBDD transferBDD, BDDRoute record, BDD routeSpace) {
    if (_accepted && transferBDD.getAccepted(routeSpace) != SymbolicResult.ACCEPT) {
      return false;
    }
    BDD matches = new RouteToBDD(record).buildPrefixBDD(_prefixRange);
    if (matches.and(routeSpace).isZero()) {
      return false;
    }
    return true;
  }

  public void printStaticRoutes() {
    SpecifierContext specifierContext = _batfish.specifierContext();
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);
    for (String node : nodeSet) {
      System.out.println(node);
      Configuration config = specifierContext.getConfigs().get(node);
      config
          .getDefaultVrf()
          .getStaticRoutes()
          .forEach(
              route -> {
                System.out.println("\t"
                        + route.getNetwork() + " "
                        + route.getAdministrativeCost() + " "
                        + route.getMetric() + " "
                        + route.getTag() + " "
                        + route.getNextHopInterface() + " "
                        + route.getNextHopIp() + " "
                        + route.getProtocol() + " '"
                        + route.getText() + "'");
              });
    }
  }

}

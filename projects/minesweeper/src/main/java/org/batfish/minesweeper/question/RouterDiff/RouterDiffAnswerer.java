package org.batfish.minesweeper.question.RouterDiff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import net.sf.javabdd.BDD;
import org.apache.commons.lang3.ObjectUtils;
import org.batfish.common.Answerer;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.StringAnswerElement;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.minesweeper.CommunityVar;
import org.batfish.minesweeper.Graph;
import org.batfish.minesweeper.GraphEdge;
import org.batfish.minesweeper.Protocol;
import org.batfish.minesweeper.TransferResult;
import org.batfish.minesweeper.abstraction.InterfacePolicy;
import org.batfish.minesweeper.bdd.BDDNetwork;
import org.batfish.minesweeper.bdd.BDDRoute;
import org.batfish.minesweeper.bdd.PolicyQuotient;
import org.batfish.minesweeper.bdd.TransferBDD;
import org.batfish.minesweeper.bdd.TransferReturn;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class RouterDiffAnswerer extends Answerer {

  @Nonnull private final NodeSpecifier _nodeSpecifier;

  public RouterDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    RouterDiffQuestion findQuestion = (RouterDiffQuestion) question;
    _nodeSpecifier = findQuestion.getNodeSpecifier();
  }

  @Override public AnswerElement answer() {
    // printStaticRoutes();
    // test();
    getBGPDiff();
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

    graph
        .getEdgeMap()
        .forEach(
            (key, value) -> {
              System.out.println(key);
              value.forEach(edge -> System.out.println("\t" + edge));
            });

    System.out.println("eBGP neighbors");
    graph
        .getEbgpNeighbors()
        .forEach(
            (key, value) -> {
              System.out.println(key + " " + value.getPeerAddress());
            });

    System.out.println("BGP neighbors");
    graph
        .getNeighbors()
        .forEach(
            (key, value) -> {
              System.out.println(key);
              value.forEach(neigh -> System.out.println("\t" + neigh));
            });
  }

  private void getBGPDiff() {
    Graph graph = new Graph(_batfish);
    SpecifierContext specifierContext = _batfish.specifierContext();
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);

    if (nodeSet.size() < 2) {
      System.err.println("Fewer than 2 specified nodes: ");
      nodeSet.forEach(System.out::println);
      return;
    } else if (nodeSet.size() > 2) {
      System.err.println("More than 2 specified nodes: ");
      nodeSet.forEach(System.out::println);
      return;
    }

    List<Configuration> configurations =
        nodeSet.stream().map(specifierContext.getConfigs()::get).collect(Collectors.toList());

    Map<GraphEdge, GraphEdge> linkPairs = new HashMap<>();
    List<GraphEdge> r1Edges = graph.getEdgeMap().get(configurations.get(0).getHostname());
    List<GraphEdge> r2Edges = graph.getEdgeMap().get(configurations.get(1).getHostname());

    // Exact edge matches
    for (GraphEdge r1Edge : r1Edges) {
      GraphEdge foundEdge = null;
      for (GraphEdge r2Edge : r2Edges) {
        if (r1Edge.isAbstract() == r2Edge.isAbstract()) {
          if (r1Edge.getPeer() != null
              && r2Edge.getPeer() != null
              && r1Edge.getPeer().equals(r2Edge.getPeer())) {
            linkPairs.put(r1Edge, r2Edge);
            foundEdge = r2Edge;
          } else if (r1Edge.isNullEdge() && r2Edge.isNullEdge()) {
            linkPairs.put(r1Edge, r2Edge);
            foundEdge = r2Edge;
          }
        }
      }
      if (foundEdge != null) {
        r2Edges.remove(foundEdge);
      }
    }

    Set<CommunityVar> comms = graph.getAllCommunities();
    BDDRoute record = new BDDRoute(comms);
    PolicyQuotient policyQuotient = new PolicyQuotient();

    for (Entry<GraphEdge, GraphEdge> pair : linkPairs.entrySet()) {
      System.out.println(pair.getKey() + " : " + pair.getValue());
      GraphEdge ge1 = pair.getKey();
      GraphEdge ge2 = pair.getValue();
      Optional.ofNullable(graph.findImportRoutingPolicy(ge1.getRouter(), Protocol.BGP, ge1)).ifPresent(p -> System.out.println("\tIMPORT1 " + p.getName()));
      Optional.ofNullable(graph.findImportRoutingPolicy(ge2.getRouter(), Protocol.BGP, ge2)).ifPresent(p -> System.out.println("\tIMPORT2 " + p.getName()));
      Optional.ofNullable(graph.findExportRoutingPolicy(ge1.getRouter(), Protocol.BGP, ge1)).ifPresent(p -> System.out.println("\tEXPORT1 " + p.getName()));
      Optional.ofNullable(graph.findExportRoutingPolicy(ge2.getRouter(), Protocol.BGP, ge2)).ifPresent(p -> System.out.println("\tEXPORT2 " + p.getName()));

      RoutingPolicy r1Import = graph.findImportRoutingPolicy(ge1.getRouter(), Protocol.BGP, ge1);

      RoutingPolicy r2Import = graph.findImportRoutingPolicy(ge2.getRouter(), Protocol.BGP, ge2);

      if (r1Import == null && r2Import == null) {
        System.out.println("NO IMPORT POLICIES");
        continue;
      }

      // Compare Imports
      r1Import =
          ObjectUtils.defaultIfNull(
              graph.findImportRoutingPolicy(ge1.getRouter(), Protocol.BGP, ge1),
              Graph.findCommonRoutingPolicy(configurations.get(0), Protocol.BGP));
      r2Import =
          ObjectUtils.defaultIfNull(
              graph.findImportRoutingPolicy(ge2.getRouter(), Protocol.BGP, ge2),
              Graph.findCommonRoutingPolicy(configurations.get(1), Protocol.BGP));

      printRoutePolicyDiff(graph, configurations, r1Import, r2Import, record, policyQuotient);

      RoutingPolicy r1Export = graph.findImportRoutingPolicy(ge1.getRouter(), Protocol.BGP, ge1);
      RoutingPolicy r2Export = graph.findImportRoutingPolicy(ge2.getRouter(), Protocol.BGP, ge2);
      if (r1Export == null && r2Export == null) {
        System.out.println("NO IMPORT POLICIES");
        continue;
      }

      // Compare Imports
      r1Export =
          ObjectUtils.defaultIfNull(
              graph.findImportRoutingPolicy(ge1.getRouter(), Protocol.BGP, ge1),
              Graph.findCommonRoutingPolicy(configurations.get(0), Protocol.BGP));
      r2Export =
          ObjectUtils.defaultIfNull(
              graph.findImportRoutingPolicy(ge2.getRouter(), Protocol.BGP, ge2),
              Graph.findCommonRoutingPolicy(configurations.get(1), Protocol.BGP));

      printRoutePolicyDiff(graph, configurations, r1Export, r2Export, record, policyQuotient);
    }
  }

  private void printRoutePolicyDiff(Graph graph, List<Configuration> configurations,
      RoutingPolicy r1Import, RoutingPolicy r2Import,
      BDDRoute record, PolicyQuotient policyQuotient) {


    TransferBDD transferBDD1 =
        new TransferBDD(graph, configurations.get(0), r1Import.getStatements(), policyQuotient);
    BDDRoute route1 = record.deepCopy();
    TransferResult<TransferReturn, BDD> transferResult1 =
        transferBDD1.compute(new TreeSet<>(), route1);
    BDD accepted1 = transferResult1.getReturnValue().getSecond();

    TransferBDD transferBDD2 =
        new TransferBDD(graph, configurations.get(1), r2Import.getStatements(), policyQuotient);
    BDDRoute route2 = record.deepCopy();
    TransferResult<TransferReturn, BDD> transferResult2 =
        transferBDD2.compute(new TreeSet<>(), route2);
    BDD accepted2 = transferResult2.getReturnValue().getSecond();

    BDD difference = accepted1.and(accepted2.not()).or(accepted1.not().and(accepted2));
    System.out.println("HAS DIFF: " + !difference.isZero());
    printStatements(configurations.get(0).getHostname(), transferBDD1, r1Import, record, difference);
    printStatements(configurations.get(1).getHostname(), transferBDD2, r2Import, record, difference);
    System.out.println();
  }

  public void printStatements(String router, TransferBDD transferBDD, RoutingPolicy policy, BDDRoute record, BDD space) {
    Map<BDD, List<Statement>> stmtmap =
        transferBDD.getStatementsActingOnRouteSet(BDDRoute.getFactory().one(), record);

    System.out.println(router + " " + policy.getName());
    System.out.println();
    for (Entry<BDD, List<Statement>> entry : stmtmap.entrySet()) {
      BDD routeSubspace = entry.getKey().and(space);
      if (routeSubspace.isZero()) {
        continue;
      }
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
          .filter(stmt -> stmt.getText() != null)
          .forEach(stmt ->
              System.out.println(stmt.getText()));
      System.out.println(transferBDD.getAccepted(routeSubspace));
    }
    System.out.println("----------------------------------");
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

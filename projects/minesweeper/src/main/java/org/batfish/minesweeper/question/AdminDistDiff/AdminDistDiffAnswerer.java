package org.batfish.minesweeper.question.AdminDistDiff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.RoutingProtocol;
import org.batfish.datamodel.StaticRoute;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.ospf.OspfProcess;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;
import org.parboiled.common.ImmutableList;

public class AdminDistDiffAnswerer extends Answerer {

  private static final String COL_PROTOCOL = "Protocol";

  private static final String COL_NODE1 = "Node1";
  private static final String COL_COST1 = "AdminDist1";

  private static final String COL_NODE2 = "Node2";
  private static final String COL_COST2 = "AdminDist2";

  private static final int NON_EXISTANT = -1;

  @Nonnull private final NodeSpecifier _nodeSpecifier;

  public AdminDistDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    AdminDistDiffQuestion findQuestion = (AdminDistDiffQuestion) question;
    _nodeSpecifier = findQuestion.getNodeSpecifier();
  }

  @Override
  public TableAnswerElement answerDiff(NetworkSnapshot snapshot, NetworkSnapshot reference) {
    SpecifierContext currentContext = _batfish.specifierContext(snapshot);

    SpecifierContext referenceContext = _batfish.specifierContext(reference);

    Set<String> routersInBoth = new TreeSet<>(_nodeSpecifier.resolve(currentContext));
    routersInBoth.retainAll(_nodeSpecifier.resolve(referenceContext));

    TableAnswerElement answerElement = new TableAnswerElement(metadata());

    for (String routerName : routersInBoth) {
      List<Configuration> configurations = Arrays.asList(
          currentContext.getConfigs().get(routerName),
          referenceContext.getConfigs().get(routerName));

      for (Row row : ospfAdminDiff(configurations.get(0), configurations.get(1))) {
        answerElement.addRow(row);
      }
      for (Row row : bgpAdminDiff(configurations.get(0), configurations.get(1))) {
        answerElement.addRow(row);
      }
      for (Row row : staticOriginateDiff(configurations.get(0), configurations.get(1))) {
        answerElement.addRow(row);
      }
    }
    return answerElement;
  }

  @Override
  public TableAnswerElement answer(NetworkSnapshot snapshot) {

    SpecifierContext specifierContext = _batfish.specifierContext(snapshot);
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);

    if (nodeSet.size() < 2) {
      System.err.println("Fewer than 2 specified nodes: ");
      nodeSet.forEach(System.out::println);
      throw new IllegalArgumentException("Fewer than 2 specified nodes: " + nodeSet.stream()
          .reduce((a, b) -> a + "\n" + b)
          .orElse(""));
    } else if (nodeSet.size() > 2) {
      System.err.println("More than 2 specified nodes: ");
      nodeSet.forEach(System.out::println);
      throw new IllegalArgumentException("More than 2 specified nodes: " + nodeSet.stream()
          .reduce((a, b) -> a + "\n" + b)
          .orElse(""));
    }
    List<Configuration> configurations = nodeSet.stream().map(specifierContext.getConfigs()::get).collect(Collectors.toList());

    TableAnswerElement answerElement = new TableAnswerElement(metadata());
    for (Row row : ospfAdminDiff(configurations.get(0), configurations.get(1))) {
      answerElement.addRow(row);
    }
    for (Row row : bgpAdminDiff(configurations.get(0), configurations.get(1))) {
      answerElement.addRow(row);
    }
    for (Row row : staticOriginateDiff(configurations.get(0), configurations.get(1))) {
      answerElement.addRow(row);
    }

    return answerElement;
  }

  private Map<RoutingProtocol, Integer> getOspfAdmin(Configuration config) {
    if (!config.getDefaultVrf().getOspfProcesses().isEmpty()) {
      OspfProcess ospf = config.getDefaultVrf().getOspfProcesses().values().iterator().next();
      return ospf.getAdminCosts();
    }
    return new TreeMap<>();
  }

  private List<Row> ospfAdminDiff(Configuration configs1, Configuration configs2) {
    Map<RoutingProtocol, Integer> adminCosts1 = getOspfAdmin(configs1);
    Map<RoutingProtocol, Integer> adminCosts2 = getOspfAdmin(configs2);

    Set<RoutingProtocol> protocols = new TreeSet<>(adminCosts1.keySet());
    protocols.addAll(adminCosts2.keySet());
    List<Row> result = new ArrayList<>();
    for(RoutingProtocol proto : protocols) {
      int distance1 = adminCosts1.getOrDefault(proto, NON_EXISTANT);
      int distance2 = adminCosts2.getOrDefault(proto, NON_EXISTANT);

      if (distance1 != distance2) {
        result.add(makeRow(proto, configs1.getHostname(), distance1, configs2.getHostname(), distance2));
      }
    }
    return result;
  }

  private Map<RoutingProtocol, Integer> getBgpAdmin(Configuration config) {
    BgpProcess bgpProcess = config.getDefaultVrf().getBgpProcess();
    if (bgpProcess != null) {
      List<RoutingProtocol> protocols = Arrays.asList(RoutingProtocol.BGP, RoutingProtocol.IBGP);
      Map<RoutingProtocol, Integer> result = new TreeMap<>();
      if (bgpProcess != null) {
        for (RoutingProtocol proto : protocols) {
          result.put(proto, bgpProcess.getAdminCost(proto));
        }
      }
      return result;
    }
    return new TreeMap<>();
  }
  private List<Row> bgpAdminDiff(Configuration configs1, Configuration configs2) {

    Map<RoutingProtocol, Integer> adminCosts1 = getBgpAdmin(configs1);
    Map<RoutingProtocol, Integer> adminCosts2 = getBgpAdmin(configs2);

    Set<RoutingProtocol> protocols = new TreeSet<>(adminCosts1.keySet());
    protocols.addAll(adminCosts2.keySet());
    List<Row> result = new ArrayList<>();
    for(RoutingProtocol proto : protocols) {
      int distance1 = adminCosts1.getOrDefault(proto, NON_EXISTANT);
      int distance2 = adminCosts2.getOrDefault(proto, NON_EXISTANT);

      if (distance1 != distance2) {
        result.add(makeRow(proto, configs1.getHostname(), distance1, configs2.getHostname(), distance2));
      }
    }
    return result;
  }

  private static String combineString(Set<Integer> costs) {
    if (costs.isEmpty()) {
      return "None";
    }
    List<String> strings = costs.stream().map(x -> x.toString()).collect(Collectors.toList());
    return "[" + String.join(", ", strings) + "]";
  }

  /*
  Gets minimum admin distance, ie. ignores floating static routes.
   */
  private Set<Integer> getStaticAdmin(Configuration config) {
    Set<Integer> acc = new TreeSet<>();
    for (StaticRoute sr : config.getDefaultVrf().getStaticRoutes()) {
      acc.add(sr.getAdministrativeCost());
    }
    return acc;
  }

  private List<Row> staticOriginateDiff(Configuration configs1, Configuration configs2) {
    Set<Integer> adminCosts1 = getStaticAdmin(configs1);
    Set<Integer> adminCosts2 = getStaticAdmin(configs2);

    List<Row> result = new ArrayList<>();
    if (!adminCosts1.equals(adminCosts2)) {
      result.add(makeRow(RoutingProtocol.STATIC, configs1.getHostname(), adminCosts1, configs2.getHostname(), adminCosts2));
    }
    return result;
  }

  private Row makeRow(RoutingProtocol protocol, String node1, int cost1, String node2, int cost2) {
    String costString1 = cost1 != NON_EXISTANT ? Integer.toString(cost1) : "None";
    String costString2 = cost2 != NON_EXISTANT ? Integer.toString(cost2) : "None";
    return Row.builder()
        .put(COL_PROTOCOL, protocol.toString())
        .put(COL_NODE1, node1)
        .put(COL_COST1, costString1)
        .put(COL_NODE2, node2)
        .put(COL_COST2, costString2)
        .build();
  }

  private Row makeRow(RoutingProtocol protocol, String node1, Set<Integer> costs1, String node2, Set<Integer> costs2) {
    String costString1 = combineString(costs1);
    String costString2 = combineString(costs2);
    return Row.builder()
        .put(COL_PROTOCOL, protocol.toString())
        .put(COL_NODE1, node1)
        .put(COL_COST1, costString1)
        .put(COL_NODE2, node2)
        .put(COL_COST2, costString2)
        .build();
  }



  private static TableMetadata metadata() {
    return new TableMetadata(ImmutableList.of(
        new ColumnMetadata(COL_PROTOCOL, Schema.STRING, "Protocol", true, false),
        new ColumnMetadata(COL_NODE1, Schema.STRING, "Node1", true, false),
        new ColumnMetadata(COL_COST1, Schema.STRING, "AdminCost1", true, false),
        new ColumnMetadata(COL_NODE2, Schema.STRING, "Node2", true, false),
        new ColumnMetadata(COL_COST2, Schema.STRING, "AdminCost2", true, false)
    ));
  }
}

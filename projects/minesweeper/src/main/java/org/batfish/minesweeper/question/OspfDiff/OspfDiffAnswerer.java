package org.batfish.minesweeper.question.OspfDiff;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Edge;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.Topology;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.collections.NodeInterfacePair;
import org.batfish.datamodel.ospf.OspfNeighborConfigId;
import org.batfish.datamodel.ospf.OspfProcess;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.Row.RowBuilder;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;
import org.parboiled.common.ImmutableList;

public class OspfDiffAnswerer extends Answerer {

  private static final String COL_NODE1 = "Node1";
  private static final String COL_INTF1 = "Intf1";
  private static final String COL_IP1 = "IP1";
  private static final String COL_PEER1 = "Peer1";
  private static final String COL_AREA1 = "Area1";
  private static final String COL_AREA_TYPE1 = "AreaType1";
  private static final String COL_COST1 = "Cost1";
  private static final String COL_IS_PASSIVE1 = "IsPassive1";
  private static final String COL_NETWORK_TYPE1 = "NetworkType1";

  private static final String COL_NODE2 = "Node2";
  private static final String COL_INTF2 = "Intf2";
  private static final String COL_IP2 = "IP2";
  private static final String COL_PEER2 = "Peer2";
  private static final String COL_AREA2 = "Area2";
  private static final String COL_AREA_TYPE2 = "AreaType2";
  private static final String COL_COST2 = "Cost2";
  private static final String COL_IS_PASSIVE2 = "IsPassive2";
  private static final String COL_NETWORK_TYPE2 = "NetworkType2";

  @Nonnull private final NodeSpecifier _nodeSpecifier;

  public OspfDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    OspfDiffQuestion findQuestion = (OspfDiffQuestion) question;
    _nodeSpecifier = findQuestion.getNodeSpecifier();
  }

  @Override public TableAnswerElement answerDiff(NetworkSnapshot snapshot,
      NetworkSnapshot reference) {
    SpecifierContext currentContext = _batfish.specifierContext(snapshot);

    SpecifierContext referenceContext = _batfish.specifierContext(reference);

    Set<String> routersInBoth = new TreeSet<>(_nodeSpecifier.resolve(currentContext));
    routersInBoth.retainAll(_nodeSpecifier.resolve(referenceContext));

    TableAnswerElement answerElement = new TableAnswerElement(metadata());

    for (String routerName : routersInBoth) {
      List<Configuration> configurations = Arrays.asList(currentContext.getConfigs()
              .get(routerName),
          referenceContext.getConfigs().get(routerName));
      Map<String, OspfProcess> ospfProcesses1 = configurations.get(0)
          .getDefaultVrf()
          .getOspfProcesses();
      Map<String, OspfProcess> ospfProcesses2 = configurations.get(1)
          .getDefaultVrf()
          .getOspfProcesses();
      Set<String> keys = new TreeSet<>(ospfProcesses1.keySet());
      keys.addAll(ospfProcesses2.keySet());

      if (ospfProcesses1.isEmpty() || ospfProcesses2.isEmpty()) {
        if (!ospfProcesses2.isEmpty()) {
          System.out.println("Snapshot router " + routerName + " does not have OSPF");
        }
        if (!ospfProcesses1.isEmpty()) {
          System.out.println("Reference router " + routerName + " does not have OSPF");
        }
        continue;
      }
      // TODO: Check OSPF process ids
      OspfProcess ospfProcess1 = ospfProcesses1.values().iterator().next();
      OspfProcess ospfProcess2 = ospfProcesses2.values().iterator().next();

      Map<String, String> interfaceMap = getSameNameInterfaceMap(configurations.get(0),
          configurations.get(1));

      for (Row row : computeOspfDiff(
          configurations,
          ospfProcess1,
          ospfProcess2,
          interfaceMap,
          snapshot,
          reference)) {
        answerElement.addRow(row);
      }
    }
    return answerElement;
  }

  @Override public TableAnswerElement answer(NetworkSnapshot snapshot) {

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
    List<Configuration> configurations = nodeSet.stream()
        .map(specifierContext.getConfigs()::get)
        .collect(Collectors.toList());

    Map<String, OspfProcess> ospfProcesses1 = configurations.get(0)
        .getDefaultVrf()
        .getOspfProcesses();
    Map<String, OspfProcess> ospfProcesses2 = configurations.get(1)
        .getDefaultVrf()
        .getOspfProcesses();
    Set<String> keys = new TreeSet<>(ospfProcesses1.keySet());
    keys.addAll(ospfProcesses2.keySet());

    // TODO: Check OSPF process ids
    OspfProcess ospfProcess1 = null;
    OspfProcess ospfProcess2 = null;
    if (!ospfProcesses1.values().isEmpty()) {
      ospfProcess1 = ospfProcesses1.values().iterator().next();
    }
    if (!ospfProcesses2.values().isEmpty()) {
      ospfProcess2 = ospfProcesses2.values().iterator().next();
    }

    Map<String, String> interfaceMap = getDefaultInterfaceMap(configurations.get(0),
        configurations.get(1),
        _batfish,
        snapshot);

    TableAnswerElement answer = new TableAnswerElement(metadata());
    for (Row row : computeOspfDiff(
        configurations,
        ospfProcess1,
        ospfProcess2,
        interfaceMap,
        snapshot,
        snapshot)) {
      answer.addRow(row);
    }

    return answer;
  }

  private List<Row> computeOspfDiff(List<Configuration> configurations, OspfProcess ospfProcess1,
      OspfProcess ospfProcess2, Map<String, String> interfaceMap, NetworkSnapshot snapshot1,
      NetworkSnapshot snapshot2) {
    TreeMap<String, OspfInterfaceFeatures> features1 = new TreeMap<>();
    Topology topo1 = _batfish.getTopologyProvider().getInitialLayer3Topology(snapshot1);
    Topology topo2 = _batfish.getTopologyProvider().getInitialLayer3Topology(snapshot2);

    if (ospfProcess1 != null) {
      for (OspfNeighborConfigId id : ospfProcess1.getOspfNeighborConfigs().keySet()) {
        boolean intfActive = configurations.get(0).getAllInterfaces().get(id.getInterfaceName()).getActive();
        if (intfActive) {
          String neighbor = findNeighbor(configurations.get(0), id.getInterfaceName(), topo1);
          features1.put(
              id.getInterfaceName(),
              new OspfInterfaceFeatures(configurations.get(0), ospfProcess1, id, neighbor));
        }
      }
    }
    TreeMap<String, OspfInterfaceFeatures> features2 = new TreeMap<>();
    if (ospfProcess2 != null) {
      for (OspfNeighborConfigId id : ospfProcess2.getOspfNeighborConfigs().keySet()) {
        boolean intfActive = configurations.get(1).getAllInterfaces().get(id.getInterfaceName()).getActive();
        if (intfActive) {
          String neighbor = findNeighbor(configurations.get(1), id.getInterfaceName(), topo2);
          features2.put(
              id.getInterfaceName(),
              new OspfInterfaceFeatures(configurations.get(1), ospfProcess2, id, neighbor));
        }
      }
    }

    List<Row> result = new ArrayList<>();

    for (Entry<String, String> entry : interfaceMap.entrySet()) {
      String intf1 = entry.getKey();
      String intf2 = entry.getValue();
      if (!configurations.get(0).getAllInterfaces().containsKey(intf1)) {
        System.out.println(intf1 + " not found in " + configurations.get(0).getHostname());
        continue;
      }
      if (!configurations.get(1).getAllInterfaces().containsKey(intf2)) {
        System.out.println(intf2 + " not found in " + configurations.get(1).getHostname());
        continue;
      }
      OspfInterfaceFeatures feat1 = features1.get(intf1);
      OspfInterfaceFeatures feat2 = features2.get(intf2);

      if (feat1 == null && feat2 == null) {
        continue;
      }

      if (feat1 == null || !feat1.equalAttributes(feat2)) {
        result.add(makeRow(
            feat1,
            feat2,
            configurations.get(0).getHostname(),
            configurations.get(1).getHostname()));
      }
    }

    return result;
  }

  private Map<String, String> getDefaultInterfaceMap(Configuration config1, Configuration config2,
      IBatfish batfish, NetworkSnapshot snapshot) {
    Map<String, String> sameNameInterfaceMap = getSameNameInterfaceMap(config1, config2);
    Map<String, String> topologyInterfaceMap = getTopologyInterfaceMap(config1,
        config2,
        batfish,
        snapshot);

    Set<String> keys = new TreeSet<>(sameNameInterfaceMap.keySet());
    keys.addAll(topologyInterfaceMap.keySet());

    Map<String, String> result = new TreeMap<>();
    for (String key : keys) {
      if (sameNameInterfaceMap.containsKey(key) && topologyInterfaceMap.containsKey(key)
          && !sameNameInterfaceMap.get(key).equals(topologyInterfaceMap.get(key))) {
        System.out.println(String.format("Same name interface to different neighbors: %s:%s %s:%s",
            config1.getHostname(),
            key,
            config2.getHostname(),
            key));
      }
      result.put(key,
          MoreObjects.firstNonNull(topologyInterfaceMap.get(key), sameNameInterfaceMap.get(key)));
    }
    return result;
  }

  /*
  Creates a map matching all active interfaces in config1 with the similarly named interface in config2
   */
  private Map<String, String> getSameNameInterfaceMap(Configuration config1,
      Configuration config2) {
    Set<String> interfaces1 = new TreeSet<>(config1.getAllInterfaces().keySet());
    Set<String> interfaces2 = new TreeSet<>(config2.getAllInterfaces().keySet());

    interfaces1.removeIf(key -> !config1.getAllInterfaces().get(key).getActive());
    interfaces2.removeIf(key -> !config2.getAllInterfaces().get(key).getActive());

    interfaces1.retainAll(interfaces2);
    Map<String, String> result = new TreeMap<>();
    for (String s : interfaces1) {
      result.put(s, s);
    }
    return result;
  }

  private String findNeighbor(Configuration config, String intf, Topology topology) {
    List<String> neighbors = topology.getNeighbors(NodeInterfacePair.of(config.getHostname(), intf))
        .stream()
        .map(NodeInterfacePair::getHostname)
        .collect(Collectors.toList());
    return String.join(", ", neighbors);
  }

  Map<String, String> getTopologyInterfaceMap(Configuration config1, Configuration config2,
      IBatfish batfish, NetworkSnapshot snapshot) {
    Topology topology = batfish.getTopologyProvider().getInitialLayer3Topology(snapshot);
    SortedSet<Edge> edges1 = Optional.ofNullable(topology.getNodeEdges().get(config1.getHostname()))
        .orElse(new TreeSet<>());
    SortedSet<Edge> edges2 = Optional.ofNullable(topology.getNodeEdges().get(config2.getHostname()))
        .orElse(new TreeSet<>());

    Map<String, Set<String>> neighbors1 = new TreeMap<>();
    for (Edge e : edges1) {
      if (e.getNode1().equals(config1.getHostname())) {
        if (neighbors1.containsKey(e.getNode2())) {
          neighbors1.get(e.getNode2()).add(e.getInt1());
        } else {
          neighbors1.put(e.getNode2(), new TreeSet<>());
          neighbors1.get(e.getNode2()).add(e.getInt1());
        }
      }
    }
    Map<String, Set<String>> neighbors2 = new TreeMap<>();
    for (Edge e : edges2) {
      if (e.getNode1().equals(config2.getHostname())) {
        if (neighbors2.containsKey(e.getNode2())) {
          neighbors2.get(e.getNode2()).add(e.getInt1());
        } else {
          neighbors2.put(e.getNode2(), new TreeSet<>());
          neighbors2.get(e.getNode2()).add(e.getInt1());
        }
      }
    }

    Set<String> commonNeighbors = new TreeSet<>(neighbors1.keySet());
    commonNeighbors.retainAll(neighbors2.keySet());

    Map<String, String> result = new TreeMap<>();
    for (String n : commonNeighbors) {
      insertBestMatches(config1, config2, result, neighbors1.get(n), neighbors2.get(n));
    }

    if (neighbors1.containsKey(config2.getHostname())
        && neighbors2.containsKey(config1.getHostname())) {
      insertBestMatches(config1,
          config2,
          result,
          neighbors1.get(config2.getHostname()),
          neighbors2.get(config1.getHostname()));
    }

    return result;
  }

  /*
  Heuristic for matching multiple pairs of interfaces that are to the same neighbor
   */
  private void insertBestMatches(Configuration config1, Configuration config2,
      Map<String, String> matches, Set<String> first, Set<String> second) {
    if (first.isEmpty() || second.isEmpty()) {
      return;
    }
    if (first.size() == 1 && second.size() == 1) {
      matches.put(first.iterator().next(), second.iterator().next());
    } else {
      // Match by IP similarity
      List<String> intfs1;
      List<String> intfs2;
      boolean switchEntries = false;
      if (first.size() <= second.size()) {
        intfs1 = new ArrayList<>(first);
        intfs2 = new ArrayList<>(second);
      } else {
        intfs2 = new ArrayList<>(first);
        intfs1 = new ArrayList<>(second);
        Configuration temp = config1;
        config1 = config2;
        config2 = temp;
        switchEntries = true;
      }
      for (String intfString : intfs1) {
        long minDistance = Long.MAX_VALUE;
        String best = null;

        if (config1.getAllInterfaces().get(intfString).getConcreteAddress() == null) {
          continue;
        }
        Prefix prefix1 = config1.getAllInterfaces()
            .get(intfString)
            .getConcreteAddress()
            .getPrefix();

        for (String other : intfs2) {
          if (config2.getAllInterfaces().get(other).getConcreteAddress() == null) {
            continue;
          }
          Prefix prefix2 = config2.getAllInterfaces().get(other).getConcreteAddress().getPrefix();
          // Somewhat arbitrary metric
          int lengthDiff = Math.abs(prefix1.getPrefixLength() - prefix2.getPrefixLength());
          long startDiff = Math.abs(prefix1.getStartIp().asLong() - prefix2.getStartIp().asLong());
          long distance = 20 * lengthDiff + startDiff;
          if (distance < minDistance) {
            best = other;
            minDistance = distance;
          }
        }
        if (best != null) {
          if (switchEntries) {
            matches.put(best, intfString);
          } else {
            matches.put(intfString, best);
          }
          intfs2.remove(best);
        }
      }
    }
  }

  private RowBuilder makeRowFront(OspfInterfaceFeatures feat1, RowBuilder builder) {
    return builder.put(COL_NODE1, feat1.getNode())
        .put(COL_INTF1, feat1.getInterface().getName())
        .put(COL_IP1, feat1.getIp())
        .put(COL_AREA1, feat1.getArea())
        .put(COL_PEER1, feat1.getNeighbor())
        .put(COL_AREA_TYPE1, feat1.getAreaType())
        .put(COL_COST1, feat1.getCost())
        .put(COL_IS_PASSIVE1, feat1.isPassive())
        .put(COL_NETWORK_TYPE1, feat1.getNetworkType());
  }

  private RowBuilder makeEmptyRowFront(RowBuilder builder, String name) {
    return builder.put(COL_NODE1, name)
        .put(COL_INTF1, "None")
        .put(COL_IP1, "")
        .put(COL_PEER1, "")
        .put(COL_AREA1, "")
        .put(COL_AREA_TYPE1, "")
        .put(COL_COST1, "")
        .put(COL_IS_PASSIVE1, "")
        .put(COL_NETWORK_TYPE1, "");
  }

  private RowBuilder makeRowBack(OspfInterfaceFeatures feat2, RowBuilder builder) {
    return builder.put(COL_NODE2, feat2.getNode())
        .put(COL_INTF2, feat2.getInterface().getName())
        .put(COL_IP2, feat2.getIp())
        .put(COL_PEER2, feat2.getNeighbor())
        .put(COL_AREA2, feat2.getArea())
        .put(COL_AREA_TYPE2, feat2.getAreaType())
        .put(COL_COST2, feat2.getCost())
        .put(COL_IS_PASSIVE2, feat2.isPassive())
        .put(COL_NETWORK_TYPE2, feat2.getNetworkType());
  }

  private RowBuilder makeEmptyRowBack(RowBuilder builder, String name) {

    return builder.put(COL_NODE2, name)
        .put(COL_INTF2, "None")
        .put(COL_IP2, "")
        .put(COL_PEER2, "")
        .put(COL_AREA2, "")
        .put(COL_AREA_TYPE2, "")
        .put(COL_COST2, "")
        .put(COL_IS_PASSIVE2, "")
        .put(COL_NETWORK_TYPE2, "");
  }

  private Row makeRow(OspfInterfaceFeatures feat1, OspfInterfaceFeatures feat2, String name1,
      String name2) {
    RowBuilder builder = Row.builder();
    if (feat1 == null) {
      builder = makeEmptyRowFront(builder, name1);
    } else {
      builder = makeRowFront(feat1, builder);
    }
    if (feat2 == null) {
      builder = makeEmptyRowBack(builder, name2);
    } else {
      builder = makeRowBack(feat2, builder);
    }
    return builder.build();
  }

  private static TableMetadata metadata() {
    return new TableMetadata(ImmutableList.of(new ColumnMetadata(COL_NODE1,
            Schema.STRING,
            "Node1",
            true,
            false),
        new ColumnMetadata(COL_INTF1, Schema.STRING, "Interface1", true, false),
        new ColumnMetadata(COL_IP1, Schema.STRING, "Ip1", true, false),
        new ColumnMetadata(COL_PEER1, Schema.STRING, "Peer1", true, false),
        new ColumnMetadata(COL_AREA1, Schema.STRING, "Area1", false, true),
        new ColumnMetadata(COL_AREA_TYPE1, Schema.STRING, "AreaType1", false, true),
        new ColumnMetadata(COL_COST1, Schema.STRING, "Cost1", false, true),
        new ColumnMetadata(COL_IS_PASSIVE1, Schema.STRING, "Passive1", false, true),
        new ColumnMetadata(COL_NETWORK_TYPE1, Schema.STRING, "NetworkType1", false, true),
        new ColumnMetadata(COL_NODE2, Schema.STRING, "Node2", true, false),
        new ColumnMetadata(COL_INTF2, Schema.STRING, "Interface2", true, false),
        new ColumnMetadata(COL_IP2, Schema.STRING, "Ip2", true, false),
        new ColumnMetadata(COL_PEER2, Schema.STRING, "Peer2", true, false),
        new ColumnMetadata(COL_AREA2, Schema.STRING, "Area2", false, true),
        new ColumnMetadata(COL_AREA_TYPE2, Schema.STRING, "AreaType2", false, true),
        new ColumnMetadata(COL_COST2, Schema.STRING, "Cost2", false, true),
        new ColumnMetadata(COL_IS_PASSIVE2, Schema.STRING, "Passive2", false, true),
        new ColumnMetadata(COL_NETWORK_TYPE2, Schema.STRING, "NetworkType2", false, true)));
  }
}

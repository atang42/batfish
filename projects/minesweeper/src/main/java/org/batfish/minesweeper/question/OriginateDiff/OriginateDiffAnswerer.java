package org.batfish.minesweeper.question.OriginateDiff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.ConcreteInterfaceAddress;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.RoutingProtocol;
import org.batfish.datamodel.StaticRoute;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.ospf.OspfArea;
import org.batfish.datamodel.ospf.OspfProcess;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;
import org.parboiled.common.ImmutableList;

public class OriginateDiffAnswerer extends Answerer {

  private static final String COL_PREFIX = "Prefix";
  private static final String COL_PROTOCOL = "Protocol";

  private static final String COL_NODE1 = "Node1";
  private static final String COL_SOURCE1 = "Source1";

  private static final String COL_NODE2 = "Node2";
  private static final String COL_SOURCE2 = "Source2";


  @Nonnull private final NodeSpecifier _nodeSpecifier;

  public OriginateDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    OriginateDiffQuestion findQuestion = (OriginateDiffQuestion) question;
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

      for (Row row : ospfOriginateDiff(configurations.get(0), configurations.get(1))) {
        answerElement.addRow(row);
      }
      for (Row row : connectedOriginateDiff(configurations.get(0), configurations.get(1))) {
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
    for (Row row : ospfOriginateDiff(configurations.get(0), configurations.get(1))) {
      answerElement.addRow(row);
    }
    for (Row row : connectedOriginateDiff(configurations.get(0), configurations.get(1))) {
      answerElement.addRow(row);
    }
    for (Row row : staticOriginateDiff(configurations.get(0), configurations.get(1))) {
      answerElement.addRow(row);
    }

    return answerElement;
  }

  private Map<Prefix, String> getOspfPrefixes(Configuration config) {

    Map<Prefix, String> acc = new TreeMap<>();
    if (!config.getDefaultVrf().getOspfProcesses().isEmpty()) {
      OspfProcess ospf = config.getDefaultVrf().getOspfProcesses().values().iterator().next();
      for (OspfArea area : ospf.getAreas().values()) {
        for (String ifaceName : area.getInterfaces()) {
          Interface iface = config.getAllInterfaces().get(ifaceName);
          if (iface.getActive() && iface.getOspfEnabled()) {
            acc.put(iface.getConcreteAddress().getPrefix(), ifaceName);
          }
        }
      }
    }
    return acc;
  }

  private List<Row> ospfOriginateDiff(Configuration configs1, Configuration configs2) {
    Map<Prefix, String> prefixes1 = getOspfPrefixes(configs1);
    Map<Prefix, String> prefixes2 = getOspfPrefixes(configs2);

    Set<Prefix> onlyInFirst = new TreeSet<>(prefixes1.keySet());
    onlyInFirst.removeAll(prefixes2.keySet());

    Set<Prefix> onlyInSecond = new TreeSet<>(prefixes2.keySet());
    onlyInSecond.removeAll(prefixes1.keySet());

    List<Row> result = new ArrayList<>();
    for (Prefix pfx : onlyInFirst) {
      String source = "";
      if (prefixes1.get(pfx) != null) {
        source = "interface " + prefixes1.get(pfx);
      }
      result.add(makeRow(RoutingProtocol.OSPF, pfx, configs1.getHostname(), source, configs2.getHostname(), "None"));
    }
    for (Prefix pfx : onlyInSecond) {
      String source = "";
      if (prefixes2.get(pfx) != null) {
        source = "interface " + prefixes2.get(pfx);
      }
      result.add(makeRow(RoutingProtocol.OSPF, pfx,configs1.getHostname(), "None", configs2.getHostname(), source));
    }
    return result;
  }

  private Map<Prefix, String> getConnectedPrefixes(Configuration config) {
    Map<Prefix, String> acc = new TreeMap<>();
    for (Interface iface : config.getAllInterfaces().values()) {
      ConcreteInterfaceAddress address = iface.getConcreteAddress();
      if (address != null) {
        acc.put(address.getPrefix(), iface.getName());
      }
    }
    return acc;
  }
  private List<Row> connectedOriginateDiff(Configuration configs1, Configuration configs2) {
    Map<Prefix, String> prefixes1 = getConnectedPrefixes(configs1);
    Map<Prefix, String> prefixes2 = getConnectedPrefixes(configs2);

    Set<Prefix> onlyInFirst = new TreeSet<>(prefixes1.keySet());
    onlyInFirst.removeAll(prefixes2.keySet());

    Set<Prefix> onlyInSecond = new TreeSet<>(prefixes2.keySet());
    onlyInSecond.removeAll(prefixes1.keySet());

    List<Row> result = new ArrayList<>();
    for (Prefix pfx : onlyInFirst) {
      String source = "";
      if (prefixes1.get(pfx) != null) {
        source = "interface " + prefixes1.get(pfx);
      }
      result.add(makeRow(RoutingProtocol.CONNECTED, pfx, configs1.getHostname(), source, configs2.getHostname(), "None"));
    }
    for (Prefix pfx : onlyInSecond) {
      String source = "";
      if (prefixes2.get(pfx) != null) {
        source = "interface " + prefixes2.get(pfx);
      }
      result.add(makeRow(RoutingProtocol.CONNECTED, pfx,configs1.getHostname(), "None", configs2.getHostname(), source));
    }
    return result;
  }

  private static String combineString(@Nullable String a, @Nullable String b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + "\n" + b;
  }

  private Map<Prefix, String> getStaticPrefixes(Configuration config) {
    Map<Prefix, String> acc = new TreeMap<>();
    for (StaticRoute sr : config.getDefaultVrf().getStaticRoutes()) {
      Prefix prefix = sr.getNetwork();
      if (acc.containsKey(prefix)) {
        acc.put(prefix, combineString(acc.get(prefix), sr.getText()));
      } else {
        acc.put(prefix, sr.getText());
      }
    }
    return acc;
  }

  private List<Row> staticOriginateDiff(Configuration configs1, Configuration configs2) {
    Map<Prefix, String> prefixes1 = getStaticPrefixes(configs1);
    Map<Prefix, String> prefixes2 = getStaticPrefixes(configs2);

    Set<Prefix> onlyInFirst = new TreeSet<>(prefixes1.keySet());
    onlyInFirst.removeAll(prefixes2.keySet());

    Set<Prefix> onlyInSecond = new TreeSet<>(prefixes2.keySet());
    onlyInSecond.removeAll(prefixes1.keySet());

    List<Row> result = new ArrayList<>();
    for (Prefix pfx : onlyInFirst) {
      String source = "";
      if (prefixes1.get(pfx) != null) {
        source = prefixes1.get(pfx);
      }
      result.add(makeRow(RoutingProtocol.STATIC, pfx, configs1.getHostname(), source, configs2.getHostname(), "None"));
    }
    for (Prefix pfx : onlyInSecond) {
      String source = "";
      if (prefixes2.get(pfx) != null) {
        source = prefixes2.get(pfx);
      }
      result.add(makeRow(RoutingProtocol.STATIC, pfx,configs1.getHostname(), "None", configs2.getHostname(), source));
    }
    return result;
  }

  private Row makeRow(RoutingProtocol protocol, Prefix prefix, String node1, String source1, String node2, String source2) {
    return Row.builder()
        .put(COL_PROTOCOL, protocol.toString())
        .put(COL_PREFIX, prefix)
        .put(COL_NODE1, node1)
        .put(COL_SOURCE1, source1)
        .put(COL_NODE2, node2)
        .put(COL_SOURCE2, source2)
        .build();
  }

  private static TableMetadata metadata() {
    return new TableMetadata(ImmutableList.of(
        new ColumnMetadata(COL_PROTOCOL, Schema.STRING, "Protocol", true, false),
        new ColumnMetadata(COL_PREFIX, Schema.STRING, "Prefix", true, false),
        new ColumnMetadata(COL_NODE1, Schema.STRING, "Node1", true, false),
        new ColumnMetadata(COL_SOURCE1, Schema.STRING, "Source1", true, false),
        new ColumnMetadata(COL_NODE2, Schema.STRING, "Node2", true, false),
        new ColumnMetadata(COL_SOURCE2, Schema.STRING, "Source2", true, false)
    ));
  }
}

package org.batfish.minesweeper.question.BgpEdgeDiff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.BgpPeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.Row.RowBuilder;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;
import org.parboiled.common.ImmutableList;

public class BgpEdgeDiffAnswerer extends Answerer {

  private static final String COL_NODE1 = "Node1";
  private static final String COL_IP1 = "Peer1";
  private static final String COL_LOCAL_AS1 = "LocalAs1";
  private static final String COL_RRC1 = "RouteReflectorClient1";
  private static final String COL_TIEBREAKER1 = "TieBreaker1";
  private static final String COL_CAPABILITIES1 = "Capabilities1";

  private static final String COL_NODE2 = "Node2";
  private static final String COL_IP2 = "Peer2";
  private static final String COL_LOCAL_AS2 = "LocalAs2";
  private static final String COL_RRC2 = "RouteReflectorClient2";
  private static final String COL_TIEBREAKER2 = "TieBreaker2";
  private static final String COL_CAPABILITIES2 = "Capabilities2";

  @Nonnull private final NodeSpecifier _nodeSpecifier;

  public BgpEdgeDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    BgpEdgeDiffQuestion findQuestion = (BgpEdgeDiffQuestion) question;
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

      BgpProcess bgpConfigs1 = configurations.get(0).getDefaultVrf().getBgpProcess();
      BgpProcess bgpConfigs2 = configurations.get(1).getDefaultVrf().getBgpProcess();

      TableAnswerElement answer = new TableAnswerElement(metadata());
      for (Row row : bgpEdgeDiff(configurations.get(0).getHostname(), bgpConfigs1, configurations.get(1).getHostname(), bgpConfigs2)) {
        answer.addRow(row);
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

    BgpProcess bgpConfigs1 = configurations.get(0).getDefaultVrf().getBgpProcess();
    BgpProcess bgpConfigs2 = configurations.get(1).getDefaultVrf().getBgpProcess();

    TableAnswerElement answer = new TableAnswerElement(metadata());
    for (Row row : bgpEdgeDiff(configurations.get(0).getHostname(), bgpConfigs1, configurations.get(1).getHostname(), bgpConfigs2)) {
      answer.addRow(row);
    }

    return answer;
  }

  private Map<Ip, BgpPeerConfig> makeEdgeMap(List<BgpPeerConfig> bgpConfigs) {
    return bgpConfigs.stream().collect(Collectors.toMap(x -> x.getLocalIp(), Function.identity()));
  }

  private List<Row> bgpEdgeDiff(String name1, BgpProcess bgpConfigs1, String name2, BgpProcess bgpConfigs2) {

    if (bgpConfigs1 == null && bgpConfigs2 == null) {
      return Collections.emptyList();
    }

    SortedMap<Prefix, BgpActivePeerConfig> ipToBgp1 =
        bgpConfigs1 != null ? bgpConfigs1.getActiveNeighbors() : Collections.emptySortedMap();
    SortedMap<Prefix, BgpActivePeerConfig> ipToBgp2 =
        bgpConfigs2 != null ? bgpConfigs2.getActiveNeighbors() : Collections.emptySortedMap();

    if (!bgpConfigs1.getPassiveNeighbors().isEmpty() || !bgpConfigs1.getPassiveNeighbors().isEmpty()) {
      System.out.println("Passive Neighbors not empty");
    }
    if (!bgpConfigs1.getInterfaceNeighbors().isEmpty() || !bgpConfigs2.getInterfaceNeighbors().isEmpty()) {
      System.out.println("Interface Neighbors not empty");
    }

    Set<Prefix> peerIps = new TreeSet<>(ipToBgp1.keySet());
    peerIps.addAll(ipToBgp2.keySet());

    List<Row> result = new ArrayList<>();
    for (Prefix peer : peerIps) {
      BgpActivePeerConfig peerConfig1 = ipToBgp1.get(peer);
      BgpActivePeerConfig peerConfig2 = ipToBgp2.get(peer);

      BgpEdgeFeatures features1 = peerConfig1 == null ? null : new BgpEdgeFeatures(bgpConfigs1, peerConfig1);
      BgpEdgeFeatures features2 = peerConfig2 == null ? null : new BgpEdgeFeatures(bgpConfigs2, peerConfig2);

      if (features1 == null && features2 == null) {
      } else if (features1 == null || features2 == null) {
        result.add(makeRow(name1, features1, name2, features2));
      } else {

        if (!features1.equalAttributes(features2)) {
          result.add(makeRow(name1, features1, name2, features2));
        }
      }

    }

    return result;
  }

  private RowBuilder makeRowFront(String name1, BgpEdgeFeatures feat1, RowBuilder builder) {
    return builder.put(COL_NODE1, name1)
        .put(COL_IP1, feat1.getIp())
        .put(COL_LOCAL_AS1, feat1.getLocalAs())
        .put(COL_RRC1, feat1.isRouteReflectorClient())
        .put(COL_TIEBREAKER1, feat1.getTieBreaker())
        .put(COL_CAPABILITIES1, feat1.getCapabilitiesString());
  }

  private RowBuilder makeEmptyRowFront(RowBuilder builder, String name) {
    return builder.put(COL_NODE1, name)
        .put(COL_IP1, "None")
        .put(COL_LOCAL_AS1, "")
        .put(COL_RRC1, "")
        .put(COL_TIEBREAKER1, "")
        .put(COL_CAPABILITIES1, "");
  }

  private RowBuilder makeRowBack(String name2, BgpEdgeFeatures feat2, RowBuilder builder) {
    return builder.put(COL_NODE2, name2)
        .put(COL_IP2, feat2.getIp())
        .put(COL_LOCAL_AS2, feat2.getLocalAs())
        .put(COL_RRC2, feat2.isRouteReflectorClient())
        .put(COL_TIEBREAKER2, feat2.getTieBreaker())
        .put(COL_CAPABILITIES2, feat2.getCapabilitiesString());
  }

  private RowBuilder makeEmptyRowBack(RowBuilder builder, String name) {
    return builder.put(COL_NODE2, name)
        .put(COL_IP2, "None")
        .put(COL_LOCAL_AS2, "")
        .put(COL_RRC2, "")
        .put(COL_TIEBREAKER2, "")
        .put(COL_CAPABILITIES2, "");
  }

  private Row makeRow(String name1, BgpEdgeFeatures feat1, String name2, BgpEdgeFeatures feat2) {
    RowBuilder builder = Row.builder();
    if (feat1 == null) {
      builder = makeEmptyRowFront(builder, name1);
    } else {
      builder = makeRowFront(name1, feat1, builder);
    }
    if (feat2 == null) {
      builder = makeEmptyRowBack(builder, name2);
    } else {
      builder = makeRowBack(name2, feat2, builder);
    }
    return builder.build();
  }

  private static TableMetadata metadata() {
    return new TableMetadata(ImmutableList.of(
        new ColumnMetadata(COL_NODE1, Schema.STRING, "Node1", true, false),
        new ColumnMetadata(COL_IP1, Schema.STRING, "Peer1", true, false),
        new ColumnMetadata(COL_LOCAL_AS1, Schema.STRING, "LocalAs1", false, true),
        new ColumnMetadata(COL_RRC1, Schema.STRING, "IsRouteReflectorClient1", false, true),
        new ColumnMetadata(COL_CAPABILITIES1, Schema.STRING, "Capabilities1", false, true),
        new ColumnMetadata(COL_TIEBREAKER1, Schema.STRING, "TieBreaker1", false, true),
        new ColumnMetadata(COL_NODE2, Schema.STRING, "Node2", false, true),
        new ColumnMetadata(COL_IP2, Schema.STRING, "Peer2", false, true),
        new ColumnMetadata(COL_LOCAL_AS2, Schema.STRING, "LocalAs2", false, true),
        new ColumnMetadata(COL_RRC2, Schema.STRING, "IsRouteReflectorClient2", false, true),
        new ColumnMetadata(COL_CAPABILITIES2, Schema.STRING, "Capabilities2", false, true),
        new ColumnMetadata(COL_TIEBREAKER2, Schema.STRING, "TieBreaker2", false, true)
    ));
  }
}

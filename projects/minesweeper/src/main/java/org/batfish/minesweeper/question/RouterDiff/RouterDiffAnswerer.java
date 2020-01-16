package org.batfish.minesweeper.question.RouterDiff;

import static org.batfish.datamodel.table.TableMetadata.toColumnMap;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;
import org.apache.commons.lang3.ObjectUtils;
import org.batfish.common.Answerer;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.bgp.Ipv4UnicastAddressFamily;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.Row.TypedRowBuilder;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
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
import org.batfish.minesweeper.policylocalize.RoutePolicyDiff;
import org.batfish.minesweeper.policylocalize.RoutePolicyNamesExtractor;
import org.batfish.minesweeper.policylocalize.RouteToBDD;
import org.batfish.minesweeper.policylocalize.SymbolicResult;
import org.batfish.representation.cisco.RoutePolicy;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class RouterDiffAnswerer extends Answerer {

  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final List<PrefixRange> _ignoredPrefixRanges;

  public static final String COL_NEIGHBOR = "Neighbor";
  public static final String COL_ROUTE_PREFIX = "Prefix";
  public static final String COL_ROUTE_COMM = "Community";
  public static final String COL_NODE1 = "Node1";
  public static final String COL_FILTER1 = "Filter1";
  public static final String COL_TEXT1 = "Text1";
  public static final String COL_ACTION1 = "Action1";
  public static final String COL_NODE2 = "Node2";
  public static final String COL_FILTER2 = "Filter2";
  public static final String COL_TEXT2 = "Text2";
  public static final String COL_ACTION2 = "Action2";

  public static final List<ColumnMetadata> COLUMN_METADATA =
      ImmutableList.of(
          new ColumnMetadata(COL_NEIGHBOR, Schema.STRING, "Neighbor IP", true, false),
          new ColumnMetadata(COL_ROUTE_PREFIX, Schema.STRING, "Prefix", true, false),
          new ColumnMetadata(COL_ROUTE_COMM, Schema.STRING, "Community", true, false),
          new ColumnMetadata(COL_NODE1, Schema.STRING, "Node", true, false),
          new ColumnMetadata(COL_FILTER1, Schema.STRING, "Filter name", true, false),
          new ColumnMetadata(COL_TEXT1, Schema.STRING, "Line text", true, false),
          new ColumnMetadata(
              COL_ACTION1,
              Schema.STRING,
              "Action performed by the line (e.g., PERMIT or DENY)",
              true,
              false),
          new ColumnMetadata(COL_NODE2, Schema.STRING, "Node", true, false),
          new ColumnMetadata(COL_FILTER2, Schema.STRING, "Filter name", true, false),
          new ColumnMetadata(COL_TEXT2, Schema.STRING, "Line text", true, false),
          new ColumnMetadata(
              COL_ACTION2,
              Schema.STRING,
              "Action performed by the line (e.g., PERMIT or DENY)",
              true,
              false));

  private static final Map<String, ColumnMetadata> METADATA_MAP = toColumnMap(COLUMN_METADATA);

  public RouterDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    RouterDiffQuestion findQuestion = (RouterDiffQuestion) question;
    _nodeSpecifier = findQuestion.getNodeSpecifier();
    _ignoredPrefixRanges = findQuestion.getIgnoredPrefixRanges();
  }

  @Override
  public TableAnswerElement answer() {
    RoutePolicyDiff diff = new RoutePolicyDiff(_batfish, _nodeSpecifier, _ignoredPrefixRanges);
    return diff.answer();
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
                System.out.println(
                    "\t"
                        + route.getNetwork()
                        + " "
                        + route.getAdministrativeCost()
                        + " "
                        + route.getMetric()
                        + " "
                        + route.getTag()
                        + " "
                        + route.getNextHopInterface()
                        + " "
                        + route.getNextHopIp()
                        + " "
                        + route.getProtocol()
                        + " '"
                        + route.getText()
                        + "'");
              });
    }
  }
}

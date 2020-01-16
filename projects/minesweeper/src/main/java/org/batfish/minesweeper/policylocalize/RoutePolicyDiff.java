package org.batfish.minesweeper.policylocalize;

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
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.bgp.Ipv4UnicastAddressFamily;
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
import org.batfish.minesweeper.TransferResult;
import org.batfish.minesweeper.bdd.BDDRoute;
import org.batfish.minesweeper.bdd.PolicyQuotient;
import org.batfish.minesweeper.bdd.TransferBDD;
import org.batfish.minesweeper.bdd.TransferReturn;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class RoutePolicyDiff {

  private static final String COL_NEIGHBOR = "Neighbor";
  private static final String COL_ROUTE_PREFIX = "Prefix";
  private static final String COL_ROUTE_COMM = "Community";
  private static final String COL_NODE1 = "Node1";
  private static final String COL_FILTER1 = "Filter1";
  private static final String COL_TEXT1 = "Text1";
  private static final String COL_ACTION1 = "Action1";
  private static final String COL_NODE2 = "Node2";
  private static final String COL_FILTER2 = "Filter2";
  private static final String COL_TEXT2 = "Text2";
  private static final String COL_ACTION2 = "Action2";
  private static final String COL_BDD_BITS = "BDDBits";

  private static final List<ColumnMetadata> COLUMN_METADATA =
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
              false),
          new ColumnMetadata(COL_BDD_BITS, Schema.STRING, "BDD bits", true, false));

  private static final Map<String, ColumnMetadata> METADATA_MAP = TableMetadata.toColumnMap(COLUMN_METADATA);

  private final IBatfish _batfish;
  private final NodeSpecifier _nodeSpecifier;
  private final Graph _graph;
  private final BDDRoute _record;
  private final Configuration _config1;
  private final Configuration _config2;
  private final PolicyQuotient _policyQuotient;
  private final List<PrefixRange> _ignoredPrefixRanges;

  public RoutePolicyDiff(IBatfish batfish, NodeSpecifier specifier, List<PrefixRange> ranges) {
    _batfish = batfish;
    _nodeSpecifier = specifier;
    _ignoredPrefixRanges = ranges;

    _graph = new Graph(_batfish);
    SpecifierContext specifierContext = _batfish.specifierContext();
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);

    if (nodeSet.size() < 2) {
      System.err.println("Fewer than 2 specified nodes: ");
      nodeSet.forEach(System.out::println);
      throw new IllegalArgumentException(
          "Fewer than 2 specified nodes: "
              + nodeSet.stream().reduce((a, b) -> a + "\n" + b).orElse(""));
    } else if (nodeSet.size() > 2) {
      System.err.println("More than 2 specified nodes: ");
      nodeSet.forEach(System.out::println);
      throw new IllegalArgumentException(
          "More than 2 specified nodes: "
              + nodeSet.stream().reduce((a, b) -> a + "\n" + b).orElse(""));
    }

    Set<CommunityVar> comms = _graph.getAllCommunities();
    _record = new BDDRoute(comms);
    _policyQuotient = new PolicyQuotient();

    List<Configuration> _configurations = nodeSet.stream()
        .map(specifierContext.getConfigs()::get)
        .collect(Collectors.toList());
    _config1 = _configurations.get(0);
    _config2 = _configurations.get(1);
  }

  public TableAnswerElement answer() {
    TableAnswerElement answerElement = new TableAnswerElement(new TableMetadata(COLUMN_METADATA));
    getBGPDiff().forEach(answerElement::addRow);
    return answerElement;
  }

  public List<Row> getBGPDiff() {
    List<Row> resultRows = new ArrayList<>();

    SortedMap<Prefix, BgpActivePeerConfig> neighbors1 =
        _config1.getDefaultVrf().getBgpProcess().getActiveNeighbors();
    SortedMap<Prefix, BgpActivePeerConfig> neighbors2 =
        _config2.getDefaultVrf().getBgpProcess().getActiveNeighbors();

    Set<Prefix> neighborPrefixes = new TreeSet<>(neighbors1.keySet());
    neighborPrefixes.addAll(neighbors2.keySet());
    for (Prefix neighbor : neighborPrefixes) {
      resultRows.addAll(getRowsForNeighbor(neighbor, neighbors1, neighbors2));
    }
    return resultRows;
  }

  private void printPolicyNames(
      RoutePolicyNamesExtractor extractor,
      RoutingPolicy r1Import,
      RoutingPolicy r2Import,
      RoutingPolicy r1Export,
      RoutingPolicy r2Export) {
    Optional.ofNullable(r1Import).ifPresent(p -> System.out.println("\tIMPORT1 " + p.getName()));
    extractor
        .extractCalledPolicies(r1Import, _config1)
        .forEach(p -> System.out.println("\t\t" + p));
    Optional.ofNullable(r2Import).ifPresent(p -> System.out.println("\tIMPORT2 " + p.getName()));
    extractor
        .extractCalledPolicies(r2Import, _config2)
        .forEach(p -> System.out.println("\t\t" + p));
    Optional.ofNullable(r1Export).ifPresent(p -> System.out.println("\tEXPORT1 " + p.getName()));
    extractor
        .extractCalledPolicies(r1Export, _config1)
        .forEach(p -> System.out.println("\t\t" + p));
    Optional.ofNullable(r2Export).ifPresent(p -> System.out.println("\tEXPORT2 " + p.getName()));
    extractor
        .extractCalledPolicies(r2Export, _config2)
        .forEach(p -> System.out.println("\t\t" + p));
  }

  private List<Row> getRowsForNeighbor(
      Prefix neighbor,
      SortedMap<Prefix, BgpActivePeerConfig> neighbors1,
      SortedMap<Prefix, BgpActivePeerConfig> neighbors2) {
    List<Row> resultRows = new ArrayList<>();
    if (!neighbors1.keySet().contains(neighbor) || !neighbors2.keySet().contains(neighbor)) {
      resultRows.add(createUnequalNeighborRow(neighbors1, neighbors2, neighbor));
      return resultRows;
    }
    BgpActivePeerConfig bgpConfig1 = neighbors1.get(neighbor);
    BgpActivePeerConfig bgpConfig2 = neighbors2.get(neighbor);

    String r1ImportName =
        Optional.ofNullable(bgpConfig1)
            .map(BgpActivePeerConfig::getIpv4UnicastAddressFamily)
            .map(Ipv4UnicastAddressFamily::getImportPolicy)
            .orElse(null);
    String r1ExportName =
        Optional.ofNullable(bgpConfig1)
            .map(BgpActivePeerConfig::getIpv4UnicastAddressFamily)
            .map(Ipv4UnicastAddressFamily::getExportPolicy)
            .orElse(null);
    String r2ImportName =
        Optional.ofNullable(bgpConfig2)
            .map(BgpActivePeerConfig::getIpv4UnicastAddressFamily)
            .map(Ipv4UnicastAddressFamily::getImportPolicy)
            .orElse(null);
    String r2ExportName =
        Optional.ofNullable(bgpConfig2)
            .map(BgpActivePeerConfig::getIpv4UnicastAddressFamily)
            .map(Ipv4UnicastAddressFamily::getExportPolicy)
            .orElse(null);

    RoutingPolicy r1Import = _config1.getRoutingPolicies().getOrDefault(r1ImportName, null);
    RoutingPolicy r1Export = _config1.getRoutingPolicies().getOrDefault(r1ExportName, null);
    RoutingPolicy r2Import = _config2.getRoutingPolicies().getOrDefault(r2ImportName, null);
    RoutingPolicy r2Export = _config2.getRoutingPolicies().getOrDefault(r2ExportName, null);

    RoutePolicyNamesExtractor extractor = new RoutePolicyNamesExtractor();
    System.out.println("TO NEIGHBOR " + neighbor);
    printPolicyNames(extractor, r1Import, r2Import, r1Export, r2Export);

    if (r1Import == null && r2Import == null) {
      System.out.println("NO IMPORT POLICIES");
    } else {
      // Compare import policies
      r1Import = ObjectUtils.defaultIfNull(r1Import, getDefaultImportPolicy(_config1));
      r2Import = ObjectUtils.defaultIfNull(r2Import, getDefaultImportPolicy(_config2));
      if (extractor.hasNonDefaultPolicy(r1Import, _config1)
          || extractor.hasNonDefaultPolicy(r2Import, _config2)) {
        System.out.println("IMPORT POLICIES:");
        resultRows.addAll(
            computeRoutePolicyDiff(neighbor.getStartIp().toString(), r1Import, r2Import));
      } else {
        System.out.println("ONLY DEFAULT IMPORT POLICIES");
      }
    }

    if (r1Export == null && r2Export == null) {
      System.out.println("NO EXPORT POLICIES");
    } else {
      // Compare export policies
      r1Export = ObjectUtils.defaultIfNull(r1Export, getDefaultExportPolicy(_config1));
      r2Export = ObjectUtils.defaultIfNull(r2Export, getDefaultExportPolicy(_config2));

      if (extractor.hasNonDefaultPolicy(r1Export, _config1)
          || extractor.hasNonDefaultPolicy(r2Export, _config2)) {
        System.out.println("EXPORT POLICIES:");
        resultRows.addAll(
            computeRoutePolicyDiff(neighbor.getStartIp().toString(), r1Export, r2Export));
      } else {
        System.out.println("ONLY DEFAULT EXPORT POLICIES");
      }
    }
    return resultRows;
  }

  private Row createUnequalNeighborRow(
      SortedMap<Prefix, BgpActivePeerConfig> neighbors1,
      SortedMap<Prefix, BgpActivePeerConfig> neighbors2,
      Prefix neighbor) {

    String n1Present = neighbors1.keySet().contains(neighbor) ? "PRESENT" : "ABSENT";
    String n2Present = neighbors2.keySet().contains(neighbor) ? "PRESENT" : "ABSENT";
    TypedRowBuilder builder =
        Row.builder(METADATA_MAP)
            .put(COL_NEIGHBOR, neighbor.getStartIp().toString())
            .put(COL_NODE1, _config1.getHostname())
            .put(COL_NODE2, _config2.getHostname())
            .put(COL_FILTER1, "")
            .put(COL_FILTER2, "")
            .put(COL_ROUTE_PREFIX, "")
            .put(COL_ROUTE_COMM, "")
            .put(COL_TEXT1, n1Present)
            .put(COL_TEXT2, n2Present)
            .put(COL_ACTION1, "")
            .put(COL_ACTION2, "");
    return builder.build();
  }

  private List<Row> computeRoutePolicyDiff(
      String neighbor, RoutingPolicy r1Policy, RoutingPolicy r2Policy) {

    List<Row> ret = new ArrayList<>();

    TransferBDD transferBDD1 =
        new TransferBDD(_graph, _config1, r1Policy.getStatements(), _policyQuotient);
    BDDRoute route1 = _record.deepCopy();
    TransferResult<TransferReturn, BDD> transferResult1 =
        transferBDD1.compute(new TreeSet<>(), route1);
    BDD accepted1 = transferResult1.getReturnValue().getSecond();

    TransferBDD transferBDD2 =
        new TransferBDD(_graph, _config2, r2Policy.getStatements(), _policyQuotient);
    BDDRoute route2 = _record.deepCopy();
    TransferResult<TransferReturn, BDD> transferResult2 =
        transferBDD2.compute(new TreeSet<>(), route2);
    BDD accepted2 = transferResult2.getReturnValue().getSecond();

    BDDPolicyActionMap actionMap1 = transferBDD1.getBDDPolicyActionMap();
    BDDPolicyActionMap actionMap2 = transferBDD2.getBDDPolicyActionMap();

    accepted1 = actionMap1.mapToEncodedBDD(_record);

    accepted2 = actionMap2.mapToEncodedBDD(_record);

    BDD difference = accepted1.and(accepted2.not()).or(accepted1.not().and(accepted2));
    BDD ignored = new RouteToBDD(_record).buildPrefixRangesBDD(_ignoredPrefixRanges);
    difference = difference.and(ignored.not());
    System.out.println("HAS DIFF: " + !difference.isZero());
    if (!difference.isZero()) {
      List<BDD> bddList =
          getBDDEquivalenceClasses(difference, actionMap1.getBDDKeys(), actionMap2.getBDDKeys());
      ret.addAll(
          createRowFromDiff(neighbor, bddList, r1Policy, transferBDD1, r2Policy, transferBDD2));
      printStatements(_config1, transferBDD1, r1Policy, difference);
      printStatements(_config2, transferBDD2, r2Policy, difference);
      System.out.println();
    }
    return ret;
  }

  /*
  Returns a list of BDDs, each representing a set that is contained in difference and matches
  different lines in the BDD set
   */
  private List<BDD> getBDDEquivalenceClasses(BDD difference, Set<BDD> bdds1, Set<BDD> bdds2) {
    List<BDD> ret = new ArrayList<>();
    Set<BDD> bddSet1 =
        bdds1
            .stream()
            .map(difference::and)
            .filter(bdd -> !bdd.isZero())
            .collect(Collectors.toSet());
    Set<BDD> bddSet2 =
        bdds2
            .stream()
            .map(difference::and)
            .filter(bdd -> !bdd.isZero())
            .collect(Collectors.toSet());
    for (BDD bdd1 : bddSet1) {
      for (BDD bdd2 : bddSet2) {
        BDD temp = bdd2.and(bdd1);
        if (!temp.isZero()) {
          ret.add(temp);
        }
      }
    }
    return ret;
  }

  @Nullable
  private RoutingPolicy getDefaultImportPolicy(Configuration conf) {
    for (Map.Entry<String, RoutingPolicy> entry : conf.getRoutingPolicies().entrySet()) {
      String name = entry.getKey();
      if (name.contains("~BGP_COMMON_IMPORT_POLICY~")
          || name.contains("~DEFAULT_BGP_IMPORT_POLICY~")) {
        return entry.getValue();
      }
    }
    return RoutingPolicy.builder()
        .setName("NO ROUTE POLICY")
        .addStatement(Statements.ExitAccept.toStaticStatement())
        .build();
  }

  @Nullable
  private RoutingPolicy getDefaultExportPolicy(Configuration conf) {
    for (Map.Entry<String, RoutingPolicy> entry : conf.getRoutingPolicies().entrySet()) {
      String name = entry.getKey();
      if (name.contains("~BGP_COMMON_EXPORT_POLICY")
          || name.contains("~DEFAULT_BGP_EXPORT_POLICY")) {
        return entry.getValue();
      }
    }
    return RoutingPolicy.builder()
        .setName("NO ROUTE POLICY")
        .addStatement(Statements.ReturnTrue.toStaticStatement())
        .build();
  }

  private List<Row> createRowFromDiff(
      String neighbor,
      List<BDD> diffs,
      RoutingPolicy policy1,
      TransferBDD transferBDD1,
      RoutingPolicy policy2,
      TransferBDD transferBDD2) {

    RoutePolicyNamesExtractor extractor = new RoutePolicyNamesExtractor();
    // Policy Names
    String policy1Names = extractor.getCombinedCalledPoliciesWithoutGenerated(policy1, _config1);
    String policy2Names = extractor.getCombinedCalledPoliciesWithoutGenerated(policy2, _config2);

    List<Row> ret = new ArrayList<>();

    for (BDD bdd : diffs) {
      BDD example = bdd.fullSatOne();
      // Prefix
      Long prefix = _record.getPrefix().getValueSatisfying(example).get();
      Long prefixLen = _record.getPrefixLength().getValueSatisfying(example).get();
      String prefixStr = Prefix.create(Ip.create(prefix), Math.toIntExact(prefixLen)).toString();

      // Communities
      StringBuilder commString = new StringBuilder();
      for (Entry<CommunityVar, BDD> commEntry : _record.getCommunities().entrySet()) {
        CommunityVar commVar = commEntry.getKey();
        BDD commBDD = commEntry.getValue();
        if (!commBDD.and(example).isZero()) {
          commString.append(commVar.getRegex()).append(" ");
        }
      }

      // Texts
      String stmtText1 = transferBDD1.getBDDPolicyActionMap().getCombinedStatementTexts(bdd);
      String stmtText2 = transferBDD2.getBDDPolicyActionMap().getCombinedStatementTexts(bdd);
      if (stmtText1.length() == 0) {
        stmtText1 = "DEFAULT BEHAVIOR";
      }
      if (stmtText2.length() == 0) {
        stmtText2 = "DEFAULT BEHAVIOR";
      }

      // Action
      String action1 = transferBDD1.getBDDPolicyActionMap().getAction(bdd).toString();
      String action2 = transferBDD2.getBDDPolicyActionMap().getAction(bdd).toString();

      action1 += "\n" + transferBDD1.getAccepted(bdd);
      action2 += "\n" + transferBDD2.getAccepted(bdd);

      TypedRowBuilder builder =
          Row.builder(METADATA_MAP)
              .put(COL_NEIGHBOR, neighbor)
              .put(COL_NODE1, _config1.getHostname())
              .put(COL_NODE2, _config2.getHostname())
              .put(COL_FILTER1, policy1Names)
              .put(COL_FILTER2, policy2Names)
              .put(COL_ROUTE_PREFIX, prefixStr)
              .put(COL_ROUTE_COMM, commString.toString())
              .put(COL_TEXT1, stmtText1)
              .put(COL_TEXT2, stmtText2)
              .put(COL_ACTION1, action1)
              .put(COL_ACTION2, action2)
              .put(COL_BDD_BITS, fullSatBDDToString(example));
      ret.add(builder.build());
    }
    return ret;
  }

  private void printStatements(
      Configuration config, TransferBDD transferBDD, RoutingPolicy policy, BDD space) {
    Map<BDD, List<Statement>> stmtmap = transferBDD.getBDDPolicyActionMap().getStatementMap();

    String joined =
        String.join(
            " ",
            new RoutePolicyNamesExtractor().extractCalledPoliciesWithoutGenerated(policy, config));
    if (joined.isEmpty()) {
      joined = "DEFAULT POLICY";
    }
    System.out.println(config.getHostname() + " " + joined);
    System.out.println();
    System.out.println("EXAMPLES");
    for (Entry<BDD, List<Statement>> entry : stmtmap.entrySet()) {
      BDD routeSubspace = entry.getKey().and(space);
      if (routeSubspace.isZero()) {
        continue;
      }
      List<Statement> statements = entry.getValue();

      System.out.println("----------------------------------");
      BDD example = routeSubspace.fullSatOne();
      Long prefix = _record.getPrefix().getValueSatisfying(example).orElse(-1L);
      Long prefixLen = _record.getPrefixLength().getValueSatisfying(example).orElse(-1L);
      System.out.println("PREFIX: " + Prefix.create(Ip.create(prefix), Math.toIntExact(prefixLen)));
      for (Entry<CommunityVar, BDD> commEntry : _record.getCommunities().entrySet()) {
        CommunityVar commVar = commEntry.getKey();
        BDD commBDD = commEntry.getValue();
        if (!commBDD.and(example).isZero()) {
          System.out.print(commVar.getRegex() + " ");
        }
      }
      System.out.println();
      statements
          .stream()
          .filter(stmt -> stmt.getText() != null)
          .forEach(stmt -> System.out.println(stmt.getText()));
      System.out.println(transferBDD.getAccepted(routeSubspace));
    }
    System.out.println("----------------------------------");
  }

  private static String fullSatBDDToString(BDD bdd) {
    BDDFactory factory = BDDRoute.getFactory();
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < factory.varNum(); i++) {
      if (bdd.and(factory.ithVar(i)).isZero()) {
        builder.append("0");
      } else {
        builder.append("1");
      }
      if (i % 16 == 15) {
        builder.append(" ").append(i+1).append("\n");
      }
    }
    return builder.toString();
  }

}

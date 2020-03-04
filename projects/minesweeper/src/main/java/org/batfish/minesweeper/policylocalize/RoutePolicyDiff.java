package org.batfish.minesweeper.policylocalize;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
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
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.bgp.Ipv4UnicastAddressFamily;
import org.batfish.datamodel.ospf.OspfProcess;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.Row.TypedRowBuilder;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.CommunityVar;
import org.batfish.minesweeper.Graph;
import org.batfish.minesweeper.Protocol;
import org.batfish.minesweeper.TransferResult;
import org.batfish.minesweeper.bdd.BDDRoute;
import org.batfish.minesweeper.bdd.PolicyQuotient;
import org.batfish.minesweeper.bdd.TransferBDD;
import org.batfish.minesweeper.bdd.TransferReturn;
import org.batfish.minesweeper.policylocalize.resultrepr.IncludedExcludedPrefixRanges;
import org.batfish.minesweeper.policylocalize.resultrepr.PrefixExtractor;

public class RoutePolicyDiff {

  private static final String COL_NEIGHBOR = "Neighbor";
  private static final String COL_ROUTE_INCLUDED_PREFIXES = "Included_Prefixes";
  private static final String COL_ROUTE_EXCLUDED_PREFIXES = "Excluded_Prefixes";
  private static final String COL_ROUTE_COMM = "Community";
  private static final String COL_ROUTE_PROTOCOL = "Protocol";
  private static final String COL_NODE1 = "Node1";
  private static final String COL_FILTER1 = "Filter1";
  private static final String COL_TEXT1 = "Text1";
  private static final String COL_LINES1 = "LINES1";
  private static final String COL_ACTION1 = "Action1";
  private static final String COL_NODE2 = "Node2";
  private static final String COL_FILTER2 = "Filter2";
  private static final String COL_TEXT2 = "Text2";
  private static final String COL_LINES2 = "LINES2";
  private static final String COL_ACTION2 = "Action2";
  private static final String COL_BDD_BITS = "BDDBits";

  public static final List<ColumnMetadata> COLUMN_METADATA =
      ImmutableList.of(
          new ColumnMetadata(COL_NEIGHBOR, Schema.STRING, "Neighbor IP", true, false),
          new ColumnMetadata(COL_ROUTE_INCLUDED_PREFIXES, Schema.STRING, "Included Prefixes", true, false),
          new ColumnMetadata(COL_ROUTE_EXCLUDED_PREFIXES, Schema.STRING, "Excluded Prefixes", true, false),
          new ColumnMetadata(COL_ROUTE_COMM, Schema.STRING, "Community", true, false),
          new ColumnMetadata(COL_ROUTE_PROTOCOL, Schema.STRING, "Protocol", true, false),
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

  private static final Map<String, ColumnMetadata> METADATA_MAP =
      TableMetadata.toColumnMap(COLUMN_METADATA);

  private final Graph _graph;
  private final BDDRoute _record;
  private final Configuration _config1;
  private final Configuration _config2;
  private final String _routerName1;
  private final String _routerName2;
  private final PolicyQuotient _policyQuotient;
  private final List<PrefixRange> _ignoredPrefixRanges;
  private final PrefixExtractor _prefixExtractor;

  public RoutePolicyDiff(
      String router1,
      String router2,
      IBatfish batfish,
      List<Configuration> configurations,
      List<PrefixRange> ranges) {
    _ignoredPrefixRanges = ranges;

    _graph = new Graph(batfish);
    Set<CommunityVar> comms = _graph.getAllCommunities();
    _record = new BDDRoute(comms);
    _policyQuotient = new PolicyQuotient();

    _config1 = configurations.get(0);
    _config2 = configurations.get(1);

    _routerName1 = router1;
    _routerName2 = router2;

    Set<RouteFilterList> filterLists = new HashSet<>(_config1.getRouteFilterLists().values());
    filterLists.addAll(_config2.getRouteFilterLists().values());

    _prefixExtractor = new PrefixExtractor(filterLists, _record);
  }

  public List<Row> getBGPDiff() {
    List<Row> resultRows = new ArrayList<>();

    if (_config1.getDefaultVrf().getBgpProcess() == null
        && _config2.getDefaultVrf().getBgpProcess() == null) {
      return new ArrayList<>();
    }

    SortedMap<Prefix, BgpActivePeerConfig> neighbors1 =
        _config1.getDefaultVrf().getBgpProcess().getActiveNeighbors();
    SortedMap<Prefix, BgpActivePeerConfig> neighbors2 =
        _config2.getDefaultVrf().getBgpProcess().getActiveNeighbors();

    Set<Prefix> neighborPrefixes = new TreeSet<>(neighbors1.keySet());
    neighborPrefixes.addAll(neighbors2.keySet());
    for (Prefix neighbor : neighborPrefixes) {
      resultRows.addAll(getRowsForNeighbor(neighbor, neighbors1, neighbors2));
    }
    resultRows = combineRows(resultRows);
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

  public List<Row> getOspfDiff() {
    Map<String, OspfProcess> ospf1 = _config1.getDefaultVrf().getOspfProcesses();
    Map<String, OspfProcess> ospf2 = _config2.getDefaultVrf().getOspfProcesses();

    List<Row> resultRows = new ArrayList<>();

    Set<String> ospfSet = new TreeSet<>(ospf1.keySet());
    ospfSet.addAll(ospf2.keySet());

    for (String key : ospfSet) {
      if (!ospf1.containsKey(key) || !ospf2.containsKey(key)) {
        // Check if both routers have an ospf process
        // TODO: OSPF processes do not need to have same name
        // resultRows.add(createUnequalOspfRow(ospf1, ospf2, key));
      } else {
        OspfProcess process1 = ospf1.get(key);
        OspfProcess process2 = ospf2.get(key);

        String policyName1 = process1.getExportPolicy();
        String policyName2 = process2.getExportPolicy();

        RoutingPolicy policy1 = _config1.getRoutingPolicies().get(policyName1);
        RoutingPolicy policy2 = _config2.getRoutingPolicies().get(policyName2);

        resultRows.addAll(computeRoutePolicyDiff("OSPF process " + key, policy1, policy2));
      }
    }
    return resultRows;
  }

  private Map<RoutingPolicy, Map<RoutingPolicy, List<Prefix>>> getCorrespondingFilters(
      SortedMap<Prefix, BgpActivePeerConfig> neighbors1,
      SortedMap<Prefix, BgpActivePeerConfig> neighbors2) {
    Map<RoutingPolicy, Map<RoutingPolicy, List<Prefix>>> result = new HashMap<>();

    Set<Prefix> neighborPrefixes = new TreeSet<>(neighbors1.keySet());
    neighborPrefixes.retainAll(neighbors2.keySet());

    for (Prefix neighbor : neighborPrefixes) {
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
      if (r1Import != null || r2Import != null) {
        // Compare import policies
        r1Import = ObjectUtils.defaultIfNull(r1Import, getDefaultImportPolicy(_config1));
        r2Import = ObjectUtils.defaultIfNull(r2Import, getDefaultImportPolicy(_config2));
        if (extractor.hasNonDefaultPolicy(r1Import, _config1)
            || extractor.hasNonDefaultPolicy(r2Import, _config2)) {
          result
              .computeIfAbsent(r1Import, k -> new HashMap<>())
              .computeIfAbsent(r2Import, k -> new ArrayList<>())
              .add(neighbor);
        }
      }

      if (r1Export != null || r2Export != null) {
        // Compare export policies
        r1Export = ObjectUtils.defaultIfNull(r1Export, getDefaultExportPolicy(_config1));
        r2Export = ObjectUtils.defaultIfNull(r2Export, getDefaultExportPolicy(_config2));
        if (extractor.hasNonDefaultPolicy(r1Export, _config1)
            || extractor.hasNonDefaultPolicy(r2Export, _config2)) {
          result
              .computeIfAbsent(r1Export, k -> new HashMap<>())
              .computeIfAbsent(r2Export, k -> new ArrayList<>())
              .add(neighbor);
        }
      }
    }
    return result;
  }

  private List<Row> getUnequalNeighborRows(
      SortedMap<Prefix, BgpActivePeerConfig> neighbors1,
      SortedMap<Prefix, BgpActivePeerConfig> neighbors2) {
    List<Row> resultRows = new ArrayList<>();
    Set<Prefix> neighborSet = new HashSet<>(neighbors1.keySet());
    neighborSet.addAll(neighbors2.keySet());
    for (Prefix neighbor : neighborSet) {
      if (!neighbors1.keySet().contains(neighbor) || !neighbors2.keySet().contains(neighbor)) {
        resultRows.add(createUnequalNeighborRow(neighbors1, neighbors2, neighbor));
      }
    }
    return resultRows;
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

    if (r1Import != null || r2Import != null) {
      // Compare import policies
      r1Import = ObjectUtils.defaultIfNull(r1Import, getDefaultImportPolicy(_config1));
      r2Import = ObjectUtils.defaultIfNull(r2Import, getDefaultImportPolicy(_config2));
      if (extractor.hasNonDefaultPolicy(r1Import, _config1)
          || extractor.hasNonDefaultPolicy(r2Import, _config2)) {
        resultRows.addAll(
            computeRoutePolicyDiff(neighborToString(neighbor, true), r1Import, r2Import));
      }
    }

    if (r1Export != null || r2Export != null) {
      // Compare export policies
      r1Export = ObjectUtils.defaultIfNull(r1Export, getDefaultExportPolicy(_config1));
      r2Export = ObjectUtils.defaultIfNull(r2Export, getDefaultExportPolicy(_config2));

      if (extractor.hasNonDefaultPolicy(r1Export, _config1)
          || extractor.hasNonDefaultPolicy(r2Export, _config2)) {
        resultRows.addAll(
            computeRoutePolicyDiff(neighborToString(neighbor, false), r1Export, r2Export));
      }
    }
    return resultRows;
  }

  private String neighborToString(Prefix neighbor, boolean incoming) {
    String extension = incoming ? "-IMPORT" : "-EXPORT";
    return neighbor.getStartIp() + extension;
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
            .put(COL_NODE1, _routerName1)
            .put(COL_NODE2, _routerName2)
            .put(COL_FILTER1, "")
            .put(COL_FILTER2, "")
            .put(COL_ROUTE_INCLUDED_PREFIXES, "")
            .put(COL_ROUTE_EXCLUDED_PREFIXES, "")
            .put(COL_ROUTE_COMM, "")
            .put(COL_ROUTE_PROTOCOL, "")
            .put(COL_TEXT1, n1Present)
            .put(COL_TEXT2, n2Present)
            .put(COL_ACTION1, "")
            .put(COL_ACTION2, "");
    return builder.build();
  }

  private Row createUnequalOspfRow(
      Map<String, OspfProcess> ospf1, Map<String, OspfProcess> ospf2, String id) {

    String n1Present = ospf1.keySet().contains(id) ? "PRESENT" : "ABSENT";
    String n2Present = ospf2.keySet().contains(id) ? "PRESENT" : "ABSENT";
    TypedRowBuilder builder =
        Row.builder(METADATA_MAP)
            .put(COL_NEIGHBOR, "OSPF Process " + id)
            .put(COL_NODE1, _routerName1)
            .put(COL_NODE2, _routerName2)
            .put(COL_FILTER1, "")
            .put(COL_FILTER2, "")
            .put(COL_ROUTE_INCLUDED_PREFIXES, "")
            .put(COL_ROUTE_COMM, "")
            .put(COL_ROUTE_PROTOCOL, "")
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

    // Ignore cases where prefix len > 32, etc.
    BDD difference = new RouteToBDD(_record).allRoutes();

    // Compute difference
    difference = difference.and(accepted1.and(accepted2.not()).or(accepted1.not().and(accepted2)));

    // Remove ignored cases
    BDD ignored = new RouteToBDD(_record).buildPrefixRangesBDD(_ignoredPrefixRanges);
    difference = difference.and(ignored.not());
    if (!difference.isZero()) {
      List<BDD> bddList =
          getBDDEquivalenceClasses(difference, actionMap1.getBDDKeys(), actionMap2.getBDDKeys());
      ret.addAll(
          createRowFromDiff(neighbor, bddList, r1Policy, transferBDD1, r2Policy, transferBDD2));
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
      // Prefix : if sets of prefix ranges is not simple, it is divided into multiple rows
      List<IncludedExcludedPrefixRanges> ranges =
          _prefixExtractor.getIncludedExcludedPrefixRanges(bdd);
      for (IncludedExcludedPrefixRanges range : ranges) {
        String includedPrefixStr = range.getIncludedPrefixString();
        String excludedPrefixStr = range.getExcludedPrefixString();

        // Protocol
        StringBuilder protoString = new StringBuilder();
        Protocol[] allProtos =
            new Protocol[] {Protocol.CONNECTED, Protocol.STATIC, Protocol.OSPF, Protocol.BGP};
        for (Protocol protocol : allProtos) {
          BDD protoBdd = _record.getProtocolHistory().value(protocol);
          if (bdd.andSat(protoBdd)) {
            protoString.append(protocol.name()).append("\n");
          }
        }

        // Communities
        StringBuilder commString = new StringBuilder();
        for (Entry<CommunityVar, BDD> commEntry : _record.getCommunities().entrySet()) {
          CommunityVar commVar = commEntry.getKey();
          BDD commBDD = commEntry.getValue();
          if (!commBDD.and(example).isZero()) {
            commString.append(commVar.getRegex()).append("\n");
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

        String bddbits = fullSatBDDToString(bdd.satOne());

        TypedRowBuilder builder =
            Row.builder(METADATA_MAP)
                .put(COL_NEIGHBOR, neighbor)
                .put(COL_NODE1, _routerName1)
                .put(COL_NODE2, _routerName2)
                .put(COL_FILTER1, policy1Names)
                .put(COL_FILTER2, policy2Names)
                .put(COL_ROUTE_INCLUDED_PREFIXES, includedPrefixStr)
                .put(COL_ROUTE_EXCLUDED_PREFIXES, excludedPrefixStr)
                .put(COL_ROUTE_COMM, commString.toString().trim())
                .put(COL_ROUTE_PROTOCOL, protoString.toString().trim())
                .put(COL_TEXT1, stmtText1)
                .put(COL_TEXT2, stmtText2)
                .put(COL_ACTION1, action1)
                .put(COL_ACTION2, action2)
                .put(COL_BDD_BITS, bddbits);
        ret.add(builder.build());
      }
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
      boolean satWithOne = bdd.andSat(factory.ithVar(i));
      boolean satWithZero = bdd.andSat(factory.ithVar(i).not());
      if (satWithOne && satWithZero) {
        builder.append("x");
      }
      else if (satWithZero) {
        builder.append("0");
      } else if (satWithOne){
        builder.append("1");
      }
      if (i % 16 == 15) {
        builder.append(" ").append(i + 1).append("\n");
      }
    }
    return builder.toString();
  }

  /*
  Check that all fields except Neighbor are equal
   */
  private boolean rowsMatch(@Nullable Row row1, @Nullable Row row2) {
    if (row1 != null && row2 != null) {
      for (ColumnMetadata columnMetadata : COLUMN_METADATA) {
        if (!columnMetadata.getName().equals(COL_NEIGHBOR)) {
          Object row1Data = row1.get(columnMetadata.getName(), columnMetadata.getSchema());
          Object row2Data = row2.get(columnMetadata.getName(), columnMetadata.getSchema());
          boolean sameField = Objects.equals(row1Data, row2Data);
          if (!sameField) {
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  /*
  Combine rows that match on all fields except neighbor
   */
  private List<Row> combineRows(List<Row> rows) {
    List<Row> newList = new ArrayList<>();
    Comparator<Row> sortByAllExceptNeighbor =
        Comparator.comparing((Row r) -> r.getString(COL_ROUTE_INCLUDED_PREFIXES))
            .thenComparing((Row r) -> r.getString(COL_ROUTE_EXCLUDED_PREFIXES))
            .thenComparing((Row r) -> r.getString(COL_ROUTE_COMM))
            .thenComparing((Row r) -> r.getString(COL_ROUTE_PROTOCOL))
            .thenComparing((Row r) -> r.getString(COL_FILTER1))
            .thenComparing((Row r) -> r.getString(COL_FILTER2))
            .thenComparing((Row r) -> r.getString(COL_ACTION1))
            .thenComparing((Row r) -> r.getString(COL_ACTION2))
            .thenComparing((Row r) -> r.getString(COL_TEXT1))
            .thenComparing((Row r) -> r.getString(COL_TEXT2));

    rows.sort(sortByAllExceptNeighbor);

    Row prevRow = null;
    for (Row row : rows) {
      if (rowsMatch(prevRow, row)) {
        TypedRowBuilder builder = Row.builder(METADATA_MAP);
        for (ColumnMetadata data : COLUMN_METADATA) {
          if (data.getName().equals(COL_NEIGHBOR)) {
            builder.put(
                COL_NEIGHBOR, prevRow.getString(COL_NEIGHBOR) + "\n" + row.getString(COL_NEIGHBOR));
          } else {
            builder.put(data.getName(), prevRow.get(data.getName()));
          }
        }
        prevRow = builder.build();
      } else {
        if (prevRow != null) {
          newList.add(prevRow);
        }
        prevRow = row;
      }
    }
    if (prevRow != null) {
      newList.add(prevRow);
    }
    return newList;
  }
}

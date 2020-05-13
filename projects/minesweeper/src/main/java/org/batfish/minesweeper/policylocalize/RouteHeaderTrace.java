package org.batfish.minesweeper.policylocalize;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.Vrf;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.bgp.Ipv4UnicastAddressFamily;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
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
import org.batfish.minesweeper.communities.CommunityVarExtractor;
import org.batfish.minesweeper.communities.CommunityVarSet;
import org.batfish.minesweeper.policylocalize.resultrepr.IncludedExcludedPrefixRanges;
import org.batfish.minesweeper.policylocalize.resultrepr.PrefixExtractor;

public class RouteHeaderTrace {

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

  public static final List<ColumnMetadata> COLUMN_METADATA = ImmutableList.of(new ColumnMetadata(COL_NEIGHBOR,
          Schema.STRING,
          "Neighbor IP",
          true,
          false),
      new ColumnMetadata(COL_ROUTE_INCLUDED_PREFIXES,
          Schema.STRING,
          "Included Prefixes",
          true,
          false),
      new ColumnMetadata(COL_ROUTE_EXCLUDED_PREFIXES,
          Schema.STRING,
          "Excluded Prefixes",
          true,
          false),
      new ColumnMetadata(COL_ROUTE_COMM, Schema.STRING, "Community", true, false),
      new ColumnMetadata(COL_ROUTE_PROTOCOL, Schema.STRING, "Protocol", true, false),
      new ColumnMetadata(COL_NODE1, Schema.STRING, "Node", true, false),
      new ColumnMetadata(COL_FILTER1, Schema.STRING, "Filter name", true, false),
      new ColumnMetadata(COL_TEXT1, Schema.STRING, "Line text", true, false),
      new ColumnMetadata(COL_ACTION1,
          Schema.STRING,
          "Action performed by the line (e.g., PERMIT or DENY)",
          true,
          false));

  private static final Map<String, ColumnMetadata> METADATA_MAP = TableMetadata.toColumnMap(
      COLUMN_METADATA);

  private final Graph _graph;
  private final BDDRoute _record;
  private final Configuration _config1;
  private final String _routerName1;
  private final PolicyQuotient _policyQuotient;
  private final List<PrefixRange> _relevantPrefixes;
  private final PrefixExtractor _prefixExtractor;
  private final CommunityVarSet _comms;

  public RouteHeaderTrace(String router1, IBatfish batfish, Configuration configuration,
      List<PrefixRange> ranges) {
    _relevantPrefixes = ranges;

    _graph = new Graph(batfish, batfish.getSnapshot());
    _comms = new CommunityVarExtractor(Collections.singletonList(configuration)).getCommunityVars();
    _record = new BDDRoute(_comms.getVars());
    _policyQuotient = new PolicyQuotient();

    _config1 = configuration;

    _routerName1 = router1;

    Set<RouteFilterList> filterLists = new HashSet<>(_config1.getRouteFilterLists().values());

    _prefixExtractor = new PrefixExtractor(filterLists, _record);
    //_prefixExtractor.addPrefixRanges(ranges);
  }

  public List<Row> getBGPDiff() {
    List<Row> resultRows = new ArrayList<>();

    if (_config1.getDefaultVrf().getBgpProcess() == null) {
      return new ArrayList<>();
    }

    SortedMap<Prefix, BgpActivePeerConfig> neighbors1 = _config1.getDefaultVrf()
        .getBgpProcess()
        .getActiveNeighbors();

    Set<Prefix> neighborPrefixes = neighbors1.keySet();
    for (Prefix neighbor : neighborPrefixes) {
      resultRows.addAll(getRowsForNeighbor(neighbor, neighbors1));
    }
    resultRows = combineRows(resultRows);
    return resultRows;
  }

  private List<Row> getRowsForNeighbor(Prefix neighbor,
      SortedMap<Prefix, BgpActivePeerConfig> neighbors1) {
    List<Row> resultRows = new ArrayList<>();
    BgpActivePeerConfig bgpConfig1 = neighbors1.get(neighbor);

    String r1ImportName = Optional.ofNullable(bgpConfig1)
        .map(BgpActivePeerConfig::getIpv4UnicastAddressFamily)
        .map(Ipv4UnicastAddressFamily::getImportPolicy)
        .orElse(null);
    String r1ExportName = Optional.ofNullable(bgpConfig1)
        .map(BgpActivePeerConfig::getIpv4UnicastAddressFamily)
        .map(Ipv4UnicastAddressFamily::getExportPolicy)
        .orElse(null);

    RoutingPolicy r1Import = _config1.getRoutingPolicies().getOrDefault(r1ImportName, null);
    RoutingPolicy r1Export = _config1.getRoutingPolicies().getOrDefault(r1ExportName, null);

    RoutePolicyNamesExtractor extractor = new RoutePolicyNamesExtractor();

    if (r1Import != null) {
      // Compare import policies
      r1Import = ObjectUtils.defaultIfNull(r1Import, getDefaultImportPolicy(_config1));
      if (extractor.hasNonDefaultPolicy(r1Import, _config1)) {
        resultRows.addAll(computeRoutePolicyDiff(neighborToString(neighbor, true),
            r1Import,
            Collections.singleton(Protocol.BGP)));
      }
    }

    if (r1Export != null) {
      // Compare export policies
      r1Export = ObjectUtils.defaultIfNull(r1Export, getDefaultExportPolicy(_config1));

      if (extractor.hasNonDefaultPolicy(r1Export, _config1)) {
        resultRows.addAll(computeRoutePolicyDiff(neighborToString(neighbor, false),
            r1Export,
            getBgpRedistributeProtocols()));
      }
    }
    return resultRows;
  }

  private List<Row> computeRoutePolicyDiff(String neighbor, RoutingPolicy r1Policy,
      Collection<Protocol> protos) {

    List<Row> ret = new ArrayList<>();

    TransferBDD transferBDD1 = new TransferBDD(_graph,
        _config1,
        r1Policy.getStatements(),
        _policyQuotient);
    BDDRoute route1 = _record.deepCopy();
    TransferResult<TransferReturn, BDD> transferResult1 = transferBDD1.compute(new TreeSet<>(),
        route1,
        _comms);
    BDD accepted1 = transferResult1.getReturnValue().getSecond();

    BDDPolicyActionMap actionMap1 = transferBDD1.getBDDPolicyActionMap();

    accepted1 = actionMap1.mapToEncodedBDD(_record);

    // Ignore cases where prefix len > 32, etc.
    BDD relevant = new RouteToBDD(_record).allRoutes();
    BDD protoBdd = protos.stream()
        .map(_record.getProtocolHistory()::value)
        .reduce(BDD::or)
        .orElse(BDDRoute.getFactory().one());
    relevant.andWith(protoBdd);

    // Remove ignored cases
    BDD r = new RouteToBDD(_record).buildPrefixRangesBDD(_relevantPrefixes);
    relevant = relevant.and(r);
    if (!relevant.isZero()) {
      List<BDD> bddList = actionMap1.getBDDKeys()
          .stream()
          .map(relevant::and)
          .filter(bdd -> !bdd.isZero())
          .collect(Collectors.toList());
      ret.addAll(createRowFromDiff(neighbor, bddList, r1Policy, transferBDD1));
    }
    return ret;
  }

  private List<Row> createRowFromDiff(String neighbor, List<BDD> diffs, RoutingPolicy policy1,
      TransferBDD transferBDD1) {

    RoutePolicyNamesExtractor extractor = new RoutePolicyNamesExtractor();
    // Policy Names
    String policy1Names = extractor.getCombinedCalledPoliciesWithoutGenerated(policy1, _config1);

    List<Row> ret = new ArrayList<>();

    for (BDD bdd : diffs) {
      BDD example = bdd.fullSatOne();
      // Prefix : if sets of prefix ranges is not simple, it is divided into multiple rows
      List<IncludedExcludedPrefixRanges> ranges = _prefixExtractor.getIncludedExcludedPrefixRanges(
          bdd);
      for (IncludedExcludedPrefixRanges range : ranges) {
        String includedPrefixStr = range.getIncludedPrefixString();
        String excludedPrefixStr = range.getExcludedPrefixString();

        // Protocol
        StringBuilder protoString = new StringBuilder();
        for (Protocol protocol : Protocol.allProtocols()) {
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
        if (stmtText1.length() == 0) {
          stmtText1 = "DEFAULT BEHAVIOR";
        }
        // Action
        String action1 = transferBDD1.getBDDPolicyActionMap().getAction(bdd).toString();

        String bddbits = fullSatBDDToString(bdd.satOne());

        TypedRowBuilder builder = Row.builder(METADATA_MAP)
            .put(COL_NEIGHBOR, neighbor)
            .put(COL_NODE1, _routerName1)
            .put(COL_FILTER1, policy1Names)
            .put(COL_ROUTE_INCLUDED_PREFIXES, includedPrefixStr)
            .put(COL_ROUTE_EXCLUDED_PREFIXES, excludedPrefixStr)
            .put(COL_ROUTE_COMM, commString.toString().trim())
            .put(COL_ROUTE_PROTOCOL, protoString.toString().trim())
            .put(COL_TEXT1, stmtText1)
            .put(COL_ACTION1, action1);
        ret.add(builder.build());
      }
    }
    return ret;
  }

  private static String fullSatBDDToString(BDD bdd) {
    BDDFactory factory = BDDRoute.getFactory();
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < factory.varNum(); i++) {
      boolean satWithOne = bdd.andSat(factory.ithVar(i));
      boolean satWithZero = bdd.andSat(factory.ithVar(i).not());
      if (satWithOne && satWithZero) {
        builder.append("x");
      } else if (satWithZero) {
        builder.append("0");
      } else if (satWithOne) {
        builder.append("1");
      }
      if (i % 16 == 15) {
        builder.append(" ").append(i + 1).append("\n");
      }
    }
    return builder.toString();
  }

  private Collection<Protocol> getBgpRedistributeProtocols() {
    Vrf vrf1 = _config1.getDefaultVrf();
    Set<Protocol> protos = new HashSet<>();
    protos.add(Protocol.BGP);
    protos.add(Protocol.CONNECTED);
    if (vrf1.getOspfProcesses().size() > 0) {
      protos.add(Protocol.OSPF);
    }
    if (vrf1.getStaticRoutes().size() > 0) {
      protos.add(Protocol.STATIC);
    }
    return protos;
  }

  @Nullable private RoutingPolicy getDefaultImportPolicy(Configuration conf) {
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

  @Nullable private RoutingPolicy getDefaultExportPolicy(Configuration conf) {
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

  /*
Combine rows that match on all fields except neighbor
 */
  private List<Row> combineRows(List<Row> rows) {
    List<Row> newList = new ArrayList<>();
    Comparator<Row> sortByAllExceptNeighbor = Comparator.comparing((Row r) -> r.getString(
        COL_ROUTE_INCLUDED_PREFIXES))
        .thenComparing((Row r) -> r.getString(COL_ROUTE_EXCLUDED_PREFIXES))
        .thenComparing((Row r) -> r.getString(COL_ROUTE_COMM))
        .thenComparing((Row r) -> r.getString(COL_ROUTE_PROTOCOL))
        .thenComparing((Row r) -> r.getString(COL_FILTER1))
        .thenComparing((Row r) -> r.getString(COL_ACTION1))
        .thenComparing((Row r) -> r.getString(COL_TEXT1));

    rows.sort(sortByAllExceptNeighbor);

    Row prevRow = null;
    for (Row row : rows) {
      if (rowsMatch(prevRow, row)) {
        TypedRowBuilder builder = Row.builder(METADATA_MAP);
        for (ColumnMetadata data : COLUMN_METADATA) {
          if (data.getName().equals(COL_NEIGHBOR)) {
            builder.put(COL_NEIGHBOR,
                prevRow.getString(COL_NEIGHBOR) + "\n" + row.getString(COL_NEIGHBOR));
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
    newList.sort(Comparator.comparing(r -> r.getString(COL_NEIGHBOR)));
    return newList;
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

  private String neighborToString(Prefix neighbor, boolean incoming) {
    String extension = incoming ? "-IMPORT" : "-EXPORT";
    return neighbor.getStartIp() + extension;
  }

}

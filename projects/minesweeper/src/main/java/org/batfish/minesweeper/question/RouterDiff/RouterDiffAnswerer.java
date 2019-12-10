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
import org.batfish.minesweeper.policylocalize.RoutePolicyNamesExtractor;
import org.batfish.minesweeper.policylocalize.RouteToBDD;
import org.batfish.minesweeper.policylocalize.SymbolicResult;
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
    TableAnswerElement answerElement = new TableAnswerElement(new TableMetadata(COLUMN_METADATA));
    getBGPDiff().forEach(answerElement::addRow);
    return answerElement;
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

  private List<Row> getBGPDiff() {
    List<Row> resultRows = new ArrayList<>();

    Graph graph = new Graph(_batfish);
    SpecifierContext specifierContext = _batfish.specifierContext();
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);

    if (nodeSet.size() < 2) {
      System.err.println("Fewer than 2 specified nodes: ");
      nodeSet.forEach(System.out::println);
      throw new IllegalArgumentException("Fewer than 2 specified nodes: "
          + nodeSet.stream().reduce((a, b) -> a + "\n" + b).orElse(""));
    } else if (nodeSet.size() > 2) {
      System.err.println("More than 2 specified nodes: ");
      nodeSet.forEach(System.out::println);
      throw new IllegalArgumentException("More than 2 specified nodes: "
          + nodeSet.stream().reduce((a, b) -> a + "\n" + b).orElse(""));
    }

    Set<CommunityVar> comms = graph.getAllCommunities();
    BDDRoute record = new BDDRoute(comms);
    PolicyQuotient policyQuotient = new PolicyQuotient();

    List<Configuration> configurations =
        nodeSet.stream().map(specifierContext.getConfigs()::get).collect(Collectors.toList());
    Configuration config1 = configurations.get(0);
    Configuration config2 = configurations.get(1);

    SortedMap<Prefix, BgpActivePeerConfig> neighbors1 =
        config1.getDefaultVrf().getBgpProcess().getActiveNeighbors();
    SortedMap<Prefix, BgpActivePeerConfig> neighbors2 =
        config2.getDefaultVrf().getBgpProcess().getActiveNeighbors();

    Set<Prefix> neighborPrefixes = new TreeSet<>(neighbors1.keySet());
    neighborPrefixes.addAll(neighbors2.keySet());
    for (Prefix neighbor : neighborPrefixes) {
      if (!neighbors1.keySet().contains(neighbor) || !neighbors2.keySet().contains(neighbor)) {
        String n1Present = neighbors1.keySet().contains(neighbor) ? "PRESENT" : "ABSENT";
        String n2Present = neighbors2.keySet().contains(neighbor) ? "PRESENT" : "ABSENT";
        TypedRowBuilder builder =
            Row.builder(METADATA_MAP)
                .put(COL_NEIGHBOR, neighbor.getStartIp().toString())
                .put(COL_NODE1, config1.getHostname())
                .put(COL_NODE2, config2.getHostname())
                .put(COL_FILTER1, "")
                .put(COL_FILTER2, "")
                .put(COL_ROUTE_PREFIX, "")
                .put(COL_ROUTE_COMM, "")
                .put(COL_TEXT1, n1Present)
                .put(COL_TEXT2, n2Present)
                .put(COL_ACTION1, "")
                .put(COL_ACTION2, "");
        resultRows.add(builder.build());
        continue;
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

      RoutingPolicy r1Import = config1.getRoutingPolicies().getOrDefault(r1ImportName, null);
      RoutingPolicy r1Export = config1.getRoutingPolicies().getOrDefault(r1ExportName, null);
      RoutingPolicy r2Import = config2.getRoutingPolicies().getOrDefault(r2ImportName, null);
      RoutingPolicy r2Export = config2.getRoutingPolicies().getOrDefault(r2ExportName, null);

      RoutePolicyNamesExtractor extractor = new RoutePolicyNamesExtractor();
      System.out.println("TO NEIGHBOR " + neighbor);
      Optional.ofNullable(r1Import).ifPresent(p -> System.out.println("\tIMPORT1 " + p.getName()));
      extractor
          .extractCalledPolicies(r1Import, config1)
          .forEach(p -> System.out.println("\t\t" + p));
      Optional.ofNullable(r2Import).ifPresent(p -> System.out.println("\tIMPORT2 " + p.getName()));
      extractor
          .extractCalledPolicies(r2Import, config2)
          .forEach(p -> System.out.println("\t\t" + p));
      Optional.ofNullable(r1Export).ifPresent(p -> System.out.println("\tEXPORT1 " + p.getName()));
      extractor
          .extractCalledPolicies(r1Export, config1)
          .forEach(p -> System.out.println("\t\t" + p));
      Optional.ofNullable(r2Export).ifPresent(p -> System.out.println("\tEXPORT2 " + p.getName()));
      extractor
          .extractCalledPolicies(r2Export, config2)
          .forEach(p -> System.out.println("\t\t" + p));

      if (r1Import == null && r2Import == null) {
        System.out.println("NO IMPORT POLICIES");
      } else {
        // Compare import policies
        r1Import = ObjectUtils.defaultIfNull(r1Import, getDefaultImportPolicy(config1));
        r2Import = ObjectUtils.defaultIfNull(r2Import, getDefaultImportPolicy(config2));
        if (extractor.hasNonDefaultPolicy(r1Import, config1)
            || extractor.hasNonDefaultPolicy(r2Import, config2)) {
          System.out.println("IMPORT POLICIES:");
          resultRows.addAll(
              printRoutePolicyDiff(
                  neighbor.getStartIp().toString(),
                  graph,
                  configurations,
                  r1Import,
                  r2Import,
                  record,
                  policyQuotient));
        } else {
          System.out.println("ONLY DEFAULT IMPORT POLICIES");
        }
      }

      if (r1Export == null && r2Export == null) {
        System.out.println("NO EXPORT POLICIES");
      } else {
        // Compare export policies
        r1Export = ObjectUtils.defaultIfNull(r1Export, getDefaultExportPolicy(config1));
        r2Export = ObjectUtils.defaultIfNull(r2Export, getDefaultExportPolicy(config2));

        if (extractor.hasNonDefaultPolicy(r1Export, config1)
            || extractor.hasNonDefaultPolicy(r2Export, config2)) {
          System.out.println("EXPORT POLICIES:");
          resultRows.addAll(
              printRoutePolicyDiff(
                  neighbor.getStartIp().toString(),
                  graph,
                  configurations,
                  r1Export,
                  r2Export,
                  record,
                  policyQuotient));
        } else {
          System.out.println("ONLY DEFAULT EXPORT POLICIES");
        }
      }
    }
    return resultRows;
  }


  private List<Row> printRoutePolicyDiff(
      String neighbor,
      Graph graph,
      List<Configuration> configurations,
      RoutingPolicy r1Policy,
      RoutingPolicy r2Policy,
      BDDRoute record,
      PolicyQuotient policyQuotient) {

    List<Row> ret = new ArrayList<>();

    TransferBDD transferBDD1 =
        new TransferBDD(graph, configurations.get(0), r1Policy.getStatements(), policyQuotient);
    BDDRoute route1 = record.deepCopy();
    TransferResult<TransferReturn, BDD> transferResult1 =
        transferBDD1.compute(new TreeSet<>(), route1);
    BDD accepted1 = transferResult1.getReturnValue().getSecond();

    TransferBDD transferBDD2 =
        new TransferBDD(graph, configurations.get(1), r2Policy.getStatements(), policyQuotient);
    BDDRoute route2 = record.deepCopy();
    TransferResult<TransferReturn, BDD> transferResult2 =
        transferBDD2.compute(new TreeSet<>(), route2);
    BDD accepted2 = transferResult2.getReturnValue().getSecond();

    BDD difference = accepted1.and(accepted2.not()).or(accepted1.not().and(accepted2));
    BDD ignored = new RouteToBDD(record).buildPrefixRangesBDD(_ignoredPrefixRanges);
    difference = difference.and(ignored.not());
    System.out.println("HAS DIFF: " + !difference.isZero());
    if (!difference.isZero()) {
      Map<BDD, List<Statement>> stmtmap1 =
          transferBDD1.getStatementsActingOnRouteSet(new RouteToBDD(record).allRoutes(), record);
      Map<BDD, List<Statement>> stmtmap2 =
          transferBDD2.getStatementsActingOnRouteSet(new RouteToBDD(record).allRoutes(), record);
      List<BDD> bddList =
          getBDDEquivalenceClasses(difference, stmtmap1.keySet(), stmtmap2.keySet());
      ret.addAll(
          createRowFromDiff(
              neighbor,
              record,
              bddList,
              configurations.get(0),
              r1Policy,
              transferBDD1,
              configurations.get(1),
              r2Policy,
              transferBDD2));
      printStatements(configurations.get(0), transferBDD1, r1Policy, record, difference);
      printStatements(configurations.get(1), transferBDD2, r2Policy, record, difference);
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
  public RoutingPolicy getDefaultImportPolicy(Configuration conf) {
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
  public RoutingPolicy getDefaultExportPolicy(Configuration conf) {
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

  public List<Row> createRowFromDiff(
      String neighbor,
      BDDRoute record,
      List<BDD> diffs,
      Configuration config1,
      RoutingPolicy policy1,
      TransferBDD transferBDD1,
      Configuration config2,
      RoutingPolicy policy2,
      TransferBDD transferBDD2) {

    RoutePolicyNamesExtractor extractor = new RoutePolicyNamesExtractor();
    String policy1Names = extractor.getCombinedCalledPoliciesWithoutGenerated(policy1, config1);
    String policy2Names = extractor.getCombinedCalledPoliciesWithoutGenerated(policy2, config2);

    List<Row> ret = new ArrayList<>();

    Map<BDD, List<Statement>> stmtmap1 =
        transferBDD1.getStatementsActingOnRouteSet(new RouteToBDD(record).allRoutes(), record);
    Map<BDD, List<Statement>> stmtmap2 =
        transferBDD2.getStatementsActingOnRouteSet(new RouteToBDD(record).allRoutes(), record);

    for (BDD bdd : diffs) {
      BDD example = bdd.fullSatOne();
      Long prefix = record.getPrefix().getValueSatisfying(example).get();
      Long prefixLen = record.getPrefixLength().getValueSatisfying(example).get();
      String prefixStr = Prefix.create(Ip.create(prefix), Math.toIntExact(prefixLen)).toString();
      StringBuilder commString = new StringBuilder();
      for (Entry<CommunityVar, BDD> commEntry : record.getCommunities().entrySet()) {
        CommunityVar commVar = commEntry.getKey();
        BDD commBDD = commEntry.getValue();
        if (!commBDD.and(example).isZero()) {
          commString.append(commVar.getRegex()).append(" ");
        }
      }

      StringBuilder stmtText1 = new StringBuilder();
      StringBuilder stmtText2 = new StringBuilder();
      for (Entry<BDD, List<Statement>> compared : stmtmap1.entrySet()) {
        if (!compared.getKey().and(bdd).isZero()) {
          List<Statement> statements = compared.getValue();
          statements
              .stream()
              .filter(stmt -> stmt.getText() != null)
              .forEach(stmt -> stmtText1.append(stmt.getText()).append(System.lineSeparator()));
        }
      }
      for (Entry<BDD, List<Statement>> compared : stmtmap2.entrySet()) {
        if (!compared.getKey().and(bdd).isZero()) {
          List<Statement> statements = compared.getValue();
          statements
              .stream()
              .filter(stmt -> stmt.getText() != null)
              .forEach(stmt -> stmtText2.append(stmt.getText()).append(System.lineSeparator()));
        }
      }
      String stmtStr1 = stmtText1.toString().trim();
      String stmtStr2 = stmtText2.toString().trim();
      if (stmtStr1.length() == 0) {
        stmtStr1 = "DEFAULT BEHAVIOR";
      }
      if (stmtStr2.length() == 0) {
        stmtStr2 = "DEFAULT BEHAVIOR";
      }

      SymbolicResult action1 = transferBDD1.getAccepted(bdd);
      SymbolicResult action2 = transferBDD2.getAccepted(bdd);

      TypedRowBuilder builder =
          Row.builder(METADATA_MAP)
              .put(COL_NEIGHBOR, neighbor)
              .put(COL_NODE1, config1.getHostname())
              .put(COL_NODE2, config2.getHostname())
              .put(COL_FILTER1, policy1Names)
              .put(COL_FILTER2, policy2Names)
              .put(COL_ROUTE_PREFIX, prefixStr)
              .put(COL_ROUTE_COMM, commString.toString())
              .put(COL_TEXT1, stmtStr1)
              .put(COL_TEXT2, stmtStr2)
              .put(COL_ACTION1, action1.name())
              .put(COL_ACTION2, action2.name());
      ret.add(builder.build());
    }
    return ret;
  }

  public void printStatements(
      Configuration config,
      TransferBDD transferBDD,
      RoutingPolicy policy,
      BDDRoute record,
      BDD space) {
    Map<BDD, List<Statement>> stmtmap =
        transferBDD.getStatementsActingOnRouteSet(new RouteToBDD(record).allRoutes(), record);

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
      Long prefix = record.getPrefix().getValueSatisfying(example).get();
      Long prefixLen = record.getPrefixLength().getValueSatisfying(example).get();
      System.out.println("PREFIX: " + Prefix.create(Ip.create(prefix), Math.toIntExact(prefixLen)));
      for (Entry<CommunityVar, BDD> commEntry : record.getCommunities().entrySet()) {
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

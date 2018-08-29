package org.batfish.symbolic.smt;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.batfish.common.plugin.IBatfish;
import org.batfish.config.Settings;
import org.batfish.datamodel.InterfaceAddress;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.smt.EnvironmentType;
import org.batfish.datamodel.questions.smt.HeaderQuestion;
import org.batfish.symbolic.CommunityVar;
import org.batfish.symbolic.Graph;
import org.batfish.symbolic.GraphEdge;
import org.batfish.symbolic.Protocol;
import org.batfish.symbolic.answers.SmtManyAnswerElement;
import org.batfish.symbolic.utils.PatternUtils;
import org.batfish.symbolic.utils.Tuple;

public class DifferenceChecker {

  private IBatfish _batfish;
  private final Settings _settings;

  public DifferenceChecker(IBatfish batfish, Settings settings) {
    this._batfish = batfish;
    this._settings = settings;
  }

  private Tuple<Map<GraphEdge, GraphEdge>, Long> bruteForceMatch(
      Map<GraphEdge, GraphEdge> acc,
      List<GraphEdge> edges1,
      List<GraphEdge> edges2,
      BiFunction<GraphEdge, GraphEdge, Long> scorer) {
    if (edges1.isEmpty() || edges2.isEmpty()) {

      long score =
          acc.entrySet()
              .stream()
              .map(ent -> scorer.apply(ent.getKey(), ent.getValue()))
              .reduce(Long::sum)
              .get();
      return new Tuple<>(new HashMap<>(acc), score);
    }
    List<GraphEdge> newEdges1;
    List<GraphEdge> newEdges2;

    GraphEdge ge1 = edges1.get(0);
    long bestScore = Long.MAX_VALUE;
    Map<GraphEdge, GraphEdge> bestMap = null;
    for (GraphEdge ge2 : edges2) {
      acc.put(ge1, ge2);
      newEdges1 = new ArrayList<>(edges1);
      newEdges1.remove(ge1);
      newEdges2 = new ArrayList<>(edges2);
      newEdges2.remove(ge2);
      Tuple<Map<GraphEdge, GraphEdge>, Long> result =
          bruteForceMatch(acc, newEdges1, newEdges2, scorer);
      if (result.getSecond() < bestScore) {
        bestMap = result.getFirst();
        bestScore = result.getSecond();
      }
      acc.remove(ge1);
    }
    return new Tuple<>(bestMap, bestScore);
  }

  private Map<GraphEdge, GraphEdge> getInterfaceMatch(
      String router1,
      String router2,
      List<GraphEdge> edges1,
      List<GraphEdge> edges2,
      String method) {
    Map<GraphEdge, GraphEdge> matchingInterfaces;
    Map<String, GraphEdge> interfaces1 =
        edges1
            .stream()
            .collect(Collectors.toMap(ge -> ge.getStart().getName(), Function.identity()));
    Map<String, GraphEdge> interfaces2 =
        edges2
            .stream()
            .collect(Collectors.toMap(ge -> ge.getStart().getName(), Function.identity()));

    if (method.equals("ip")) {
      /*
      IP based matching:
      First tries to pick unique best matches for each interface
      Then does a brute force matching among remaining interfaces
       */
      BiFunction<GraphEdge, GraphEdge, Long> calculateIPScore =
          (edge1, edge2) -> {
            InterfaceAddress addr1 = edge1.getStart().getAddress();
            InterfaceAddress addr2 = edge2.getStart().getAddress();
            return Math.abs(addr1.getIp().asLong() - addr2.getIp().asLong());
          };
      if (edges1.size() != edges2.size()) {
        System.err.print("Different number of interfaces");
      }
      Map<GraphEdge, Tuple<GraphEdge, Long>> best1to2 = new HashMap<>();
      Map<GraphEdge, Tuple<GraphEdge, Long>> best2to1 = new HashMap<>();
      for (GraphEdge ge1 : edges1) {
        best1to2.put(ge1, new Tuple<>(null, Long.MAX_VALUE));
        for (GraphEdge ge2 : edges2) {
          long prevScore1to2 = best1to2.get(ge1).getSecond();
          long newScore = calculateIPScore.apply(ge1, ge2);
          if (newScore < prevScore1to2) {
            best1to2.put(ge1, new Tuple<>(ge2, newScore));
          } else if (newScore == prevScore1to2) {
            best1to2.put(ge1, new Tuple<>(null, newScore));
          }

          long prevScore2to1 =
              best2to1.get(ge2) == null ? Long.MAX_VALUE : best2to1.get(ge2).getSecond();
          if (newScore < prevScore2to1) {
            best2to1.put(ge2, new Tuple<>(ge1, newScore));
          } else if (newScore == prevScore2to1) {
            best2to1.put(ge2, new Tuple<>(null, newScore));
          }
        }
      }
      // Match interfaces that are each others' bests
      matchingInterfaces = new HashMap<>();
      for (GraphEdge ge1 : new ArrayList<>(edges1)) {
        GraphEdge ge2 = best1to2.get(ge1).getFirst();
        if (ge2 != null
            && best2to1.get(ge2).getFirst() != null
            && best2to1.get(ge2).getFirst().equals(ge1)) {
          matchingInterfaces.put(ge1, ge2);
          edges1.remove(ge1);
          edges2.remove(ge2);
        }
      }
      // Do name matching
      for (GraphEdge edge : new ArrayList<>(edges1)) {
        if (interfaces2.keySet().contains(edge.getStart().getName())) {
          matchingInterfaces.put(edge, interfaces2.get(edge.getStart().getName()));
          edges1.remove(edge);
          edges2.remove(interfaces2.get(edge.getStart().getName()));
        } else {
          System.err.println("interfaces do not match: " + router1 + " " + edge);
        }
      }
      for (GraphEdge edge : edges2) {
        if (!interfaces1.keySet().contains(edge.getStart().getName())) {
          System.err.println("interfaces do not match: " + router2 + " " + edge);
        }
      }
      // Do brute force search with remaining edges
      if (!edges1.isEmpty() && !edges2.isEmpty()) {
        Map<GraphEdge, GraphEdge> others =
            bruteForceMatch(new HashMap<>(), edges1, edges2, calculateIPScore).getFirst();
        matchingInterfaces.putAll(others);
      }
    } else {
      /*
      Name based matching:
      Matches interfaces with the same name
       */

      matchingInterfaces = new HashMap<>();
      for (GraphEdge edge : edges1) {
        if (interfaces2.keySet().contains(edge.getStart().getName())) {
          matchingInterfaces.put(edge, interfaces2.get(edge.getStart().getName()));
        } else {
          System.err.println("interfaces do not match: " + router1 + " " + edge);
        }
      }
      for (GraphEdge edge : edges2) {
        if (!interfaces1.keySet().contains(edge.getStart().getName())) {
          System.err.println("interfaces do not match: " + router2 + " " + edge);
        }
      }
    }

    return matchingInterfaces;
  }



  public AnswerElement checkMultiple(
      HeaderQuestion question, Pattern routerRegex, Prefix prefix, int maxLength) {
    long totalStart = System.currentTimeMillis();
    Graph graph = new Graph(_batfish);
    List<String> routers = PatternUtils.findMatchingNodes(graph, routerRegex, Pattern.compile(""));
    if (routers.size() > 2) {
      System.out.println(
          "Only comparing first 2 routers: " + routers.get(0) + " " + routers.get(1));
      routers = Arrays.asList(routers.get(0), routers.get(1));
    }
    // Generate subgraphs
    Set<String> node1 = new TreeSet<>();
    node1.add(routers.get(0));
    Set<String> node2 = new TreeSet<>();
    node2.add(routers.get(1));
    Graph g1 = new Graph(_batfish, null, node1);
    Graph g2 = new Graph(_batfish, null, node2);

    // Get edges
    Pattern all = Pattern.compile(".*");
    Pattern none = Pattern.compile("");
    Pattern r1 = Pattern.compile(routers.get(0));
    Pattern r2 = Pattern.compile(routers.get(1));
    List<GraphEdge> edges1 = PatternUtils.findMatchingEdges(g1, r1, none, all, none);
    List<GraphEdge> edges2 = PatternUtils.findMatchingEdges(g2, r2, none, all, none);

    // Match interfaces
    Map<GraphEdge, GraphEdge> matchingInterfaces =
        getInterfaceMatch(routers.get(0), routers.get(1), edges1, edges2, "ip");
    matchingInterfaces.entrySet().forEach(System.out::println);

    HeaderQuestion q = new HeaderQuestion(question);
    q.setFailures(0);

    Encoder encoder1 = new Encoder(_settings, g1, q);
    Encoder encoder2 = new Encoder(encoder1, g2);
    encoder1.computeEncoding();
    addEnvironmentConstraints(encoder1, q.getBaseEnvironmentType());
    encoder2.computeEncoding();
    addEnvironmentConstraints(encoder2, q.getBaseEnvironmentType());
    Context ctx = encoder2.getCtx();

    // Equate packet fields in both symbolic packets
    SymbolicPacket pkt1 = encoder1.getMainSlice().getSymbolicPacket();
    SymbolicPacket pkt2 = encoder2.getMainSlice().getSymbolicPacket();
    encoder2.add(pkt1.mkEqual(pkt2));

    // Ignore IP address of interfaces
    BoolExpr ignored =
        ignoreExactInterfaces(
            ctx, graph, encoder2.getMainSlice().getSymbolicPacket().getDstIp(), routers);
    encoder2.add(ignored);

    // Equal Environments on adjacent links
    System.out.println("ENV");
    encoder1
        .getMainSlice()
        .getLogicalGraph()
        .getEnvironmentVars()
        .entrySet()
        .forEach(System.out::println);
    encoder2
        .getMainSlice()
        .getLogicalGraph()
        .getEnvironmentVars()
        .entrySet()
        .forEach(System.out::println);
    Stream<Entry<LogicalEdge, SymbolicRoute>> envStream1 =
        encoder1.getMainSlice().getLogicalGraph().getEnvironmentVars().entrySet().stream();
    Map<GraphEdge, SymbolicRoute> realToEnv1 =
        envStream1.collect(
            Collectors.toMap((ent) -> ent.getKey().getEdge(), Entry::getValue));
    Stream<Entry<LogicalEdge, SymbolicRoute>> envStream2 =
        encoder2.getMainSlice().getLogicalGraph().getEnvironmentVars().entrySet().stream();
    Map<GraphEdge, SymbolicRoute> realToEnv2 =
        envStream2.collect(
            Collectors.toMap((ent) -> ent.getKey().getEdge(), Entry::getValue));
    for (GraphEdge ge1 : matchingInterfaces.keySet()) {
      GraphEdge ge2 = matchingInterfaces.get(ge1);
      SymbolicRoute env1 = realToEnv1.get(ge1);
      SymbolicRoute env2 = realToEnv2.get(ge2);
      if (env1 != null && env2 != null) {
        BoolExpr envEqual = symRouteEqual(ctx, env1, env2);
        encoder2.add(envEqual);
      }
    }
    // Add constraints on forwarding
    BoolExpr sameForwarding = ctx.mkBool(true);
    for (Entry<GraphEdge, GraphEdge> entry : matchingInterfaces.entrySet()) {
      GraphEdge edge1 = entry.getKey();
      GraphEdge edge2 = entry.getValue();

      EncoderSlice mainSlice1 = encoder1.getMainSlice();
      EncoderSlice mainSlice2 = encoder2.getMainSlice();
      BoolExpr dataFwd1 =
          mainSlice1.getSymbolicDecisions().getDataForwarding().get(routers.get(0), edge1);
      BoolExpr dataFwd2 =
          mainSlice2.getSymbolicDecisions().getDataForwarding().get(routers.get(1), edge2);
      assert (dataFwd1 != null);
      assert (dataFwd2 != null);
      sameForwarding = ctx.mkAnd(sameForwarding, ctx.mkEq(dataFwd1, dataFwd2));

    }
    // Add constraints on incoming ACL
    BoolExpr sameIncomingACL =
        getIncomingACLEquivalence(
            ctx, encoder1.getMainSlice(), encoder2.getMainSlice(), matchingInterfaces);
    BoolExpr sameRouteExport =
        getRouteExportEquivalence(
            ctx, encoder1.getMainSlice(), encoder2.getMainSlice(), matchingInterfaces);
    BoolExpr sameBehavior = ctx.mkAnd(sameForwarding, sameIncomingACL, sameRouteExport);

    BoolExpr[] assertions =
        Arrays.stream(encoder2.getSolver().getAssertions())
            .map(Expr::simplify)
            .toArray(BoolExpr[]::new);
    encoder2.add(ctx.mkNot(ctx.mkAnd(sameBehavior)));
    Arrays.stream(encoder2.getSolver().getAssertions())
        .map(Expr::simplify)
        .forEach(System.out::println);

    // Creating solver to test that all dstIps in prefix have a difference
    // Get assertions before requiring forwarding
    Solver dstNoneEquivalent = ctx.mkSolver();
    SymbolicPacket packet = encoder2.getMainSlice().getSymbolicPacket();
    BitVecExpr dstIp = packet.getDstIp();
    BitVecExpr srcIp = packet.getSrcIp();
    Expr[] packetFields = {
        packet.getDstPort(),
        packet.getSrcPort(),
        packet.getIpProtocol(),
        packet.getIcmpCode(),
        packet.getIcmpType()
    };
    List<Expr> inputFields = new ArrayList<>(Arrays.asList(packetFields));
    Expr[] notDstVars =
        encoder2
            .getAllVariables()
            .values()
            .stream()
            .filter(expr -> !expr.equals(dstIp))
            .toArray(Expr[]::new);
    Expr[] notSrcVars =
        encoder2
            .getAllVariables()
            .values()
            .stream()
            .filter(expr -> !expr.equals(srcIp))
            .toArray(Expr[]::new);

    BoolExpr conjunction = ctx.mkAnd(assertions);
    BoolExpr implies = ctx.mkImplies(conjunction, sameBehavior);
    BoolExpr forall = ctx.mkForall(notDstVars, implies, 1, null, null, null, null);
    dstNoneEquivalent.add(forall);
    dstNoneEquivalent.add(ignored);

    // Loop to test longer prefixes
    TreeMap<String, VerificationResult> results = new TreeMap<>();
    List<Long> runTimes = new ArrayList<>();
    List<Long> forallTimes = new ArrayList<>();

    ArrayDeque<SrcDstPair> prefixQueue = new ArrayDeque<>();
    prefixQueue.push(new SrcDstPair(Prefix.parse("0.0.0.0/0"), prefix));
    Set<SrcDstPair> verified = new HashSet<>();
    int prevDstDepth = 0;
    while (!prefixQueue.isEmpty()) {
      SrcDstPair sdp = prefixQueue.pop();
      Prefix currSrcPrefix = sdp.src;
      Prefix currDstPrefix = sdp.dst;
      int dstShift = 32 - currDstPrefix.getPrefixLength();
      int srcShift = 32 - currSrcPrefix.getPrefixLength();
      if (currDstPrefix.getPrefixLength() > prevDstDepth) {
        prevDstDepth = currDstPrefix.getPrefixLength();
        System.out.println(prevDstDepth);
      }
      encoder2.getSolver().push();

      // Add constraint on prefix
      BitVecExpr dstPfxIpExpr = ctx.mkBV(currDstPrefix.getStartIp().asLong(), 32);
      BitVecExpr dstShiftExpr = ctx.mkBV(dstShift, 32);
      BoolExpr matchDstPrefix =
          ctx.mkEq(ctx.mkBVLSHR(dstIp, dstShiftExpr), ctx.mkBVLSHR(dstPfxIpExpr, dstShiftExpr));
      BitVecExpr srcPfxIpExpr = ctx.mkBV(currSrcPrefix.getStartIp().asLong(), 32);
      BitVecExpr srcShiftExpr = ctx.mkBV(srcShift, 32);
      BoolExpr matchSrcPrefix =
          ctx.mkEq(ctx.mkBVLSHR(srcIp, srcShiftExpr), ctx.mkBVLSHR(srcPfxIpExpr, srcShiftExpr));

      encoder2.add(matchDstPrefix);
      encoder2.add(matchSrcPrefix);

      long startTime = System.currentTimeMillis();
      VerificationResult result = encoder2.verify().getFirst();
      long endTime = System.currentTimeMillis();
      runTimes.add(endTime - startTime);
      System.out.println("RUN: " + (endTime-startTime));

      encoder2.getSolver().pop();

      // Check that no ip address equivalent
      boolean doForalls = true;
      if (doForalls) {
        if (!result.isVerified() && currDstPrefix.getPrefixLength() < maxLength) {
          dstNoneEquivalent.push();
          dstNoneEquivalent.add(matchDstPrefix);
          dstNoneEquivalent.add(matchSrcPrefix);

          long forallStartTime = System.currentTimeMillis();
          Status neStatus = dstNoneEquivalent.check();
          long forallEndTime = System.currentTimeMillis();
          forallTimes.add(forallEndTime - forallStartTime);
          System.out.println("FRL: " + (forallEndTime-forallStartTime));

          if (neStatus.equals(Status.UNSATISFIABLE)) {
            results.put(sdp.toString(), result);
          } else {
            if (neStatus.equals(Status.UNKNOWN)) {
              System.out.println("Unknown");
              System.out.println(dstNoneEquivalent.getReasonUnknown());
            }
            prefixQueue.addAll(genLongerPrefix(sdp, maxLength));
          }
          dstNoneEquivalent.pop();
        } else if (result.isVerified()) {
          verified.add(sdp);
        } else if (currDstPrefix.getPrefixLength() >= maxLength) {
          results.put(sdp.toString(), result);
        }
      } else {
        if (!result.isVerified() && currDstPrefix.getPrefixLength() < maxLength) {
          prefixQueue.addAll(genLongerPrefix(sdp, maxLength));
        } else if (result.isVerified()) {
          verified.add(sdp);
        } else if (currDstPrefix.getPrefixLength() >= maxLength) {
          results.put(sdp.toString(), result);
        }
      }
    }

    // Print prefixes where equivalent
    Comparator<SrcDstPair> pfxLenCompare =
        (a, b) -> {
          if (a.dst.getPrefixLength() < b.dst.getPrefixLength()) {
            return -1;
          } else if (a.dst.getPrefixLength() > b.dst.getPrefixLength()) {
            return 1;
          }
          return a.dst.compareTo(b.dst);
        };
    verified.stream().sorted(pfxLenCompare).forEach(System.out::println);

    // Print Times
    System.out.println("RUN COUNT: " + runTimes.size());
    System.out.println("MIN RUN: " + runTimes.stream().mapToLong(Long::longValue).min().getAsLong());
    System.out.println("MAX RUN: " + runTimes.stream().mapToLong(Long::longValue).max().getAsLong());
    System.out.println("AVG RUN: " + runTimes.stream().mapToLong(Long::longValue).average().getAsDouble());
    System.out.println("FRL COUNT: " + forallTimes.size());
    System.out.println("MIN FRL: " + forallTimes.stream().mapToLong(Long::longValue).min().getAsLong());
    System.out.println("MAX FRL: " + forallTimes.stream().mapToLong(Long::longValue).max().getAsLong());
    System.out.println("AVG FRL: " + forallTimes.stream().mapToLong(Long::longValue).average().getAsDouble());

    System.out.println("TOTAL TIME: " + (System.currentTimeMillis() - totalStart));
    return new SmtManyAnswerElement(results);
  }

  class SrcDstPair {
    public Prefix src;
    public Prefix dst;

    public SrcDstPair(Prefix s, Prefix d) {
      src = s;
      dst = d;
    }

    @Override public String toString() {
      return src.toString() + " " + dst.toString();
    }
  }

  /*
  Create longer prefixes that cover an original prefix
   */
  private Set<SrcDstPair> genLongerPrefix(SrcDstPair sdp, int limit) {
    Set<SrcDstPair> ret = new HashSet<>();
    long dstBits = sdp.dst.getStartIp().asLong();
    long srcBits = sdp.src.getStartIp().asLong();
    int dstLength = sdp.dst.getPrefixLength();
    int srcLength = sdp.src.getPrefixLength();
    if (dstLength < limit) {
      for (int i = 0; i < 2; i++) {
        long nextDstBit = 1L << (31 - dstLength);
        Prefix newDst = new Prefix(new Ip(dstBits + i * nextDstBit), dstLength + 1);
        ret.add(new SrcDstPair(sdp.src, newDst));
      }
    } else {
      for (int i = 0; i < 2; i++) {
        long nextSrcBit = 1L << (31 - srcLength);
        Prefix newSrc = new Prefix(new Ip(srcBits + i * nextSrcBit), srcLength + 1);
        ret.add(new SrcDstPair(newSrc, sdp.dst));
      }
    }
    return ret;
  }

  /*
   * Creates a boolean variable representing destinations we don't want
   * to consider due to local differences.
   */
  private BoolExpr ignoreExactInterfaces(
      Context ctx, Graph graph, Expr dstIp, List<String> routers) {
    BoolExpr validDest = ctx.mkBool(true);
    for (GraphEdge ge : graph.getAllEdges()) {
      long address = ge.getStart().getAddress().getIp().asLong();
      validDest = ctx.mkAnd(validDest, ctx.mkNot(ctx.mkEq(dstIp, ctx.mkBV(address, 32))));
    }
    return validDest;
  }

  /*
  Expression for Incoming ACL Equivalence
   */
  private BoolExpr getIncomingACLEquivalence(
      Context ctx, EncoderSlice slice1, EncoderSlice slice2, Map<GraphEdge, GraphEdge> intfMatch) {
    BoolExpr ret = ctx.mkTrue();
    Map<GraphEdge, BoolExpr> acl1 = slice1.getIncomingAcls();
    Map<GraphEdge, BoolExpr> acl2 = slice2.getIncomingAcls();
    for (Entry<GraphEdge, GraphEdge> ent : intfMatch.entrySet()) {
      GraphEdge first = ent.getKey();
      GraphEdge second = ent.getValue();
      if (acl1.containsKey(first) && acl2.containsKey(second)) {
        ret = ctx.mkAnd(ret, ctx.mkEq(acl1.get(first), acl2.get(second)));
      } else if (acl1.containsKey(first)) {
        ret = ctx.mkAnd(ret, acl1.get(first));
      } else if (acl2.containsKey(second)) {
        ret = ctx.mkAnd(ret, acl2.get(second));
      }
    }
    return ret;
  }

  private BoolExpr getRouteExportEquivalence(
      Context ctx, EncoderSlice slice1, EncoderSlice slice2, Map<GraphEdge, GraphEdge> intfMatch) {
    BoolExpr ret = ctx.mkTrue();
    System.out.println("Slice 1:");
    slice1
        .getLogicalGraph()
        .getLogicalEdges()
        .forEach(
            (a, b, c) -> {
              System.out.println(a + " " + b.name());
              for (List<LogicalEdge> d : c) {
                for (LogicalEdge le : d) {
                  System.out.println(le.getEdgeType() + " " + le.getEdge());
                }
              }
            });
    System.out.println("Slice 2:");
    slice2
        .getLogicalGraph()
        .getLogicalEdges()
        .forEach(
            (a, b, c) -> {
              System.out.println(a + " " + b.name());
              for (List<LogicalEdge> d : c) {
                for (LogicalEdge le : d) {
                  System.out.println(le.getEdgeType() + " " + le.getEdge());
                }
              }
            });
    Map<Protocol, List<ArrayList<LogicalEdge>>> slice1EdgeMap = new HashMap<>();
    Map<Protocol, List<ArrayList<LogicalEdge>>> slice2EdgeMap = new HashMap<>();
    slice1.getLogicalGraph().getLogicalEdges().forEach((a, b) -> slice1EdgeMap.putAll(b));
    slice2.getLogicalGraph().getLogicalEdges().forEach((a, b) -> slice2EdgeMap.putAll(b));
    for (Protocol proto : slice1EdgeMap.keySet()) {
      if (!slice2EdgeMap.containsKey(proto)) {
        continue;
      }
      List<LogicalEdge> slice1Edges =
          slice1EdgeMap.get(proto).stream().flatMap(List::stream).collect(Collectors.toList());
      List<LogicalEdge> slice2Edges =
          slice2EdgeMap.get(proto).stream().flatMap(List::stream).collect(Collectors.toList());

      for (LogicalEdge le1 : slice1Edges) {
        for (LogicalEdge le2 : slice2Edges) {
          if (le1.getEdgeType().equals(EdgeType.EXPORT)
              && le2.getEdgeType().equals(EdgeType.EXPORT)
              && intfMatch.containsKey(le1.getEdge())
              && intfMatch.get(le1.getEdge()).equals(le2.getEdge())) {
            ret =
                ctx.mkAnd(
                    ret, symRouteEqual(ctx, le1.getSymbolicRecord(), le2.getSymbolicRecord()));
          }
        }
      }
    }

    System.out.println(ret.simplify());

    return ret;
  }

  private BoolExpr symRouteEqual(Context ctx, SymbolicRoute route1, SymbolicRoute route2) {
    if ((route1.getProto().isBgp() && route2.getProto().isBgp())
        || (route1.getProto().isOspf() && route2.getProto().isOspf())) {

      BoolExpr ret = ctx.mkEq(route1.getPermitted(), route2.getPermitted());
      ret = ctx.mkAnd(ret, ctx.mkEq(route1.getMetric(), route2.getMetric()));
      ret = ctx.mkAnd(ret, ctx.mkEq(route1.getPrefixLength(), route2.getPrefixLength()));
      if (route1.getProto().isOspf()
          && route2.getProto().isOspf()
          && route1.getOspfType() != null
          && route2.getOspfType() != null) {
        ret =
            ctx.mkAnd(
                ret, ctx.mkEq(route1.getOspfType().getBitVec(), route2.getOspfType().getBitVec()));
      } else if (route1.getProto().isBgp()) {
        // Equal communities
        Map<CommunityVar, BoolExpr> comm1 = route1.getCommunities();
        Map<CommunityVar, BoolExpr> comm2 = route2.getCommunities();
        Set<CommunityVar> commSet = new HashSet<>(comm1.keySet());
        commSet.addAll(comm2.keySet());
        System.out.println("-------------- COMMUNITIES -------------");
        for(CommunityVar cv : commSet) {
          System.out.println(cv);
          if(comm1.containsKey(cv) && comm2.containsKey(cv)) {
            System.out.println(comm1.get(cv) + " " + comm2.get(cv));
            ret = ctx.mkAnd(ret, ctx.mkEq(comm1.get(cv), comm2.get(cv)));
          }
        }
      }
      return ret;
    }
    return ctx.mkTrue();
  }

  /*
  Add packet bound constraints to a solver
   */
  private void addSolverPacketConstraints(SymbolicPacket packet, Solver solver, Context ctx) {

    ArithExpr upperBound4 = ctx.mkInt((long) Math.pow(2, 4));
    ArithExpr upperBound8 = ctx.mkInt((long) Math.pow(2, 8));
    ArithExpr upperBound16 = ctx.mkInt((long) Math.pow(2, 16));
    ArithExpr upperBound32 = ctx.mkInt((long) Math.pow(2, 32));
    ArithExpr zero = ctx.mkInt(0);

    // Valid 16 bit integer
    solver.add(ctx.mkGe(packet.getDstPort(), zero));
    solver.add(ctx.mkGe(packet.getSrcPort(), zero));
    solver.add(ctx.mkLt(packet.getDstPort(), upperBound16));
    solver.add(ctx.mkLt(packet.getSrcPort(), upperBound16));

    // Valid 8 bit integer
    solver.add(ctx.mkGe(packet.getIcmpType(), zero));
    solver.add(ctx.mkGe(packet.getIpProtocol(), zero));
    solver.add(ctx.mkLt(packet.getIcmpType(), upperBound8));
    solver.add(ctx.mkLe(packet.getIpProtocol(), upperBound8));

    // Valid 4 bit integer
    solver.add(ctx.mkGe(packet.getIcmpCode(), zero));
    solver.add(ctx.mkLt(packet.getIcmpCode(), upperBound4));
  }

  public AnswerElement checkTwoRouters(
      HeaderQuestion question, Pattern routerRegex, Prefix prefix, int maxLength) {
    Graph graph = new Graph(_batfish);
    List<String> routers = PatternUtils.findMatchingNodes(graph, routerRegex, Pattern.compile(""));
    if (routers.size() > 2) {
      System.out.println("Only matching 1st router: " + routers.get(0));
    }

    HeaderQuestion q = new HeaderQuestion(question);
    q.setFailures(0);

    TreeSet<String> node1 = new TreeSet<>();
    node1.add(routers.get(0));
    TreeSet<String> node2 = new TreeSet<>();
    node2.add(routers.get(1));
    Graph g1 = new Graph(_batfish, null, node1);
    Graph g2 = new Graph(_batfish, null, node2);

    Encoder encoder1 = new Encoder(_settings, g1, q);
    Encoder encoder2 = new Encoder(_settings, g2, q);
    //    Encoder encoder3 = new Encoder(encoder1, g2);
    //    Encoder encoder4 = new Encoder(encoder2, g1);
    Encoder encoder5 = new Encoder(_settings, graph, q);
    encoder1.computeEncoding();
    addEnvironmentConstraints(encoder1, q.getBaseEnvironmentType());
    encoder2.computeEncoding();
    addEnvironmentConstraints(encoder2, q.getBaseEnvironmentType());
    //    encoder3.computeEncoding();
    //    addEnvironmentConstraints(encoder3, q.getBaseEnvironmentType());
    //    encoder4.computeEncoding();
    //    addEnvironmentConstraints(encoder4, q.getBaseEnvironmentType());
    encoder5.computeEncoding();
    addEnvironmentConstraints(encoder5, q.getBaseEnvironmentType());

    VerificationResult result1 = encoder1.verify().getFirst();
    VerificationResult result2 = encoder2.verify().getFirst();
    //    VerificationResult result3 = encoder3.verify().getFirst();
    //    VerificationResult result4 = encoder4.verify().getFirst();
    VerificationResult result5 = encoder5.verify().getFirst();

    SortedMap<String, VerificationResult> results = new TreeMap<>();
    results.put("1", result1);
    results.put("2", result2);
    //    results.put("1+2", result3);
    //    results.put("2+1", result4);
    results.put("both", result5);

    return new SmtManyAnswerElement(results);
  }


  private void addEnvironmentConstraints(Encoder enc, EnvironmentType t) {
    LogicalGraph lg = enc.getMainSlice().getLogicalGraph();
    Context ctx = enc.getCtx();
    switch (t) {
    case ANY:
      break;
    case NONE:
      for (SymbolicRoute vars : lg.getEnvironmentVars().values()) {
        enc.add(ctx.mkNot(vars.getPermitted()));
      }
      break;
    case SANE:
      for (SymbolicRoute vars : lg.getEnvironmentVars().values()) {
        enc.add(ctx.mkLe(vars.getMetric(), ctx.mkInt(50)));
      }
      break;
    default:
      break;
    }
  }

}

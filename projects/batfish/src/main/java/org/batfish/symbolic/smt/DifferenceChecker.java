package org.batfish.symbolic.smt;

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
import java.util.PriorityQueue;
import java.util.Queue;
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

  /*
  Creates constraint for same environment between router in slice of encoder1 and slice of encoder2
   */
  private BoolExpr getEqualEnv(
      Encoder encoder1, Encoder encoder2, Map<GraphEdge, GraphEdge> matchingInterfaces) {
    Context ctx = encoder2.getCtx();
    BoolExpr ret = ctx.mkTrue();
    Stream<Entry<LogicalEdge, SymbolicRoute>> envStream1 =
        encoder1.getMainSlice().getLogicalGraph().getEnvironmentVars().entrySet().stream();
    Map<GraphEdge, SymbolicRoute> realToEnv1 =
        envStream1.collect(Collectors.toMap((ent) -> ent.getKey().getEdge(), Entry::getValue));
    Stream<Entry<LogicalEdge, SymbolicRoute>> envStream2 =
        encoder2.getMainSlice().getLogicalGraph().getEnvironmentVars().entrySet().stream();
    Map<GraphEdge, SymbolicRoute> realToEnv2 =
        envStream2.collect(Collectors.toMap((ent) -> ent.getKey().getEdge(), Entry::getValue));
    for (GraphEdge ge1 : matchingInterfaces.keySet()) {
      GraphEdge ge2 = matchingInterfaces.get(ge1);
      SymbolicRoute env1 = realToEnv1.get(ge1);
      SymbolicRoute env2 = realToEnv2.get(ge2);
      if (env1 != null && env2 != null) {
        BoolExpr envEqual = symRouteEqual(ctx, env1, env2);
        ret = ctx.mkAnd(ret, envEqual);
      }
    }
    return ret;
  }

  /*
  Creates constraint for equal forwarding between router in slice of encoder1 and slice of encoder2
   */
  private BoolExpr getEqualForwarding(
      Encoder encoder1,
      Encoder encoder2,
      List<String> routers,
      Map<GraphEdge, GraphEdge> matchingInterfaces) {
    Context ctx = encoder2.getCtx();
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
    return sameForwarding;
  }

  public AnswerElement checkDiff(
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
    //    matchingInterfaces.entrySet().forEach(System.out::println);

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
    BoolExpr equalEnv = getEqualEnv(encoder1, encoder2, matchingInterfaces);
    encoder2.add(equalEnv);

    // Constraints on forwarding
    BoolExpr sameForwarding = getEqualForwarding(encoder1, encoder2, routers, matchingInterfaces);

    // Constraints on incoming ACL
    BoolExpr sameIncomingACL =
        getIncomingACLEquivalence(
            ctx, encoder1.getMainSlice(), encoder2.getMainSlice(), matchingInterfaces);

    // Constraints on route exports
    BoolExpr sameRouteExport =
        getRouteExportEquivalence(
            ctx, encoder1.getMainSlice(), encoder2.getMainSlice(), matchingInterfaces);

    BoolExpr sameBehavior = ctx.mkAnd(sameForwarding, sameIncomingACL, sameRouteExport);

    BoolExpr[] assertions =
        Arrays.stream(encoder2.getSolver().getAssertions())
            .map(Expr::simplify)
            .toArray(BoolExpr[]::new);

    encoder2.add(ctx.mkNot(ctx.mkAnd(sameBehavior)));
    //    Arrays.stream(encoder2.getSolver().getAssertions())
    //        .map(Expr::simplify)
    //        .forEach(System.out::println);

    // Creating solver to test that all dstIps in prefix have a difference
    // Get assertions before requiring forwarding
    Solver dstNoneEquivalent = ctx.mkSolver();
    Solver srcNoneEquivalent = ctx.mkSolver();
    SymbolicPacket packet = encoder2.getMainSlice().getSymbolicPacket();
    BitVecExpr dstIp = packet.getDstIp();
    BitVecExpr srcIp = packet.getSrcIp();

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

    TreeMap<String, VerificationResult> results = new TreeMap<>();
    List<Long> runTimes = new ArrayList<>();
    List<Long> forallTimes = new ArrayList<>();

    ArrayDeque<SrcDstPair> dstPrefixQueue = new ArrayDeque<>();
    ArrayDeque<SrcDstPair> srcPrefixQueue = new ArrayDeque<>();
    dstPrefixQueue.push(new SrcDstPair(Prefix.parse("0.0.0.0/0"), prefix));
    Set<SrcDstPair> similarities = new HashSet<>();
    Set<SrcDstPair> differences = new HashSet<>();
    int prevDstDepth = 0;

    // Loop to test longer dst prefixes
    while (!dstPrefixQueue.isEmpty()) {
      SrcDstPair sdp = dstPrefixQueue.pop();
      Prefix currSrcPrefix = sdp.src;
      Prefix currDstPrefix = sdp.dst;
      int dstShift = 32 - currDstPrefix.getPrefixLength();
      int srcShift = 32 - currSrcPrefix.getPrefixLength();
      if (currDstPrefix.getPrefixLength() > prevDstDepth) {
        prevDstDepth = currDstPrefix.getPrefixLength();
        // System.out.println(prevDstDepth);
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
      // System.out.println("RUN: " + (endTime - startTime));

      encoder2.getSolver().pop();

      // Check that no ip address equivalent for destinations
      if (!result.isVerified() && currDstPrefix.getPrefixLength() < maxLength) {
        dstNoneEquivalent.push();
        dstNoneEquivalent.add(matchDstPrefix);

        long forallStartTime = System.currentTimeMillis();
        Status neStatus = dstNoneEquivalent.check();
        long forallEndTime = System.currentTimeMillis();
        forallTimes.add(forallEndTime - forallStartTime);
        // System.out.println("FRL: " + (forallEndTime - forallStartTime));

        if (neStatus.equals(Status.UNSATISFIABLE)) {
          srcPrefixQueue.add(sdp);
        } else {
          if (neStatus.equals(Status.UNKNOWN)) {
            // System.out.println("Unknown");
            // System.out.println(dstNoneEquivalent.getReasonUnknown());
          }
          dstPrefixQueue.addAll(genLongerDstPrefix(sdp));
        }
        dstNoneEquivalent.pop();
      } else if (result.isVerified()) {
        similarities.add(sdp);
      } else if (currDstPrefix.getPrefixLength() >= maxLength) {
        srcPrefixQueue.add(sdp);
      }
    }

    int prevSrcDepth = -1;
    // loop for longer src prefixes
    while (!srcPrefixQueue.isEmpty()) {

      SrcDstPair sdp = srcPrefixQueue.pop();
      Prefix currSrcPrefix = sdp.src;
      Prefix currDstPrefix = sdp.dst;
      int dstShift = 32 - currDstPrefix.getPrefixLength();
      int srcShift = 32 - currSrcPrefix.getPrefixLength();
      if (currSrcPrefix.getPrefixLength() > prevSrcDepth) {
        prevSrcDepth = currSrcPrefix.getPrefixLength();
        //        System.out.println(prevSrcDepth);
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
      //      System.out.println("RUN: " + (endTime - startTime));

      encoder2.getSolver().pop();

      // Check that no ip address equivalent
      if (!result.isVerified() && currSrcPrefix.getPrefixLength() < maxLength) {
        srcNoneEquivalent.push();
        BoolExpr srcImplies = ctx.mkImplies(matchDstPrefix, implies);
        BoolExpr srcForall = ctx.mkForall(notSrcVars, srcImplies, 1, null, null, null, null);
        srcNoneEquivalent.add(srcForall);

        srcNoneEquivalent.add(matchSrcPrefix);

        long forallStartTime = System.currentTimeMillis();
        Status neStatus = srcNoneEquivalent.check();
        long forallEndTime = System.currentTimeMillis();
        forallTimes.add(forallEndTime - forallStartTime);
        // System.out.println("FRL: " + (forallEndTime - forallStartTime));

        if (neStatus.equals(Status.UNSATISFIABLE)) {
          results.put(sdp.toString(), result);
          differences.add(sdp);
        } else {
          if (neStatus.equals(Status.UNKNOWN)) {
            // System.out.println("Unknown");
            // System.out.println(srcNoneEquivalent.getReasonUnknown());
          }
          srcPrefixQueue.addAll(genLongerSrcPrefix(sdp));
        }
        srcNoneEquivalent.pop();
      } else if (result.isVerified()) {
        similarities.add(sdp);
      } else if (currSrcPrefix.getPrefixLength() >= maxLength) {
        results.put(sdp.toString(), result);
        differences.add(sdp);
      }
    }

    System.out.println("--------- DIFFERENCES --------");
    Map<Prefix, Set<Prefix>> differencesBySource = separateBySource(differences);
    for (Entry<Prefix, Set<Prefix>> entry : differencesBySource.entrySet()) {
      System.out.println("SRC");
      System.out.println(entry.getKey());
      System.out.println("DST");
      // minimizePrefixes(entry.getValue());
      Set<Prefix> dstDiff =
          differences.stream().map(SrcDstPair::getDst).collect(Collectors.toSet());
      Set<Prefix> dstSiml =
          similarities.stream().map(SrcDstPair::getDst).collect(Collectors.toSet());
      new PrefixMinimize().minimizePrefixWithNesting(dstDiff, dstSiml);
    }
    System.out.println("------------------------------");
    System.out.println("--------- SIMILARITIES -------");
    Map<Prefix, Set<Prefix>> verifiedBySource = separateBySource(similarities);
    for (Entry<Prefix, Set<Prefix>> entry : verifiedBySource.entrySet()) {
      System.out.println("SRC");
      System.out.println(entry.getKey());
      System.out.println("DST");
      // minimizePrefixes(entry.getValue());
    }
    System.out.println("------------------------------");

    // Print Times
    System.out.println("RUN COUNT: " + runTimes.size());
    System.out.println(
        "MIN RUN: " + runTimes.stream().mapToLong(Long::longValue).min().getAsLong());
    System.out.println(
        "MAX RUN: " + runTimes.stream().mapToLong(Long::longValue).max().getAsLong());
    System.out.println(
        "AVG RUN: " + runTimes.stream().mapToLong(Long::longValue).average().getAsDouble());
    System.out.println("FRL COUNT: " + forallTimes.size());
    System.out.println(
        "MIN FRL: " + forallTimes.stream().mapToLong(Long::longValue).min().getAsLong());
    System.out.println(
        "MAX FRL: " + forallTimes.stream().mapToLong(Long::longValue).max().getAsLong());
    System.out.println(
        "AVG FRL: " + forallTimes.stream().mapToLong(Long::longValue).average().getAsDouble());

    Long totalTime = System.currentTimeMillis() - totalStart;
    System.out.println("TOTAL TIME: " + totalTime);
    System.out.format(
        "%% RUNs %.2f\n", 100.0 * runTimes.stream().mapToLong(Long::longValue).sum() / totalTime);
    System.out.format(
        "%% FRL %.2f\n", 100.0 * forallTimes.stream().mapToLong(Long::longValue).sum() / totalTime);
    return new SmtManyAnswerElement(results);
  }

  public AnswerElement checkDstDiff(
      HeaderQuestion question,
      Pattern routerRegex,
      Prefix prefix,
      int maxLength,
      String ignoreInterfaces) {
    long totalStart = System.currentTimeMillis();
    Graph graph = new Graph(_batfish);
    List<String> routers = PatternUtils.findMatchingNodes(graph, routerRegex, Pattern.compile(""));
    if (routers.size() > 2) {
      System.out.println(
          "Only comparing first 2 routers: " + routers.get(0) + " " + routers.get(1));
      routers = Arrays.asList(routers.get(0), routers.get(1));
    }
    System.out.println("Comparing routers: " + routers.get(0) + " and " + routers.get(1));

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

    // Exclude ignored IP addresses
    BoolExpr ignored;
    if (ignoreInterfaces.equalsIgnoreCase("subnet")) {
      // Ignore subnets of every interface
      ignored =
          ignoreInterfaceSubnets(
              ctx, graph, encoder1.getMainSlice().getSymbolicPacket().getDstIp(), routers);
    } else if (ignoreInterfaces.equalsIgnoreCase("exact")) {
      // Ignore exact IP address of interfaces
      ignored =
          ignoreExactInterfaces(
              ctx, graph, encoder2.getMainSlice().getSymbolicPacket().getDstIp(), routers);
    } else if (ignoreInterfaces.equalsIgnoreCase("none")) {
      // Don't ignore
      ignored = ctx.mkTrue();
    } else {
      throw new IllegalArgumentException(
          "Illegal value for ignoreInterfaces: '" + ignoreInterfaces + "'\n");
    }
    encoder2.add(ignored);

    // Equal Environments on adjacent links
    BoolExpr equalEnv = getEqualEnv(encoder1, encoder2, matchingInterfaces);
    encoder2.add(equalEnv);

    // Constraints on forwarding
    BoolExpr sameForwarding = getEqualForwarding(encoder1, encoder2, routers, matchingInterfaces);

    // Constraints on incoming ACL
    BoolExpr sameIncomingACL =
        getIncomingACLEquivalence(
            ctx, encoder1.getMainSlice(), encoder2.getMainSlice(), matchingInterfaces);

    // Constraints on route exports
    BoolExpr sameRouteExport =
        getRouteExportEquivalence(
            ctx, encoder1.getMainSlice(), encoder2.getMainSlice(), matchingInterfaces);

    BoolExpr sameBehavior = ctx.mkAnd(sameForwarding, sameIncomingACL, sameRouteExport);

    BoolExpr[] sameInputConstraints =
        Arrays.stream(encoder2.getSolver().getAssertions())
            .map(Expr::simplify)
            .toArray(BoolExpr[]::new);

    encoder2.add(ctx.mkNot(ctx.mkAnd(sameBehavior)));

    // Creating solver to test that all dstIps in prefix have a difference
    Solver dstNoneEquivalent = ctx.mkSolver();
    SymbolicPacket packet = encoder2.getMainSlice().getSymbolicPacket();
    BitVecExpr dstIp = packet.getDstIp();
    BitVecExpr srcIp = packet.getSrcIp();

    Expr[] notDstVars =
        encoder2
            .getAllVariables()
            .values()
            .stream()
            .filter(expr -> !expr.equals(dstIp))
            .toArray(Expr[]::new);

    BoolExpr sameInputs = ctx.mkAnd(sameInputConstraints);
    BoolExpr implies = ctx.mkImplies(sameInputs, sameBehavior);
    BoolExpr forall = ctx.mkForall(notDstVars, implies, 1, null, null, null, null);
    dstNoneEquivalent.add(forall);
    dstNoneEquivalent.add(ignored);

    TreeMap<String, VerificationResult> results = new TreeMap<>();
    List<Long> runTimes = new ArrayList<>();
    List<Long> forallTimes = new ArrayList<>();

    ArrayDeque<SrcDstPair> dstPrefixQueue = new ArrayDeque<>();
    dstPrefixQueue.push(new SrcDstPair(Prefix.parse("0.0.0.0/0"), prefix));
    Set<SrcDstPair> similarities = new HashSet<>();
    Set<SrcDstPair> differences = new HashSet<>();
    int prevDstDepth = 0;

    // Loop to test longer dst prefixes
    while (!dstPrefixQueue.isEmpty()) {
      SrcDstPair sdp = dstPrefixQueue.pop();
      Prefix currSrcPrefix = sdp.src;
      Prefix currDstPrefix = sdp.dst;
      int dstShift = 32 - currDstPrefix.getPrefixLength();
      int srcShift = 32 - currSrcPrefix.getPrefixLength();
      if (currDstPrefix.getPrefixLength() > prevDstDepth) {
        prevDstDepth = currDstPrefix.getPrefixLength();
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

      encoder2.getSolver().pop();

      // Check that no ip address equivalent for destinations
      if (!result.isVerified() && currDstPrefix.getPrefixLength() < maxLength) {
        dstNoneEquivalent.push();
        dstNoneEquivalent.add(matchDstPrefix);

        long forallStartTime = System.currentTimeMillis();
        Status neStatus = dstNoneEquivalent.check();
        long forallEndTime = System.currentTimeMillis();
        forallTimes.add(forallEndTime - forallStartTime);
        // System.out.println("FRL: " + (forallEndTime - forallStartTime));

        if (neStatus.equals(Status.UNSATISFIABLE)) {
          differences.add(sdp);
          results.put(sdp.toString(), result);
        } else {
          if (neStatus.equals(Status.UNKNOWN)) {
            System.out.println("Unknown");
          }
          dstPrefixQueue.addAll(genLongerDstPrefix(sdp));
        }
        dstNoneEquivalent.pop();
      } else if (result.isVerified()) {
        similarities.add(sdp);
      } else if (currDstPrefix.getPrefixLength() >= maxLength) {
        differences.add(sdp);
        results.put(sdp.toString(), result);
      }
    }

    System.out.println("--------- DIFFERENCES --------");
    Map<Prefix, Set<Prefix>> differencesBySource = separateBySource(differences);
    for (Entry<Prefix, Set<Prefix>> entry : differencesBySource.entrySet()) {
      System.out.println("DST");
      Set<Prefix> destinations = entry.getValue();
      Set<Prefix> noParallels = removeParallelLinks(destinations, matchingInterfaces);
      noParallels.forEach(System.out::println);
      System.out.println("----");
      minimizePrefixes(entry.getValue());
    }
    System.out.println("------------------------------");
    System.out.println("--------- SIMILARITIES -------");
    Map<Prefix, Set<Prefix>> verifiedBySource = separateBySource(similarities);
    for (Entry<Prefix, Set<Prefix>> entry : verifiedBySource.entrySet()) {
      System.out.println("DST");
      minimizePrefixes(entry.getValue());
      // Set<Prefix> dstDiff =
      // differences.stream().map(SrcDstPair::getDst).collect(Collectors.toSet());
      // Set<Prefix> dstSiml =
      // similarities.stream().map(SrcDstPair::getDst).collect(Collectors.toSet());
      // new PrefixMinimize().minimizePrefixWithNesting(dstSiml, dstDiff);
    }
    System.out.println("------------------------------");

    // Print Times
    /* System.out.println("RUN COUNT: " + runTimes.size());
        System.out.println("MIN RUN: " + runTimes.stream().mapToLong(Long::longValue).min().getAsLong());
        System.out.println("MAX RUN: " + runTimes.stream().mapToLong(Long::longValue).max().getAsLong());
        System.out.println("AVG RUN: " + runTimes.stream().mapToLong(Long::longValue).average().getAsDouble());
        System.out.println("FRL COUNT: " + forallTimes.size());
        System.out.println("MIN FRL: " + forallTimes.stream().mapToLong(Long::longValue).min().getAsLong());
        System.out.println("MAX FRL: " + forallTimes.stream().mapToLong(Long::longValue).max().getAsLong());
        System.out.println("AVG FRL: " + forallTimes.stream().mapToLong(Long::longValue).average().getAsDouble());

        Long totalTime = System.currentTimeMillis() - totalStart;
        System.out.println("TOTAL TIME: " + totalTime);
        System.out.format("%% RUNs %.2f\n", 100.0 * runTimes.stream().mapToLong(Long::longValue).sum() / totalTime);
        System.out.format("%% FRL %.2f\n", 100.0* forallTimes.stream().mapToLong(Long::longValue).sum() / totalTime);
    */
    return new SmtManyAnswerElement(results);
  }

  private class SrcDstPair {
    public Prefix src;
    public Prefix dst;

    SrcDstPair(Prefix s, Prefix d) {
      src = s;
      dst = d;
    }

    public Prefix getSrc() {
      return src;
    }

    public Prefix getDst() {
      return dst;
    }

    @Override
    public String toString() {
      return src.toString() + " " + dst.toString();
    }
  }

  Map<Prefix, Set<Prefix>> separateBySource(Set<SrcDstPair> set) {
    Map<Prefix, Set<Prefix>> ret =
        set.stream()
            .collect(
                Collectors.groupingBy(
                    SrcDstPair::getSrc,
                    Collectors.mapping(SrcDstPair::getDst, Collectors.toSet())));
    return ret;
  }

  /*
  Create longer prefixes that cover an original dst prefix
   */
  private Set<SrcDstPair> genLongerDstPrefix(SrcDstPair sdp) {
    Set<SrcDstPair> ret = new HashSet<>();
    long dstBits = sdp.dst.getStartIp().asLong();
    int dstLength = sdp.dst.getPrefixLength();
    for (int i = 0; i < 2; i++) {
      long nextDstBit = 1L << (31 - dstLength);
      Prefix newDst = new Prefix(new Ip(dstBits + i * nextDstBit), dstLength + 1);
      ret.add(new SrcDstPair(sdp.src, newDst));
    }
    return ret;
  }
  /*
   Create longer prefixes that cover an original src prefix
  */
  private Set<SrcDstPair> genLongerSrcPrefix(SrcDstPair sdp) {
    Set<SrcDstPair> ret = new HashSet<>();
    long srcBits = sdp.src.getStartIp().asLong();
    int srcLength = sdp.src.getPrefixLength();
    for (int i = 0; i < 2; i++) {
      long nextDstBit = 1L << (31 - srcLength);
      Prefix newSrc = new Prefix(new Ip(srcBits + i * nextDstBit), srcLength + 1);
      ret.add(new SrcDstPair(newSrc, sdp.dst));
    }
    return ret;
  }

  /*
   * Creates a boolean variable representing destinations we don't want
   * to consider due to local differences. This covers only the exact interface ip.
   */
  private BoolExpr ignoreExactInterfaces(
      Context ctx, Graph graph, Expr dstIp, List<String> routers) {
    BoolExpr validDest = ctx.mkBool(true);
    for (GraphEdge ge : graph.getAllEdges()) {
      if (routers.contains(ge.getRouter())) {
        long address = ge.getStart().getAddress().getIp().asLong();
        validDest = ctx.mkAnd(validDest, ctx.mkNot(ctx.mkEq(dstIp, ctx.mkBV(address, 32))));
      }
    }
    return validDest;
  }

  /*
   * Creates a boolean variable representing destinations we don't want
   * to consider due to local differences. This covers interfaces and their subnets
   */
  private BoolExpr ignoreInterfaceSubnets(
      Context ctx, Graph graph, BitVecExpr dstIp, List<String> routers) {
    BoolExpr validDest = ctx.mkBool(true);
    for (GraphEdge ge : graph.getAllEdges()) {
      if (routers.contains(ge.getRouter())) {
        long address = ge.getStart().getAddress().getIp().asLong();
        long maskLen = ge.getStart().getAddress().getPrefix().getPrefixLength();

        BitVecExpr dstPfxIpExpr = ctx.mkBV(address, 32);
        BitVecExpr dstShiftExpr = ctx.mkBV(32 - maskLen, 32);
        BoolExpr notMatchDstPrefix =
            ctx.mkNot(
                ctx.mkEq(
                    ctx.mkBVLSHR(dstIp, dstShiftExpr), ctx.mkBVLSHR(dstPfxIpExpr, dstShiftExpr)));
        validDest = ctx.mkAnd(validDest, notMatchDstPrefix);
      }
    }
    return validDest;
  }

  /*
   * Gets a set of prefixes that covers the address space contained in a but not in b
   */
  private Set<Prefix> subtractPrefix(Prefix a, Prefix b) {
    Set<Prefix> result = new TreeSet<>();
    if (!a.containsPrefix(b)) {
      result.add(a);
      return result;
    }
    long pfx = b.getStartIp().asLong();
    for (int len = a.getPrefixLength() + 1; len <= b.getPrefixLength(); len++) {
      long bit = 1 << (32 - len);
      result.add(new Prefix(new Ip(pfx ^ bit), len));
    }
    return result;
  }

  /*
   * Takes the address space contained in r and removes the parallel interfaces from the matching
   */
  private Set<Prefix> removeParallelLinks(
      Set<Prefix> r, Map<GraphEdge, GraphEdge> matchingInterfaces) {
    List<Prefix> matchDifferences = new ArrayList<>();
    for (Entry<GraphEdge, GraphEdge> ent : matchingInterfaces.entrySet()) {
      Prefix pfx1 = ent.getKey().getStart().getAddress().getPrefix();
      Prefix pfx2 = ent.getValue().getStart().getAddress().getPrefix();
      matchDifferences.add(pfx1);
      matchDifferences.add(pfx2);
    }

    Set<Prefix> result = new TreeSet<>();

    Set<Prefix> done = new TreeSet<>(r);
    for (Prefix ignored : matchDifferences) {
      Queue<Prefix> inputPrefixes = new ArrayDeque<>(done);
      done = new TreeSet<>();
      while (!inputPrefixes.isEmpty()) {
        Prefix pfx = inputPrefixes.poll();
        if (pfx.containsPrefix(ignored)) {
          Set<Prefix> diff = subtractPrefix(pfx, ignored);
          inputPrefixes.addAll(diff);
        } else {
          done.add(pfx);
        }
      }
    }
    return done;
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
    /*System.out.println("Slice 1:");
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
            });*/
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

    // System.out.println(ret.simplify());

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
        //        System.out.println("-------------- COMMUNITIES -------------");
        for (CommunityVar cv : commSet) {
          //          System.out.println(cv);
          if (comm1.containsKey(cv) && comm2.containsKey(cv)) {
            //            System.out.println(comm1.get(cv) + " " + comm2.get(cv));
            ret = ctx.mkAnd(ret, ctx.mkEq(comm1.get(cv), comm2.get(cv)));
          }
        }
      }
      return ret;
    }
    return ctx.mkTrue();
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

  /*
  Convert a set of prefixes into a union of differences with the least number of terms
   */
  private void minimizePrefixes(Set<Prefix> differences) {
    if (differences.isEmpty()) {
      return;
    }

    // Prefixes in the final union
    Set<Prefix> resultPrefixes = new TreeSet<>(differences);

    // Prefixes removed from the subtree represented by the key prefix
    Map<Prefix, Set<Prefix>> subtracted = new TreeMap<>();

    // Prefixes included at the subtree represented by the key prefix
    Map<Prefix, Set<Prefix>> added = new TreeMap<>();

    // How many extra terms a P - X representation has for the subtree of prefix P
    Map<Prefix, Integer> distanceFromOptimal = new TreeMap<>();
    for (Prefix pfx : differences) {
      added.put(pfx, new TreeSet<>());
      added.get(pfx).add(pfx);
      distanceFromOptimal.put(pfx, 0);
    }

    // Used to sort prefixes from longest to shortest
    Comparator<Prefix> pfxLongToShort =
        (prefix1, prefix2) -> {
          int ret = Integer.compare(prefix1.getPrefixLength(), prefix2.getPrefixLength());
          if (ret != 0) {
            return -ret;
          }
          return prefix1.getStartIp().compareTo(prefix2.getStartIp());
        };

    PriorityQueue<Prefix> prefixesToCheck = new PriorityQueue<>(differences.size(), pfxLongToShort);
    prefixesToCheck.addAll(differences);

    int minOptLength =
        differences.stream().map(Prefix::getPrefixLength).min(Integer::compareTo).orElse(0);

    while (!prefixesToCheck.isEmpty()) {
      Prefix currPfx = prefixesToCheck.poll();
      Prefix parentPfx = new Prefix(currPfx.getStartIp(), currPfx.getPrefixLength() - 1);
      if (distanceFromOptimal.containsKey(parentPfx)) {
        continue;
      }
      Prefix neighborPfx = getNeighborPfx(currPfx);

      int currDistance = distanceFromOptimal.get(currPfx);
      int neighborDistance = distanceFromOptimal.getOrDefault(neighborPfx, 2);
      int parentDistance = currDistance + neighborDistance - 1;
      if (parentDistance < 0) {
        parentDistance = 0;
      }
      distanceFromOptimal.put(parentPfx, parentDistance);

      if (!subtracted.containsKey(parentPfx)) {
        subtracted.put(parentPfx, new TreeSet<>());
      }
      subtracted.get(parentPfx).addAll(subtracted.getOrDefault(currPfx, new TreeSet<>()));
      subtracted.get(parentPfx).addAll(subtracted.getOrDefault(neighborPfx, new TreeSet<>()));
      if (!distanceFromOptimal.containsKey(neighborPfx)) {
        subtracted.get(parentPfx).add(neighborPfx);
      }
      if (!added.containsKey(parentPfx)) {
        added.put(parentPfx, new TreeSet<>());
      }

      if (parentDistance == 0) {
        resultPrefixes.add(parentPfx);
        resultPrefixes.removeAll(added.getOrDefault(currPfx, new TreeSet<>()));
        resultPrefixes.removeAll(added.getOrDefault(neighborPfx, new TreeSet<>()));
        resultPrefixes.remove(currPfx);
        resultPrefixes.remove(neighborPfx);
        added.get(parentPfx).add(parentPfx);
        if (parentPfx.getPrefixLength() < minOptLength && parentPfx.getPrefixLength() > 0) {
          minOptLength = parentPfx.getPrefixLength();
        }
      } else {
        added.get(parentPfx).addAll(added.get(currPfx));
        added.get(parentPfx).addAll(added.getOrDefault(neighborPfx, new TreeSet<>()));
      }
      if (parentPfx.getPrefixLength() > 0) {
        prefixesToCheck.add(parentPfx);
      }
      /*
            System.out.println("****************");
            System.out.println(currPfx + " " + currDistance);
            System.out.println(neighborPfx + " " + neighborDistance);
            System.out.println(parentPfx + " " + parentDistance);
            System.out.println("*****");
            System.out.println(added);
            System.out.println(subtracted);
            resultPrefixes.stream().sorted(pfxLongToShort).forEach(System.out::println);
            System.out.println("****************");
      */
    }
    /*    System.out.println("======== R1 =======");
    for (Prefix pfx : resultPrefixes) {
      System.out.println(pfx);
      subtracted.getOrDefault(pfx, new TreeSet<>()).forEach(p -> System.out.println(" - " + p));
    }*/

    // System.out.println("======== R2 =======");
    for (Prefix pfx : added.get(new Prefix(new Ip(0), 0))) {
      System.out.println(pfx);
      subtracted.getOrDefault(pfx, new TreeSet<>()).forEach(p -> System.out.println(" - " + p));
    }
  }

  private Prefix getNeighborPfx(Prefix pfx) {
    Long bits = pfx.getStartIp().asLong();
    Long flippedBit = 1L << (32 - pfx.getPrefixLength());
    return new Prefix(new Ip(bits ^ flippedBit), pfx.getPrefixLength());
  }
}

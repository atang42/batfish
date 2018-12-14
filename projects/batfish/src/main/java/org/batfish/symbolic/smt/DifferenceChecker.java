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
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.smt.DifferenceQuestion;
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

  static private class IntfDecl {
    public enum IntfType {
      ETHERNET,
      LOOPBACK,
      MANAGEMENT,
      VLAN,
      CHANNEL,
      NULL_INTF,
      OTHER,
      DUMMY,
    }

    public IntfType type;
    public String strPart;
    public String numPart;
    public InterfaceAddress address;

    public IntfDecl(GraphEdge edge) {
      String intfName = edge.getStart().getName();
      InterfaceAddress intfAddr = edge.getStart().getAddress();

      strPart = intfName.replaceAll("[^A-Za-z]", "");
      numPart = intfName.replaceAll("[A-Za-z]", "");
      address = intfAddr;

      if (strPart.toLowerCase().contains("ethernet")) {
        type = IntfType.ETHERNET;
      } else if (strPart.toLowerCase().contains("loopback")) {
        type = IntfType.LOOPBACK;
      } else if (strPart.toLowerCase().contains("management")
              || strPart.toLowerCase().contains("mgmt")) {
        type = IntfType.MANAGEMENT;
      } else if (strPart.toLowerCase().contains("vlan")) {
        type = IntfType.VLAN;
      } else if (strPart.toLowerCase().contains("null")) {
        type = IntfType.NULL_INTF;
      } else if (strPart.toLowerCase().contains("channel")) {
        type = IntfType.CHANNEL;
      } else {
        type = IntfType.OTHER;
      }

    }


    public double getNoMatchCost() {
      if (type == IntfType.VLAN) {
        return VLAN_NO_MATCH_COST;
      } else if (type == IntfType.ETHERNET) {
        return ETHERNET_NO_MATCH_COST;
      }
      return DEFAULT_NO_MATCH_COST;
    }

    public static final int MAX_DISTANCE = 10000;

    public static final double VLAN_NO_MATCH_COST = 1;
    public static final double ETHERNET_NO_MATCH_COST = 500;
    public static final double DEFAULT_NO_MATCH_COST = MAX_DISTANCE / 2;

    public static double intfNameDist(IntfDecl decl1, IntfDecl decl2) {
      if (decl1.type != decl2.type || decl1.type == IntfType.NULL_INTF
          || decl2.type == IntfType.NULL_INTF) {
        return MAX_DISTANCE;
      }
      String nums1[] = decl1.numPart.split("[^0-9]");
      String nums2[] = decl2.numPart.split("[^0-9]");

      if(decl1.type == IntfType.ETHERNET) {

        // Arbitrary numbers, hopefully make reasonable in future
        double sum1, sum2;
        if (nums1.length == 3) {
          sum1 = 200 * Integer.parseInt(nums1[0]) + 50 * Integer.parseInt(nums1[1]) + Integer.parseInt(nums1[2]);
        } else if (nums1.length == 2){
          sum1 = 200 * Integer.parseInt(nums1[0]) + Integer.parseInt(nums1[1]);
        } else {
          sum1 = Arrays.stream(nums1).mapToInt(Integer::parseInt).sum();
        }
        if (nums2.length == 3) {
          sum2 = 200 * Integer.parseInt(nums2[0]) + 50 * Integer.parseInt(nums2[1]) + Integer.parseInt(nums2[2]);
        } else if (nums2.length == 2){
          sum2 = 200 * Integer.parseInt(nums2[0]) + Integer.parseInt(nums2[1]);
        } else {
          sum2 = Arrays.stream(nums2).mapToInt(Integer::parseInt).sum();
        }
        return Math.pow(Math.abs(sum2 - sum1), 1.00001);
      } else {
        double sum = 0;
        for (int i = 0; i < nums1.length; i++) {
          // Exponent acts as tiebreak between equal distances
          // This way, consistent differences are preferred over inconsistent differences
          sum +=
              Math.pow(Math.abs(Integer.parseInt(nums1[i]) - Integer.parseInt(nums2[i])), 1.00001);
        }
        return sum;
      }
    }
  }

  private Map<GraphEdge, GraphEdge> getInterfaceMatchGroupedByName(Encoder encoder, List<GraphEdge> edges1, List<GraphEdge> edges2) {
    Map<GraphEdge, GraphEdge> matchingInterfaces = new HashMap<>();

    /*
    Name based matching:
    Matches interfaces with the same name
     */

    Map<GraphEdge, IntfDecl> edgeToDecl = new HashMap<>();
    for(GraphEdge edge : edges1) {
      edgeToDecl.put(edge, new IntfDecl(edge));
    }
    for(GraphEdge edge : edges2) {
      edgeToDecl.put(edge, new IntfDecl(edge));
    }

    int sumLen = edges1.size() + edges2.size();
    double[][] edgeDistances = new double[sumLen][sumLen];

    for (int i = 0; i < edges1.size(); i++) {
      GraphEdge edge1 = edges1.get(i);
      IntfDecl decl1 = edgeToDecl.get(edge1);
      for (int j = 0; j < edges2.size(); j++) {
        GraphEdge edge2 = edges2.get(j);
        IntfDecl decl2 = edgeToDecl.get(edge2);
        edgeDistances[i][j] = IntfDecl.intfNameDist(decl1, decl2);
      }
      for (int j = edges2.size(); j < sumLen; j++) {
        edgeDistances[i][j] = decl1.getNoMatchCost();
      }
    }
    for (int i = edges1.size(); i < sumLen; i++) {
      for (int j = 0; j < edges2.size(); j++) {
        GraphEdge edge2 = edges2.get(j);
        IntfDecl decl2 = edgeToDecl.get(edge2);
        edgeDistances[i][j] = decl2.getNoMatchCost();
      }
      for (int j = edges2.size(); j < sumLen; j++) {
        edgeDistances[i][j] = 0;
      }
    }

    // Hungarian matching algorithm
    HungarianAlgorithm bestMatch = new HungarianAlgorithm(edgeDistances);
    int[] result = bestMatch.execute();

    // Get match
    for(int i = 0; i < edges1.size(); i++) {
      GraphEdge edge1 = edges1.get(i);
      if (result[i] >= edges2.size() || result[i] == -1) {
        System.err.println("interfaces do not match: " + edge1.getRouter() + " " + edge1 + " " + i + " " + result[i]);
      } else {
        GraphEdge edge2 = edges2.get(result[i]);
        matchingInterfaces.put(edge1, edge2);
      }
    }
    for(int j = 0; j < edges2.size(); j++) {
      boolean found = false;
      for (int i = 0; i < edges1.size(); i++) {
        if (result[i] == j) {
          found = true;
          break;
        }
      }
      if (!found) {
        GraphEdge edge2 = edges2.get(j);
        System.err.println("interfaces do not match: " + edge2.getRouter() + " " + edge2 + " " + j);
      }
    }

    return matchingInterfaces;
  }

  private Map<GraphEdge, GraphEdge> getInterfaceMatch(String router1, String router2,
      List<GraphEdge> edges1, List<GraphEdge> edges2) {
    Map<GraphEdge, GraphEdge> matchingInterfaces;
    Map<String, GraphEdge> interfaces1 =
        edges1
            .stream()
            .collect(Collectors.toMap(ge -> ge.getStart().getName(), Function.identity()));
    Map<String, GraphEdge> interfaces2 =
        edges2
            .stream()
            .collect(Collectors.toMap(ge -> ge.getStart().getName(), Function.identity()));

      /*
      Name based matching:
      Matches interfaces with the same name
       */

      // Split by string part
    Map<String, Set<String>> intfByType1 = new TreeMap<>();
    for (String s : interfaces1.keySet()) {
      String sPart = s.replaceAll("[^A-Za-z]", "");
      String nPart = s.replaceAll("[A-Za-z]", "");
      Set<String> updatedSet = intfByType1.getOrDefault(sPart, new TreeSet<>());
      updatedSet.add(nPart);
      intfByType1.put(sPart, updatedSet);
    }
    Map<String, Set<String>> intfByType2 = new TreeMap<>();
    for (String s : interfaces2.keySet()) {
      String sPart = s.replaceAll("[^A-Za-z]", "");
      String nPart = s.replaceAll("[A-Za-z]", "");
      Set<String> updatedSet = intfByType2.getOrDefault(sPart, new TreeSet<>());
      updatedSet.add(nPart);
      intfByType2.put(sPart, updatedSet);
    }

    matchingInterfaces = new HashMap<>();
    for (GraphEdge edge : edges1) {
      String sPart = edge.getStart().getName().replaceAll("[^A-Za-z]", "");
      String nPart = edge.getStart().getName().replaceAll("[A-Za-z]", "");
      if (intfByType2.containsKey(sPart) && intfByType2.get(sPart).contains(nPart)) {
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

    return matchingInterfaces;
  }

  /*
  Creates constraint for same environment between router in slice of encoder1 and slice of encoder2
  or no environment for unmatched interfaces.
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

    // No environment for unmatched interfaces
    for (Entry<LogicalEdge, SymbolicRoute> ent :
        encoder1.getMainSlice().getLogicalGraph().getEnvironmentVars().entrySet()) {
      if (!matchingInterfaces.containsKey(ent.getKey().getEdge())) {
        BoolExpr notPermitted = ctx.mkNot(ent.getValue().getPermitted());
        ret = ctx.mkAnd(ret, notPermitted);
      }
    }
    for (Entry<LogicalEdge, SymbolicRoute> ent :
        encoder2.getMainSlice().getLogicalGraph().getEnvironmentVars().entrySet()) {
      if (!matchingInterfaces.containsValue(ent.getKey().getEdge())) {
        BoolExpr notPermitted = ctx.mkNot(ent.getValue().getPermitted());
        ret = ctx.mkAnd(ret, notPermitted);
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

  public BoolExpr matchPacket(SymbolicPacket packet, SrcDstPair sdp, Context ctx) {
    Prefix currDstPrefix = sdp.dst;
    Prefix currSrcPrefix = sdp.src;
    SubRange currDstPort = sdp.dstPorts;
    SubRange currSrcPort = sdp.srcPorts;

    BitVecExpr dstIp = packet.getDstIp();
    BitVecExpr srcIp = packet.getSrcIp();
    ArithExpr srcPort = packet.getSrcPort();
    ArithExpr dstPort = packet.getDstPort();
    int dstShift = 32 - currDstPrefix.getPrefixLength();
    int srcShift = 32 - currSrcPrefix.getPrefixLength();

    // Add constraint on prefix
    BitVecExpr dstPfxIpExpr = ctx.mkBV(currDstPrefix.getStartIp().asLong(), 32);
    BitVecExpr dstShiftExpr = ctx.mkBV(dstShift, 32);
    BoolExpr matchDstPrefix =
        ctx.mkEq(ctx.mkBVLSHR(dstIp, dstShiftExpr), ctx.mkBVLSHR(dstPfxIpExpr, dstShiftExpr));

    BitVecExpr srcPfxIpExpr = ctx.mkBV(currSrcPrefix.getStartIp().asLong(), 32);
    BitVecExpr srcShiftExpr = ctx.mkBV(srcShift, 32);
    BoolExpr matchSrcPrefix =
        ctx.mkEq(ctx.mkBVLSHR(srcIp, srcShiftExpr), ctx.mkBVLSHR(srcPfxIpExpr, srcShiftExpr));

    // Add constraints on port numbers
    ArithExpr minSrcPort = ctx.mkInt(currSrcPort.getStart());
    ArithExpr maxSrcPort = ctx.mkInt(currSrcPort.getEnd());
    BoolExpr matchSrcPort = ctx.mkAnd(ctx.mkLe(minSrcPort, srcPort), ctx.mkLe(srcPort, maxSrcPort));

    ArithExpr minDstPort = ctx.mkInt(currDstPort.getStart());
    ArithExpr maxDstPort = ctx.mkInt(currDstPort.getEnd());
    BoolExpr matchDstPort = ctx.mkAnd(ctx.mkLe(minDstPort, dstPort), ctx.mkLe(dstPort, maxDstPort));

    return ctx.mkAnd(matchDstPort, matchDstPrefix, matchSrcPort, matchSrcPrefix);
  }

  public TreeMap<String, VerificationResult> searchAllDst(
      DifferenceQuestion question,
      Encoder encoder,
      BoolExpr noneConstraint,
      BoolExpr ignored,
      Map<GraphEdge, GraphEdge> matchingInterfaces) {
    long before = System.currentTimeMillis();
    TreeMap<String, VerificationResult> results = new TreeMap<>();
    Context ctx = encoder.getCtx();

    Prefix dstStart = Prefix.parse(question.getDstPrefix());
    Prefix srcPrefix = Prefix.parse(question.getSrcPrefix());
    SubRange srcPortRange = question.getSrcPortRange();
    SubRange dstPortRange = question.getDstPortRange();
    int maxLength = question.getMaxLength();

    BitVecExpr dstIp = encoder.getMainSlice().getSymbolicPacket().getDstIp();
    BitVecExpr srcIp = encoder.getMainSlice().getSymbolicPacket().getSrcIp();

    ArrayDeque<SrcDstPair> dstPrefixQueue = new ArrayDeque<>();
    dstPrefixQueue.push(new SrcDstPair(srcPrefix, dstStart, srcPortRange, dstPortRange));
    Set<SrcDstPair> similarities = new HashSet<>();
    Set<SrcDstPair> differences = new HashSet<>();
    int prevDstDepth = 0;

    Solver dstNoneEquivalent = ctx.mkSolver();
    Expr[] notDstVars =
        encoder
            .getAllVariables()
            .values()
            .stream()
            .filter(expr -> !expr.equals(dstIp))
            .toArray(Expr[]::new);

    // Loop to test longer dst prefixes
    while (!dstPrefixQueue.isEmpty()) {
      SrcDstPair sdp = dstPrefixQueue.pop();
      Prefix currDstPrefix = sdp.dst;
      int dstShift = 32 - currDstPrefix.getPrefixLength();
      if (currDstPrefix.getPrefixLength() > prevDstDepth) {
        prevDstDepth = currDstPrefix.getPrefixLength();
      }
      encoder.getSolver().push();

      BoolExpr matchPacketConstraints =
          matchPacket(encoder.getMainSlice().getSymbolicPacket(), sdp, ctx);
      encoder.add(matchPacketConstraints);

      org.batfish.symbolic.smt.VerificationResult result = encoder.verify().getFirst();

      encoder.getSolver().pop();

      // Check that no ip address equivalent for destinations
      if (!result.isVerified() && currDstPrefix.getPrefixLength() < maxLength) {
        dstNoneEquivalent.push();

        BoolExpr fullConstraint = ctx.mkImplies(matchPacketConstraints, noneConstraint);
        BoolExpr forall = ctx.mkForall(notDstVars, fullConstraint, 1, null, null, null, null);
        dstNoneEquivalent.add(forall);
        dstNoneEquivalent.add(ignored);

        BitVecExpr dstPfxIpExpr = ctx.mkBV(currDstPrefix.getStartIp().asLong(), 32);
        BitVecExpr dstShiftExpr = ctx.mkBV(dstShift, 32);
        BoolExpr matchDstPrefix =
            ctx.mkEq(ctx.mkBVLSHR(dstIp, dstShiftExpr), ctx.mkBVLSHR(dstPfxIpExpr, dstShiftExpr));
        dstNoneEquivalent.add(matchDstPrefix);


        Status neStatus = dstNoneEquivalent.check();
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

    printDstDifferences(differences, similarities, matchingInterfaces);
    return results;
  }

  public TreeMap<String, VerificationResult> searchAllSrc(
      DifferenceQuestion question,
      Encoder encoder,
      BoolExpr noneConstraint,
      BoolExpr ignored,
      Map<GraphEdge, GraphEdge> matchingInterfaces) {
    TreeMap<String, VerificationResult> results = new TreeMap<>();
    Context ctx = encoder.getCtx();
    Prefix dstPrefix = Prefix.parse(question.getDstPrefix());
    Prefix srcStart = Prefix.parse(question.getSrcPrefix());
    SubRange srcPortRange = question.getSrcPortRange();
    SubRange dstPortRange = question.getDstPortRange();
    int maxLength = question.getMaxLength();

    BitVecExpr srcIp = encoder.getMainSlice().getSymbolicPacket().getSrcIp();

    ArrayDeque<SrcDstPair> srcPrefixQueue = new ArrayDeque<>();
    srcPrefixQueue.push(new SrcDstPair(srcStart, dstPrefix, srcPortRange, dstPortRange));
    Set<SrcDstPair> similarities = new HashSet<>();
    Set<SrcDstPair> differences = new HashSet<>();

    Solver srcNoneEquivalent = ctx.mkSolver();
    Expr[] notSrcVars =
        encoder
            .getAllVariables()
            .values()
            .stream()
            .filter(expr -> !expr.equals(srcIp))
            .toArray(Expr[]::new);

    // Loop to test longer src prefixes
    long makeSolverTime = 0;
    long solverCheckTime = 0;
    while (!srcPrefixQueue.isEmpty()) {
      SrcDstPair sdp = srcPrefixQueue.pop();
      Prefix currSrcPrefix = sdp.src;
      int srcShift = 32 - currSrcPrefix.getPrefixLength();
      encoder.getSolver().push();

      BoolExpr matchPacketConstraints =
          matchPacket(encoder.getMainSlice().getSymbolicPacket(), sdp, ctx);
      encoder.add(matchPacketConstraints);

      org.batfish.symbolic.smt.VerificationResult result = encoder.verify().getFirst();

      encoder.getSolver().pop();


      // Check that no ip address equivalent for destinations
      if (!result.isVerified() && currSrcPrefix.getPrefixLength() < maxLength) {
        srcNoneEquivalent.push();

        long before = System.currentTimeMillis();
        BoolExpr fullConstraints = ctx.mkImplies(matchPacketConstraints, noneConstraint);
        BoolExpr forall = ctx.mkForall(notSrcVars, fullConstraints, 1, null, null, null, null);
        srcNoneEquivalent.add(forall);
        srcNoneEquivalent.add(ignored);

        BitVecExpr srcPfxIpExpr = ctx.mkBV(currSrcPrefix.getStartIp().asLong(), 32);
        BitVecExpr srcShiftExpr = ctx.mkBV(srcShift, 32);
        BoolExpr matchSrcPrefix =
            ctx.mkEq(ctx.mkBVLSHR(srcIp, srcShiftExpr), ctx.mkBVLSHR(srcPfxIpExpr, srcShiftExpr));
        srcNoneEquivalent.add(matchSrcPrefix);
        long after = System.currentTimeMillis();
        makeSolverTime += after - before;

        before = System.currentTimeMillis();
        Status neStatus = srcNoneEquivalent.check();
        after = System.currentTimeMillis();
        solverCheckTime += after - before;
        if (neStatus.equals(Status.UNSATISFIABLE)) {
          differences.add(sdp);
          results.put(sdp.toString(), result);
        } else {
          if (neStatus.equals(Status.UNKNOWN)) {
            System.out.println("Unknown");
          }
          srcPrefixQueue.addAll(genLongerSrcPrefix(sdp));
        }
        srcNoneEquivalent.pop();
      } else if (result.isVerified()) {
        similarities.add(sdp);
      } else if (currSrcPrefix.getPrefixLength() >= maxLength) {
        differences.add(sdp);
        results.put(sdp.toString(), result);
      }
    }

    System.out.println("*******************");
    System.out.println(makeSolverTime);
    System.out.println(solverCheckTime);
    System.out.println("*******************");

    printSrcDifferences(differences, similarities);

    return results;
  }

  public TreeMap<String, VerificationResult> searchAllPairs(
      DifferenceQuestion question,
      Encoder encoder,
      BoolExpr noneConstraint,
      BoolExpr ignored,
      Map<GraphEdge, GraphEdge> matchingInterfaces) {
    long before = System.currentTimeMillis();
    TreeMap<String, VerificationResult> results = new TreeMap<>();
    Context ctx = encoder.getCtx();

    Prefix dstStart = Prefix.parse(question.getDstPrefix());
    Prefix srcPrefix = Prefix.parse(question.getSrcPrefix());
    SubRange srcPortRange = question.getSrcPortRange();
    SubRange dstPortRange = question.getDstPortRange();
    int maxLength = question.getMaxLength();

    BitVecExpr dstIp = encoder.getMainSlice().getSymbolicPacket().getDstIp();
    BitVecExpr srcIp = encoder.getMainSlice().getSymbolicPacket().getSrcIp();

    ArrayDeque<SrcDstPair> dstPrefixQueue = new ArrayDeque<>();
    dstPrefixQueue.push(new SrcDstPair(srcPrefix, dstStart, srcPortRange, dstPortRange));
    Set<SrcDstPair> similarities = new HashSet<>();
    Set<SrcDstPair> differences = new HashSet<>();
    int prevDstDepth = 0;

    Solver dstNoneEquivalent = ctx.mkSolver();
    Expr[] notDstVars =
        encoder
            .getAllVariables()
            .values()
            .stream()
            .filter(expr -> !expr.equals(dstIp))
            .toArray(Expr[]::new);

    // Loop to test longer dst prefixes
    while (!dstPrefixQueue.isEmpty()) {
      SrcDstPair sdp = dstPrefixQueue.pop();
      Prefix currDstPrefix = sdp.dst;
      int dstShift = 32 - currDstPrefix.getPrefixLength();
      if (currDstPrefix.getPrefixLength() > prevDstDepth) {
        prevDstDepth = currDstPrefix.getPrefixLength();
      }
      encoder.getSolver().push();

      BoolExpr matchPacketConstraints =
          matchPacket(encoder.getMainSlice().getSymbolicPacket(), sdp, ctx);
      encoder.add(matchPacketConstraints);

      org.batfish.symbolic.smt.VerificationResult result = encoder.verify().getFirst();

      encoder.getSolver().pop();

      // Check that no ip address equivalent for destinations
      if (!result.isVerified() && currDstPrefix.getPrefixLength() < maxLength) {
        dstNoneEquivalent.push();

        BoolExpr fullConstraint = ctx.mkImplies(matchPacketConstraints, noneConstraint);
        BoolExpr forall = ctx.mkForall(notDstVars, fullConstraint, 1, null, null, null, null);
        dstNoneEquivalent.add(forall);
        dstNoneEquivalent.add(ignored);

        BitVecExpr dstPfxIpExpr = ctx.mkBV(currDstPrefix.getStartIp().asLong(), 32);
        BitVecExpr dstShiftExpr = ctx.mkBV(dstShift, 32);
        BoolExpr matchDstPrefix =
            ctx.mkEq(ctx.mkBVLSHR(dstIp, dstShiftExpr), ctx.mkBVLSHR(dstPfxIpExpr, dstShiftExpr));
        dstNoneEquivalent.add(matchDstPrefix);

        Status neStatus = dstNoneEquivalent.check();
        if (neStatus.equals(Status.UNSATISFIABLE)) {
          differences.add(sdp);
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
      }
    }

    for (SrcDstPair sdp : differences) {
      System.out.println(sdp);
      question.setDstPrefix(sdp.getDst().toString());
      question.setSrcPrefix(sdp.getSrc().toString());
      question.setDstPortRange(sdp.getDstPorts());
      question.setSrcPortRange(sdp.getSrcPorts());
      results.putAll(searchAllSrc(question, encoder, noneConstraint, ignored, matchingInterfaces));
    }

    Solver checkPair = ctx.mkSolver();
    Expr[] notSrcDstVars =
        encoder
            .getAllVariables()
            .values()
            .stream()
            .filter(expr -> !expr.equals(srcIp) && !expr.equals(dstIp))
            .toArray(Expr[]::new);
    for(String res : results.keySet()) {
      checkPair.push();

      String[] ips = res.split(" ");
      SrcDstPair sdp = new SrcDstPair(Prefix.parse(ips[0]), Prefix.parse(ips[1]), new SubRange(0, 65535), new SubRange(0, 65535));
      Prefix currDstPrefix = sdp.dst;
      int dstShift = 32 - currDstPrefix.getPrefixLength();
      Prefix currSrcPrefix = sdp.src;
      int srcShift = 32 - currSrcPrefix.getPrefixLength();

      BoolExpr matchPacketConstraints =
          matchPacket(encoder.getMainSlice().getSymbolicPacket(), sdp, ctx);

      BoolExpr fullConstraint = ctx.mkImplies(matchPacketConstraints, noneConstraint);

      BoolExpr forall = ctx.mkForall(notSrcDstVars, fullConstraint, 1, null, null, null, null);
      checkPair.add(forall);
      checkPair.add(ignored);

      BitVecExpr dstPfxIpExpr = ctx.mkBV(sdp.dst.getStartIp().asLong(), 32);
      BitVecExpr dstShiftExpr = ctx.mkBV(dstShift, 32);
      BoolExpr matchDstPrefix =
          ctx.mkEq(ctx.mkBVLSHR(dstIp, dstShiftExpr), ctx.mkBVLSHR(dstPfxIpExpr, dstShiftExpr));
      checkPair.add(matchDstPrefix);

      BitVecExpr srcPfxIpExpr = ctx.mkBV(sdp.src.getStartIp().asLong(), 32);
      BitVecExpr srcShiftExpr = ctx.mkBV(srcShift, 32);
      BoolExpr matchSrcPrefix =
          ctx.mkEq(ctx.mkBVLSHR(srcIp, srcShiftExpr), ctx.mkBVLSHR(srcPfxIpExpr, srcShiftExpr));
      checkPair.add(matchSrcPrefix);

      Status status = checkPair.check();
      if (status.equals(Status.SATISFIABLE)) {
        System.out.println(sdp + " is equivalent for some src/dst pairs");
      }

      checkPair.pop();
    }

    System.out.println("TIME: " + (System.currentTimeMillis() - before));

    return results;
  }

  public TreeMap<String, VerificationResult> searchAllDstPorts(
      DifferenceQuestion question,
      Encoder constraints,
      BoolExpr noneConstraint,
      BoolExpr ignore,
      Map<GraphEdge, GraphEdge> matchingInterfaces) {
    TreeMap<String, VerificationResult> results = new TreeMap<>();
    Context ctx = constraints.getCtx();
    Prefix dstPrefix = Prefix.parse(question.getDstPrefix());
    Prefix srcPrefix = Prefix.parse(question.getSrcPrefix());
    SubRange srcPortRange = question.getSrcPortRange();
    SubRange dstPortRange = question.getDstPortRange();

    ArithExpr dstPort = constraints.getMainSlice().getSymbolicPacket().getDstPort();

    // Build check for none equivalent
    Solver dstPortNoneEquivalent = ctx.mkSolver();
    Expr[] notDstPortVars =
        constraints
            .getAllVariables()
            .values()
            .stream()
            .filter(expr -> !expr.equals(dstPort))
            .toArray(Expr[]::new);

    ArrayDeque<SrcDstPair> SymbolicPacketQueue = new ArrayDeque<>();
    SymbolicPacketQueue.push(new SrcDstPair(srcPrefix, dstPrefix, srcPortRange, dstPortRange));
    Set<SrcDstPair> similarities = new HashSet<>();
    Set<SrcDstPair> differences = new HashSet<>();

    // Loop to test longer src prefixes
    while (!SymbolicPacketQueue.isEmpty()) {
      SrcDstPair sdp = SymbolicPacketQueue.pop();
      SubRange currDstPort = sdp.getDstPorts();
      System.out.println(currDstPort);
      constraints.getSolver().push();

      BoolExpr matchPacketConstraints =
          matchPacket(constraints.getMainSlice().getSymbolicPacket(), sdp, ctx);
      constraints.add(matchPacketConstraints);

      org.batfish.symbolic.smt.VerificationResult result = constraints.verify().getFirst();

      constraints.getSolver().pop();

      // Check that no ip address equivalent for destinations
      if (!result.isVerified() && currDstPort.getEnd() - currDstPort.getStart() > 0) {
        dstPortNoneEquivalent.push();

        BoolExpr fullConstraints = ctx.mkImplies(matchPacketConstraints, noneConstraint);
        BoolExpr forall = ctx.mkForall(notDstPortVars, fullConstraints, 1, null, null, null, null);
        dstPortNoneEquivalent.add(forall);

        ArithExpr maxDstPort = ctx.mkInt(currDstPort.getEnd());
        ArithExpr minDstPort = ctx.mkInt(currDstPort.getStart());
        BoolExpr matchDstPort = ctx.mkAnd(ctx.mkLe(minDstPort, dstPort), ctx.mkLe(dstPort, maxDstPort));
        dstPortNoneEquivalent.add(matchDstPort);
        dstPortNoneEquivalent.add(ignore);

        Status neStatus = dstPortNoneEquivalent.check();

        if (neStatus.equals(Status.UNSATISFIABLE)) {
          differences.add(sdp);
          results.put(sdp.dstPorts.toString(), result);
        } else {
          if (neStatus.equals(Status.UNKNOWN)) {
            System.out.println("Unknown");
          }
          SymbolicPacketQueue.addAll(genDstPortSubranges(sdp));
        }
        dstPortNoneEquivalent.pop();
      } else if (result.isVerified()) {
        similarities.add(sdp);
      } else if (currDstPort.getEnd() - currDstPort.getStart() == 0) {
        differences.add(sdp);
        results.put(sdp.dstPorts.toString(), result);
      }
    }

    Comparator<SubRange> dstPortSort = new Comparator<SubRange>() {
      @Override public int compare(SubRange a, SubRange b) {
        return a.getStart() - b.getStart();
      }
    };
    List<SubRange> diffList = differences.stream()
        .map(SrcDstPair::getDstPorts)
        .sorted(dstPortSort)
        .collect(Collectors.toList());

    System.out.println("--------------------");
    System.out.println("DST PORT DIFFERENCES");
    SubRange prev = null;
    for(SubRange r : diffList) {
      if (prev == null) {
        prev = r;
      } else if (prev.getEnd() + 1 >= r.getStart()) {
        prev = new SubRange(prev.getStart(), r.getEnd());
      } else {
        System.out.println(prev);
        prev = r;
      }
    }
    if (prev != null) {
      System.out.println(prev);
    } else {
      System.out.println("NONE");
    }
    System.out.println("--------------------");

    return results;
  }

  private void printDstDifferences(Set<SrcDstPair> differences, Set<SrcDstPair> similarities) {
    printDstDifferences(differences, similarities, null);
  }

  private void printDstDifferences(
      Set<SrcDstPair> differences,
      Set<SrcDstPair> similarities,
      Map<GraphEdge, GraphEdge> matchingInterfaces) {
    printDifferences(
        differences,
        similarities,
        SrcDstPair::getSrc,
        SrcDstPair::getDst,
        "SRC:",
        "DST:",
        matchingInterfaces);
  }

  private void printSrcDifferences(Set<SrcDstPair> differences, Set<SrcDstPair> similarities) {
    printDifferences(
        differences, similarities, SrcDstPair::getDst, SrcDstPair::getSrc, "DST:", "SRC:", null);
  }

  private void printDifferences(
      Set<SrcDstPair> differences,
      Set<SrcDstPair> similarities,
      Function<SrcDstPair, Prefix> grouping,
      Function<SrcDstPair, Prefix> result,
      String label1,
      String label2,
      Map<GraphEdge, GraphEdge> matchingInterfaces) {
    System.out.println("--------- DIFFERENCES --------");
    Map<Prefix, Set<Prefix>> differencesBySource = separatePackets(differences, grouping, result);
    for (Entry<Prefix, Set<Prefix>> entry : differencesBySource.entrySet()) {
      System.out.print(label1 + " ");
      System.out.println(entry.getKey());
      System.out.println(label2);
      Set<Prefix> pfxs = removeParallelLinks(entry.getValue(), matchingInterfaces);
      minimizePrefixes(pfxs);
    }
    System.out.println("------------------------------");
    System.out.println("--------- SIMILARITIES -------");
    Map<Prefix, Set<Prefix>> verifiedBySource = separatePackets(similarities, grouping, result);
    for (Entry<Prefix, Set<Prefix>> entry : verifiedBySource.entrySet()) {
      System.out.print(label1 + " ");
      System.out.println(entry.getKey());
      System.out.println(label2);
      minimizePrefixes(entry.getValue());
    }
    System.out.println("------------------------------");
  }

  public AnswerElement checkDstDiff(DifferenceQuestion question, Pattern routerRegex) {
    Graph graph = new Graph(_batfish);
    List<String> routers = PatternUtils.findMatchingNodes(graph, routerRegex, Pattern.compile(""));
    if (routers.size() > 2) {
      System.out.println(
          "Only comparing first 2 routers: " + routers.get(0) + " " + routers.get(1));
      routers = Arrays.asList(routers.get(0), routers.get(1));
    } else if (routers.size() < 2) {
      int s = routers.size();
      System.out.println("Only " + s + " routers match regex: " + routerRegex.pattern());
      return null;
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

    HeaderQuestion q = new HeaderQuestion(question);
    q.setFailures(0);

    Encoder encoder1 = new Encoder(_settings, g1, q);
    Encoder encoder2 = new Encoder(encoder1, g2);
    encoder1.computeEncoding();
    addEnvironmentConstraints(encoder1, q.getBaseEnvironmentType());
    encoder2.computeEncoding();
    addEnvironmentConstraints(encoder2, q.getBaseEnvironmentType());
    Context ctx = encoder2.getCtx();

    // Match interfaces
    Map<GraphEdge, GraphEdge> matchingInterfaces =
        getInterfaceMatchGroupedByName(encoder2, edges1, edges2);
    matchingInterfaces.entrySet().forEach(System.out::println);


    // Equate packet fields in both symbolic packets
    SymbolicPacket pkt1 = encoder1.getMainSlice().getSymbolicPacket();
    SymbolicPacket pkt2 = encoder2.getMainSlice().getSymbolicPacket();
    encoder2.add(pkt1.mkEqual(pkt2));

    // Exclude ignored IP addresses
    BoolExpr ignored;
    if (question.getIgnoreInterfaces().equalsIgnoreCase("subnet")) {
      // Ignore subnets of every interface
      ignored =
          ignoreInterfaceSubnets(
              ctx, graph, encoder1.getMainSlice().getSymbolicPacket().getDstIp(), routers);
    } else if (question.getIgnoreInterfaces().equalsIgnoreCase("exact")) {
      // Ignore exact IP address of interfaces
      ignored =
          ignoreExactInterfaces(
              ctx, graph, encoder2.getMainSlice().getSymbolicPacket().getDstIp(), routers);
    } else if (question.getIgnoreInterfaces().equalsIgnoreCase("none")) {
      // Don't ignore
      ignored = ctx.mkTrue();
    } else {
      throw new IllegalArgumentException(
          "Illegal value for ignoreInterfaces: '" + question.getIgnoreInterfaces() + "'\n");
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

    TreeMap<String, VerificationResult> results =
        searchAllDst(question, encoder2, implies, ignored, matchingInterfaces);

    return new SmtManyAnswerElement(results);
  }

  public AnswerElement checkSrcDiff(DifferenceQuestion question, Pattern routerRegex) {
    Graph graph = new Graph(_batfish);
    List<String> routers = PatternUtils.findMatchingNodes(graph, routerRegex, Pattern.compile(""));
    if (routers.size() > 2) {
      System.out.println(
          "Only comparing first 2 routers: " + routers.get(0) + " " + routers.get(1));
      routers = Arrays.asList(routers.get(0), routers.get(1));
    } else if (routers.size() < 2) {
      int s = routers.size();
      System.out.println("Only " + s + " routers match regex: " + routerRegex.pattern());
      return null;
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

    HeaderQuestion q = new HeaderQuestion(question);
    q.setFailures(0);

    Encoder encoder1 = new Encoder(_settings, g1, q);
    Encoder encoder2 = new Encoder(encoder1, g2);
    encoder1.computeEncoding();
    addEnvironmentConstraints(encoder1, q.getBaseEnvironmentType());
    encoder2.computeEncoding();
    addEnvironmentConstraints(encoder2, q.getBaseEnvironmentType());
    Context ctx = encoder2.getCtx();

    // Match interfaces
    Map<GraphEdge, GraphEdge> matchingInterfaces =
        getInterfaceMatchGroupedByName(encoder2, edges1, edges2);
    matchingInterfaces.entrySet().forEach(System.out::println);

    // Equate packet fields in both symbolic packets
    SymbolicPacket pkt1 = encoder1.getMainSlice().getSymbolicPacket();
    SymbolicPacket pkt2 = encoder2.getMainSlice().getSymbolicPacket();
    encoder2.add(pkt1.mkEqual(pkt2));

    // Exclude ignored IP addresses
    BoolExpr ignored;
    if (question.getIgnoreInterfaces().equalsIgnoreCase("subnet")) {
      // Ignore subnets of every interface
      ignored =
          ignoreInterfaceSubnets(
              ctx, graph, encoder1.getMainSlice().getSymbolicPacket().getDstIp(), routers);
    } else if (question.getIgnoreInterfaces().equalsIgnoreCase("exact")) {
      // Ignore exact IP address of interfaces
      ignored =
          ignoreExactInterfaces(
              ctx, graph, encoder2.getMainSlice().getSymbolicPacket().getDstIp(), routers);
    } else if (question.getIgnoreInterfaces().equalsIgnoreCase("none")) {
      // Don't ignore
      ignored = ctx.mkTrue();
    } else {
      throw new IllegalArgumentException(
          "Illegal value for ignoreInterfaces: '" + question.getIgnoreInterfaces() + "'\n");
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
    Solver srcNoneEquivalent = ctx.mkSolver();
    SymbolicPacket packet = encoder2.getMainSlice().getSymbolicPacket();
    BitVecExpr dstIp = packet.getDstIp();
    BitVecExpr srcIp = packet.getSrcIp();

    Expr[] notSrcVars =
        encoder2
            .getAllVariables()
            .values()
            .stream()
            .filter(expr -> !expr.equals(srcIp))
            .toArray(Expr[]::new);

    BoolExpr sameInputs = ctx.mkAnd(sameInputConstraints);
    BoolExpr implies = ctx.mkImplies(sameInputs, sameBehavior);
    BoolExpr forall = ctx.mkForall(notSrcVars, implies, 1, null, null, null, null);
    srcNoneEquivalent.add(forall);
    srcNoneEquivalent.add(ignored);

    TreeMap<String, VerificationResult> results =
        searchAllSrc(question, encoder2, implies, ignored, matchingInterfaces);

    return new SmtManyAnswerElement(results);
  }

  public AnswerElement checkDstPortDiff(DifferenceQuestion question, Pattern routerRegex) {
    Graph graph = new Graph(_batfish);
    List<String> routers = PatternUtils.findMatchingNodes(graph, routerRegex, Pattern.compile(""));
    if (routers.size() > 2) {
      System.out.println(
          "Only comparing first 2 routers: " + routers.get(0) + " " + routers.get(1));
      routers = Arrays.asList(routers.get(0), routers.get(1));
    } else if (routers.size() < 2) {
      int s = routers.size();
      System.out.println("Only " + s + " routers match regex: " + routerRegex.pattern());
      return null;
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

    HeaderQuestion q = new HeaderQuestion(question);
    q.setFailures(0);

    Encoder encoder1 = new Encoder(_settings, g1, q);
    Encoder encoder2 = new Encoder(encoder1, g2);
    encoder1.computeEncoding();
    addEnvironmentConstraints(encoder1, q.getBaseEnvironmentType());
    encoder2.computeEncoding();
    addEnvironmentConstraints(encoder2, q.getBaseEnvironmentType());
    Context ctx = encoder2.getCtx();

    // Match interfaces
    Map<GraphEdge, GraphEdge> matchingInterfaces =
        getInterfaceMatchGroupedByName(encoder2, edges1, edges2);
    matchingInterfaces.entrySet().forEach(System.out::println);

    // Equate packet fields in both symbolic packets
    SymbolicPacket pkt1 = encoder1.getMainSlice().getSymbolicPacket();
    SymbolicPacket pkt2 = encoder2.getMainSlice().getSymbolicPacket();
    encoder2.add(pkt1.mkEqual(pkt2));

    // Exclude ignored IP addresses
    BoolExpr ignored;
    if (question.getIgnoreInterfaces().equalsIgnoreCase("subnet")) {
      // Ignore subnets of every interface
      ignored =
          ignoreInterfaceSubnets(
              ctx, graph, encoder1.getMainSlice().getSymbolicPacket().getDstIp(), routers);
    } else if (question.getIgnoreInterfaces().equalsIgnoreCase("exact")) {
      // Ignore exact IP address of interfaces
      ignored =
          ignoreExactInterfaces(
              ctx, graph, encoder2.getMainSlice().getSymbolicPacket().getDstIp(), routers);
    } else if (question.getIgnoreInterfaces().equalsIgnoreCase("none")) {
      // Don't ignore
      ignored = ctx.mkTrue();
    } else {
      throw new IllegalArgumentException(
          "Illegal value for ignoreInterfaces: '" + question.getIgnoreInterfaces() + "'\n");
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
    SymbolicPacket packet = encoder2.getMainSlice().getSymbolicPacket();
    ArithExpr dstPort = packet.getDstPort();
    Expr[] notDstPortVars =
        encoder2
            .getAllVariables()
            .values()
            .stream()
            .filter(expr -> !expr.equals(dstPort))
            .toArray(Expr[]::new);

    BoolExpr sameInputs = ctx.mkAnd(sameInputConstraints);
    BoolExpr implies = ctx.mkImplies(sameInputs, sameBehavior);

    TreeMap<String, VerificationResult> results =
        searchAllDstPorts(question, encoder2, implies, ignored, matchingInterfaces);

    return new SmtManyAnswerElement(results);
  }

  private class SrcDstPair {
    public Prefix src;
    public Prefix dst;
    public SubRange srcPorts;
    public SubRange dstPorts;

    SrcDstPair(Prefix s, Prefix d, SubRange sp, SubRange dp) {
      src = s;
      dst = d;
      srcPorts = sp;
      dstPorts = dp;
    }

    public Prefix getSrc() {
      return src;
    }

    public Prefix getDst() {
      return dst;
    }

    public SubRange getSrcPorts() {
      return srcPorts;
    }

    public SubRange getDstPorts() {
      return dstPorts;
    }

    @Override
    public String toString() {
      return src.toString() + " " + dst.toString();
    }
  }

  Map<Prefix, Set<Prefix>> separatePackets(
      Set<SrcDstPair> set,
      Function<SrcDstPair, Prefix> grouping,
      Function<SrcDstPair, Prefix> result) {
    Map<Prefix, Set<Prefix>> ret =
        set.stream()
            .collect(
                Collectors.groupingBy(grouping, Collectors.mapping(result, Collectors.toSet())));
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
      ret.add(new SrcDstPair(sdp.src, newDst, sdp.srcPorts, sdp.dstPorts));
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
      ret.add(new SrcDstPair(newSrc, sdp.dst, sdp.srcPorts, sdp.dstPorts));
    }
    return ret;
  }

  private Set<SrcDstPair> genDstPortSubranges(SrcDstPair sdp) {
    Set<SrcDstPair> ret = new HashSet<>();

    int half = (sdp.dstPorts.getStart() + sdp.dstPorts.getEnd()) / 2;
    SubRange first = new SubRange(sdp.dstPorts.getStart(), half);
    SubRange second = new SubRange(half + 1, sdp.dstPorts.getEnd());

    ret.add(new SrcDstPair(sdp.src, sdp.dst, sdp.srcPorts, first));
    ret.add(new SrcDstPair(sdp.src, sdp.dst, sdp.srcPorts, second));
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

    if (matchingInterfaces == null) {
      return r;
    }

    List<Prefix> matchDifferences = new ArrayList<>();
    for (Entry<GraphEdge, GraphEdge> ent : matchingInterfaces.entrySet()) {
      Prefix pfx1 = ent.getKey().getStart().getAddress().getPrefix();
      Prefix pfx2 = ent.getValue().getStart().getAddress().getPrefix();
      matchDifferences.add(pfx1);
      matchDifferences.add(pfx2);
    }

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
      System.out.println("NONE");
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

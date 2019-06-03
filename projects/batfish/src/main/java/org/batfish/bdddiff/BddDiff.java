package org.batfish.bdddiff;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.BDDPairing;
import org.batfish.common.bdd.BDDAcl;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.BDDPacketWithLines;
import org.batfish.common.bdd.PacketPrefixRegion;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.specifier.AllFiltersFilterSpecifier;
import org.batfish.specifier.FilterSpecifier;
import org.batfish.specifier.SpecifierContext;
import org.batfish.specifier.SpecifierFactories;

public class BddDiff {

  //public static long totalTime = 0;
  //public static int totalPairs = 0;
  //public static int totalFilters = 0;
  //public static int totalDiffs = 0;


  private BDD getPacketHeaderFields(BDDPacket packet) {
    BDD headerVars = packet.getFactory().one();
    BDD bddDstIp =
        Arrays.stream(packet.getDstIp().getBitvec()).reduce(packet.getFactory().one(), BDD::and);
    BDD bddSrcIp =
        Arrays.stream(packet.getSrcIp().getBitvec()).reduce(packet.getFactory().one(), BDD::and);
    BDD bddDstPort =
        Arrays.stream(packet.getDstPort().getBitvec()).reduce(packet.getFactory().one(), BDD::and);
    BDD bddSrcPort =
        Arrays.stream(packet.getSrcPort().getBitvec()).reduce(packet.getFactory().one(), BDD::and);
    BDD bddProtocol =
        Arrays.stream(packet.getIpProtocol().getBDDInteger().getBitvec())
            .reduce(packet.getFactory().one(), BDD::and);
    BDD bddIcmpType =
        Arrays.stream(packet.getIcmpType().getBDDInteger().getBitvec()).reduce(packet.getFactory().one(), BDD::and);
    BDD bddIcmpCode =
        Arrays.stream(packet.getIcmpCode().getBDDInteger().getBitvec()).reduce(packet.getFactory().one(), BDD::and);
    BDD bddDscp =
        Arrays.stream(packet.getDscp().getBitvec()).reduce(packet.getFactory().one(), BDD::and);
    BDD bddEcn =
        Arrays.stream(packet.getEcn().getBitvec()).reduce(packet.getFactory().one(), BDD::and);
    BDD bddFragOffset =
        Arrays.stream(packet.getFragmentOffset().getBitvec())
            .reduce(packet.getFactory().one(), BDD::and);
    BDD bddTcpBits =
        packet
            .getTcpAck()
            .and(packet.getTcpEce())
            .and(packet.getTcpFin())
            .and(packet.getTcpCwr())
            .and(packet.getTcpRst())
            .and(packet.getTcpPsh())
            .and(packet.getTcpUrg())
            .and(packet.getTcpSyn());
    headerVars.andWith(bddDstIp);
    headerVars.andWith(bddSrcIp);
    headerVars.andWith(bddDstPort);
    headerVars.andWith(bddSrcPort);
    headerVars.andWith(bddProtocol);
    headerVars.andWith(bddIcmpType);
    headerVars.andWith(bddIcmpCode);
    headerVars.andWith(bddDscp);
    headerVars.andWith(bddEcn);
    headerVars.andWith(bddFragOffset);
    headerVars.andWith(bddTcpBits);
    return headerVars;
  }

  /*
  Check differences between ACLs with same name on two routers
   */
  public SortedSet<LineDifference> findDiffWithLines(
      IBatfish batfish,
      NodesSpecifier nodesSpecifier,
      String aclRegex,
      boolean printMore) {

    SortedSet<LineDifference> differences = new TreeSet<>();
    Set<String> routerNames = nodesSpecifier.getMatchingNodes(batfish);
    SortedMap<String, Configuration> configs = batfish.loadConfigurations();
    Map<String, Map<String, IpAccessList>> aclNameToAcls = new TreeMap<>();
    Map<IpAccessList, String> aclToRouterName = new HashMap<>();
    System.out.println("NodeRegex = " + nodesSpecifier);
    System.out.println("printMore = " + printMore);

    //if (routerNames.size() == 2) {
    //  BddDiff.totalPairs++;
    //}

    for (String rr : routerNames) {
      Configuration cc = configs.get(rr);
      for (Entry<String, IpAccessList> entry : cc.getIpAccessLists().entrySet()) {
        if (!entry.getKey().matches(aclRegex)) {
          continue;
        }
        IpAccessList acl = entry.getValue();
        Map<String, IpAccessList> routerToAcl =
            aclNameToAcls.getOrDefault(entry.getKey(), new HashMap<>());
        routerToAcl.put(rr, entry.getValue());
        aclNameToAcls.put(entry.getKey(), routerToAcl);
        aclToRouterName.put(acl, rr);
      }
    }

    for (Entry<String, Map<String, IpAccessList>> entry : aclNameToAcls.entrySet()) {
      String aclName = entry.getKey();
      Map<String, IpAccessList> accessLists = entry.getValue();
      BDDPacketWithLines packet = new BDDPacketWithLines();
      try {
        if (accessLists.size() == 2) {
          differences.addAll(compareAcls(batfish, packet, accessLists, printMore, false));
        } else {
          /*
          System.out.print(entry.getKey() + " is present in ");
          accessLists.keySet()
              .forEach(x -> System.out.print(x + " " ));
          System.out.println();
          */
        }

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    /*
    long timeElapsed = System.currentTimeMillis() - startTime;
    if (count == 0) {
      totalPairs--;
    }
    totalTime += timeElapsed;
    System.out.println(nodesSpecifier);
    System.out.println("Time: " + timeElapsed);
    System.out.println("Count: " + count);
    System.out.println("Total Time: " + totalTime);
    System.out.println("Total Pairs: " + totalPairs);
    System.out.println("Total Filters: " + totalFilters);
    System.out.println("Total Diffs: " + totalDiffs);
    */
    return differences;
  }

  public SortedSet<LineDifference> compareAcls(
      IBatfish batfish,
      BDDPacketWithLines packet,
      Map<String, IpAccessList> accessLists,
      boolean printMore,
      boolean differential) {
    assert(accessLists.size() == 2);
    // BddDiff.totalFilters++;
    SortedSet<LineDifference> differences = new TreeSet<>();
    List<String> routers = new ArrayList<>(accessLists.keySet());
    BDDAcl acl1 = BDDAcl.createWithLines(packet, routers.get(0), accessLists.get(routers.get(0)));
    BDDAcl acl2 = BDDAcl.createWithLines(packet, routers.get(1), accessLists.get(routers.get(1)));
    BDD first = acl1.getBdd();
    BDD second = acl2.getBdd();

    BDD acceptVar = packet.getAccept();

    BDD acceptFirst = first.restrict(acceptVar);
    BDD acceptSecond = second.restrict(acceptVar);
    BDD rejectFirst = first.restrict(acceptVar.not());
    BDD rejectSecond = second.restrict(acceptVar.not());
    BDD notEquivalent = acceptFirst.and(rejectSecond).or(acceptSecond.and(rejectFirst));

    if (notEquivalent.isZero()) {
      // System.out.println(entry.getKey() + " is consistent");
    } else {
      // BddDiff.totalDiffs++;
      System.out.println("**************************");
      BDD linesNotEquivalent = notEquivalent.exist(getPacketHeaderFields(packet));

      List<AclDiffReport> reportList = new ArrayList<>();
      while (!linesNotEquivalent.isZero()) {
        BDD lineSat = linesNotEquivalent.satOne();
        BDD counterexample = notEquivalent.and(lineSat).satOne();

        /*
        long dstIp = packet.getDstIp().getValueSatisfying(counterexample).get();
        long srcIp = packet.getSrcIp().getValueSatisfying(counterexample).get();
        long dstPort = packet.getDstPort().getValueSatisfying(counterexample).get();
        long srcPort = packet.getSrcPort().getValueSatisfying(counterexample).get();
        System.out.println("EXAMPLE:");
        System.out.println("\tDST-IP:    " + Ip.create(dstIp));
        System.out.println("\tSRC-IP:    " + Ip.create(srcIp));
        System.out.println("\tDST-PORT:  " + dstPort);
        System.out.println("\tSRC-PORT:  " + srcPort);
        */
        int i = 0;
        IpAccessListLine[] lineDiff = new IpAccessListLine[2];
        List<IpAccessList> aclList = new ArrayList<>(accessLists.values());
        for (IpAccessList acl : accessLists.values()) {
          boolean found = false;
          for (IpAccessListLine line : acl.getLines()) {
            if (!counterexample.and(packet.getAclLine(routers.get(i), acl, line)).isZero()) {
              System.out.print(routers.get(i) + " ");
              System.out.println(acl.getName() + ":");
              System.out.println("  " + line.getName());
              lineDiff[i] = line;
              found = true;
            }
          }
          if (!found) {
            System.out.print(routers.get(i) + " ");
            System.out.println(acl.getName());
            System.out.println("  Implicit deny ");
          }
          i++;
        }
        AclLineDiffToPrefix diffToPrefix =
            new AclLineDiffToPrefix(aclList.get(0), aclList.get(1), lineDiff[0], lineDiff[1]);

        diffToPrefix.printDifferenceInPrefix();
        AclDiffReport report = diffToPrefix.getAclDiffReport(routers.get(0), routers.get(1));
        report.print(batfish, printMore, differential);
        differences.add(report.toLineDifference(batfish, printMore, differential));
        /*boolean merged = false;
        for (AclDiffReport r : reportList) {
          System.out.println(AclDiffReport.combinedLineCount(r, report) - Integer.max(report.getLineCount(), r.getLineCount()));
          if (AclDiffReport.combinedLineCount(r, report) - Integer.max(report.getLineCount(), r.getLineCount()) <= 1
              && AclDiffReport.combinedSameAction(r, report)) {
            merged = true;
            r.combineWith(report);
            break;
          }
        }
        if (!merged) {
          reportList.add(report);
        }
        */
        BDD cond = counterexample.exist(getPacketHeaderFields(packet)).not();
        linesNotEquivalent = linesNotEquivalent.and(cond);
      }
      // for (AclDiffReport r : reportList) {
      //  r.print((Batfish) batfish);:q
      // }
      System.out.println("**************************");
    }
    return differences;
  }

  public SortedSet<LineDifference> getTimeDiff(IBatfish batfish, NodesSpecifier nodesSpecifier, String aclRegex, boolean printMore) {
    batfish.pushBaseSnapshot();
    SpecifierContext currentContext = batfish.specifierContext();
    batfish.popSnapshot();

    batfish.pushDeltaSnapshot();
    SpecifierContext referenceContext = batfish.specifierContext();
    batfish.popSnapshot();

    Multimap<String, List<IpAccessList>> aclPairs = ArrayListMultimap.create();
    Set<String> routers = new TreeSet<>(nodesSpecifier.getMatchingNodes(currentContext));
    routers.retainAll(nodesSpecifier.getMatchingNodes(referenceContext));

    Set<String> onlyFirst = nodesSpecifier.getMatchingNodes(currentContext).stream()
        .filter(s -> !routers.contains(s))
        .collect(Collectors.toCollection(TreeSet::new));
    Set<String> onlySecond = nodesSpecifier.getMatchingNodes(referenceContext).stream()
        .filter(s -> !routers.contains(s))
        .collect(Collectors.toCollection(TreeSet::new));
    for (String router : routers) {
      Set<String> currentInterfaces = currentContext.getConfigs().get(router).getAllInterfaces().keySet();
      Set<String> referenceInterfaces = referenceContext.getConfigs().get(router).getAllInterfaces().keySet();

      Set<String> intersection = new HashSet<>(currentInterfaces);
      intersection.retainAll(referenceInterfaces);
      for (String intfName : intersection) {
        Interface intf1 = currentContext.getConfigs().get(router).getAllInterfaces().get(intfName);
        Interface intf2 = referenceContext.getConfigs().get(router).getAllInterfaces().get(intfName);

        IpAccessList inAcl1 = intf1.getIncomingFilter();
        IpAccessList inAcl2 = intf2.getIncomingFilter();

        IpAccessList outAcl1 = intf1.getOutgoingFilter();
        IpAccessList outAcl2 = intf2.getOutgoingFilter();

        if (inAcl1 != null
            && inAcl2 != null
            && inAcl1.getName().matches(aclRegex)
            && inAcl2.getName().matches(aclRegex)) {
          ArrayList<IpAccessList> pair = new ArrayList<>();
          pair.add(inAcl1);
          pair.add(inAcl2);
          aclPairs.put(router, pair);
        } else if (inAcl1 != null && inAcl1.getName().matches(aclRegex)) {
          onlyFirst.add(router+":"+inAcl1.getName());
        } else if (inAcl2 != null && inAcl2.getName().matches(aclRegex)) {
          onlySecond.add(router+":"+inAcl2.getName());
        }

        if (outAcl1 != null
            && outAcl2 != null
            && outAcl1.getName().matches(aclRegex)
            && outAcl2.getName().matches(aclRegex)) {
          ArrayList<IpAccessList> pair = new ArrayList<>();
          pair.add(outAcl1);
          pair.add(outAcl2);
          aclPairs.put(router, pair);
        } else if (outAcl1 != null && outAcl1.getName().matches(aclRegex)) {
          onlyFirst.add(router+":"+outAcl1.getName());
        } else if (outAcl2 != null && outAcl2.getName().matches(aclRegex)) {
          onlySecond.add(router+":"+outAcl2.getName());
        }
      }
    }

    System.out.println("Only in current");
    onlyFirst.forEach(System.out::println);
    System.out.println("Only in reference");
    onlySecond.forEach(System.out::println);

    System.out.println("PAIRS:");

    SortedSet<LineDifference> differences = new TreeSet<>();
    BDDPacketWithLines packet = new BDDPacketWithLines();
    for (Entry<String, List<IpAccessList>> entry : aclPairs.entries())
    {
      String router = entry.getKey();

      IpAccessList acl1 = entry.getValue().get(0);
      IpAccessList acl2 = entry.getValue().get(1);
      System.out.println(router);
      System.out.println("current:" + acl1.getName() + "--" + "reference:" + acl2.getName());

      Map<String, IpAccessList> accessLists = new HashMap<>();
      accessLists.put(router + "-current", acl1);
      accessLists.put(router + "-reference", acl2);
      differences.addAll(compareAcls(batfish, packet, accessLists, printMore, true));
    }

    Map<String, SortedSet<String>> diffToFilters = new TreeMap<>();
    Map<String, SortedSet<String>> filterToDiffs = new TreeMap<>();
    for(LineDifference diff : differences) {
      for(String region : diff.getDifference()) {
        if (!diffToFilters.containsKey(region)) {
          diffToFilters.put(region, new TreeSet<>());
        }
        diffToFilters.get(region).add(diff.getRouter1() + ":" + diff.getFilter1() + "--" + diff.getRouter2() + ":" + diff.getFilter2());
      }
    }

    System.out.println("TOTAL DIFFERENCES BY REGION");
    for (String region : diffToFilters.keySet()) {
      SortedSet<String> filters = diffToFilters.get(region);
      System.out.println(region + ": [");
      filters.forEach(f -> System.out.println("\t" + f));
      System.out.println("]");
    }

    return differences;
  }

  /** Get filters specified by the given filter specifier. */
  public static Multimap<String, String> getSpecifiedFilters(
      SpecifierContext specifierContext,
      NodesSpecifier nodesSpecifier,
      String aclRegex) {
    Set<String> nodes = nodesSpecifier.getMatchingNodes(specifierContext);
    ImmutableMultimap.Builder<String, String> filters = ImmutableMultimap.builder();
    Map<String, Configuration> configs = specifierContext.getConfigs();
    FilterSpecifier filterSpecifier = SpecifierFactories.getFilterSpecifierOrDefault(aclRegex,
        AllFiltersFilterSpecifier.INSTANCE);
    nodes.stream()
        .map(configs::get)
        .forEach(
            config ->
                filterSpecifier.resolve(config.getHostname(), specifierContext).stream()
                    .forEach(filter -> filters.put(config.getHostname(), filter.getName())));
    return filters.build();
  }


  public void doTest(IBatfish batfish, NodesSpecifier nodeRegex) {
    BDDPacket packet = new BDDPacket();

    Set<String> routers = nodeRegex.getMatchingNodes(batfish);
    SortedMap<String, Configuration> configs = batfish.loadConfigurations();
    Map<String, List<IpAccessList>> aclNameToAcls = new TreeMap<>();

    for (String rr : routers) {
      Configuration cc = configs.get(rr);
      System.out.println("***********************");
      System.out.println(cc.getHostname());
      for (Entry<String, IpAccessList> entry : cc.getIpAccessLists().entrySet()) {
        IpAccessList acl = entry.getValue();
        List<IpAccessList> lists = aclNameToAcls.getOrDefault(entry.getKey(), new ArrayList<>());
        lists.add(acl);
        aclNameToAcls.put(entry.getKey(), lists);

        BDDAcl bddAcl = BDDAcl.create(packet, acl);
        /*
        if (!bddAcl.getBdd().isZero()) {
          long _dstIp = packet.getDstIp().getValueSatisfying(bddAcl.getBdd()).get();
          long _srcIp = packet.getSrcIp().getValueSatisfying(bddAcl.getBdd()).get();
          System.out.println(acl);
          System.out.println("DST: " + Ip.create(_dstIp));
          System.out.println("SRC: " + Ip.create(_srcIp));
          System.out.println();
        } else {
          System.out.println(acl);
          System.out.println("denies all packets");
          System.out.println();
        }
        */
      }
    }

    for (Entry<String, List<IpAccessList>> entry : aclNameToAcls.entrySet()) {
      List<IpAccessList> accessLists = entry.getValue();
      if (accessLists.size() == 2) {
        BDDAcl acl1 = BDDAcl.create(packet, accessLists.get(0));
        BDDAcl acl2 = BDDAcl.create(packet, accessLists.get(1));
        BDD first = acl1.getBdd();
        BDD second = acl2.getBdd();
        BDD isEquivalent = first.and(second).or(first.not().and(second.not()));
        if (isEquivalent.isOne()) {
          // System.out.println(entry.getKey() + " is consistent");
        } else {
          Queue<Prefix> prefixQueue = new ArrayDeque<>();
          prefixQueue.add(Prefix.ZERO);
          while (!prefixQueue.isEmpty()) {
            Prefix pfx = prefixQueue.remove();

            isEquivalent = first.and(second).or(first.not().and(second.not()));
            BDD pfxIsEquivalent = matchPrefix(packet, pfx).imp(isEquivalent);

            BDD bddDstIp =
                Arrays.stream(packet.getDstIp().getBitvec())
                    .reduce(packet.getFactory().one(), BDD::and);
            BDD pfxNotEquivalent = matchPrefix(packet, pfx).imp(isEquivalent.not());
            BDD forallNotEquivalent = pfxNotEquivalent.forAll(bddDstIp);
            if (pfxIsEquivalent.isOne()) {
              // System.out.println(entry.getKey() + " is consistent on " + pfx);
            } else if (!forallNotEquivalent.isZero()) {
              isEquivalent.printDot();
              forallNotEquivalent.printDot();
              pfxNotEquivalent.andWith(matchPrefix(packet, pfx));
              long dstIp = packet.getDstIp().getValueSatisfying(pfxNotEquivalent).get();
              long srcIp = packet.getSrcIp().getValueSatisfying(pfxNotEquivalent).get();
              long dstPort = packet.getDstPort().getValueSatisfying(pfxNotEquivalent).get();
              long srcPort = packet.getSrcPort().getValueSatisfying(pfxNotEquivalent).get();
              System.out.println(entry.getKey() + " disagrees on: " + pfx);
              System.out.println("EXAMPLE:");
              System.out.println("\tDST-IP:    " + Ip.create(dstIp));
              System.out.println("\tSRC-IP:    " + Ip.create(srcIp));
              System.out.println("\tDST-PORT:  " + dstPort);
              System.out.println("\tSRC-PORT:  " + srcPort);
              System.out.println();
            } else {

              prefixQueue.addAll(genLongerPrefix(pfx));
            }
          }
        }
      }
    }
  }

  private Set<Prefix> genLongerPrefix(Prefix pfx) {
    Set<Prefix> ret = new HashSet<>();
    long dstBits = pfx.getStartIp().asLong();
    int dstLength = pfx.getPrefixLength();
    if (dstLength < 32) {
      for (int i = 0; i < 2; i++) {
        long nextDstBit = 1L << (31 - dstLength);
        Prefix newDst = Prefix.create(Ip.create(dstBits + i * nextDstBit), dstLength + 1);
        ret.add(newDst);
      }
    }
    return ret;
  }

  private BDD matchPrefix(BDDPacket packet, Prefix pfx) {
    long bits = pfx.getStartIp().asLong();
    int length = pfx.getPrefixLength();
    BDD result = packet.getFactory().one();
    for (int i = 0; i < length; i++) {
      BDD dstIpBDD = packet.getDstIp().getBitvec()[i].id();
      BDD bitBDD = Ip.getBitAtPosition(bits, i) ? dstIpBDD : dstIpBDD.not();
      result.andWith(bitBDD);
    }
    return result;
  }

  private BDD restrictBDD(BDD bdd, BDDPacket pkt, PacketPrefixRegion region) {
    int dstLen = region.getDstIp().getPrefixLength();
    long dstBits = region.getDstIp().getStartIp().asLong();
    int[] dstVars = new int[dstLen];
    BDD[] dstVals = new BDD[dstLen];

    int srcLen = region.getSrcIp().getPrefixLength();
    long srcBits = region.getSrcIp().getStartIp().asLong();
    int[] srcVars = new int[srcLen];
    BDD[] srcVals = new BDD[srcLen];

    BDDFactory factory = pkt.getFactory();
    BDDPairing pairing = factory.makePair();

    for (int i = 0; i < dstLen; i++) {
      int var = pkt.getDstIp().getBitvec()[i].var(); // dstIpIndex + i;
      BDD subst = Ip.getBitAtPosition(dstBits, i) ? factory.one() : factory.zero();
      dstVars[i] = var;
      dstVals[i] = subst;
    }
    pairing.set(dstVars, dstVals);

    for (int i = 0; i < srcLen; i++) {
      int var = pkt.getSrcIp().getBitvec()[i].var(); // srcIpIndex + i;
      BDD subst = Ip.getBitAtPosition(srcBits, i) ? factory.one() : factory.zero();
      srcVars[i] = var;
      srcVals[i] = subst;
    }
    pairing.set(srcVars, srcVals);

    bdd = bdd.veccompose(pairing);

    BDD dstPortBDD =
        pkt.getDstPort()
            .leq(region.getDstPort().getEnd())
            .and(pkt.getDstPort().geq(region.getDstPort().getStart()));

    BDD srcPortBDD =
        pkt.getSrcPort()
            .leq(region.getSrcPort().getEnd())
            .and(pkt.getSrcPort().geq(region.getSrcPort().getStart()));

    BDD protoBDD = pkt.getIpProtocol().value(region.getProtocol());

    return bdd.and(dstPortBDD).and(srcPortBDD).and(protoBDD);
  }
}

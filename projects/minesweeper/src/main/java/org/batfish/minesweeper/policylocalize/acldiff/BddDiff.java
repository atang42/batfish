package org.batfish.minesweeper.policylocalize.acldiff;

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
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.BDDPairing;
import org.batfish.common.bdd.BDDAcl;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.BDDPacketWithLines;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.minesweeper.policylocalize.acldiff.representation.ConjunctHeaderSpace;
import org.batfish.specifier.AllFiltersFilterSpecifier;
import org.batfish.specifier.FilterSpecifier;
import org.batfish.specifier.SpecifierContext;
import org.batfish.specifier.SpecifierFactories;

public class BddDiff {

  private IBatfish _batfish;
  private NodesSpecifier _specifier;
  private String _aclRegex;
  private boolean _printMore;
  private BDDPacketWithLines _packet;

  public BddDiff(IBatfish batfish, NodesSpecifier specifier, String aclRegex, boolean printMore)
  {
    this._batfish = batfish;
    this._specifier = specifier;
    this._aclRegex = aclRegex;
    this._printMore = printMore;
    _packet = new BDDPacketWithLines();
  }

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
        Arrays.stream(packet.getIcmpType().getBDDInteger().getBitvec())
            .reduce(packet.getFactory().one(), BDD::and);
    BDD bddIcmpCode =
        Arrays.stream(packet.getIcmpCode().getBDDInteger().getBitvec())
            .reduce(packet.getFactory().one(), BDD::and);
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
  public SortedSet<LineDifference> findDiffWithLines() {

    SortedSet<LineDifference> differences = new TreeSet<>();
    Set<String> routerNames = _specifier.getMatchingNodes(_batfish);
    SortedMap<String, Configuration> configs = _batfish.loadConfigurations();
    Map<String, Map<String, IpAccessList>> aclNameToAcls = new TreeMap<>();
    Map<IpAccessList, String> aclToRouterName = new HashMap<>();
    System.out.println("Matched Routers:");
    routerNames.forEach(x -> System.out.println("    " + x));
    System.out.println();

    // if (routerNames.size() == 2) {
    //  BddDiff.totalPairs++;
    // }

    for (String rr : routerNames) {
      Configuration cc = configs.get(rr);
      for (Entry<String, IpAccessList> entry : cc.getIpAccessLists().entrySet()) {
        if (!entry.getKey().matches(_aclRegex)) {
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
      try {
        if (accessLists.size() == 2) {
          System.out.print("Comparing " + aclName + " for: ");
          accessLists.keySet().forEach(x -> System.out.print(x + " "));
          System.out.println();
          differences.addAll(compareAcls(accessLists, false));
        } else {
          System.out.print(
              entry.getKey() + " is present in " + accessLists.size() + " router(s): ");
          accessLists.keySet().forEach(x -> System.out.print(x + " "));
          System.out.println();
        }

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    return differences;
  }

  public SortedSet<LineDifference> compareAcls(Map<String, IpAccessList> accessLists, boolean differential) {
    assert (accessLists.size() == 2);
    SortedSet<LineDifference> differences = new TreeSet<>();
    List<String> routers = new ArrayList<>(accessLists.keySet());
    BDDAcl acl1 = BDDAcl.createWithLines(_packet, routers.get(0), accessLists.get(routers.get(0)));
    BDDAcl acl2 = BDDAcl.createWithLines(_packet, routers.get(1), accessLists.get(routers.get(1)));
    BDD first = acl1.getBdd();
    BDD second = acl2.getBdd();

    BDD acceptVar = _packet.getAccept();

    BDD acceptFirst = first.restrict(acceptVar);
    BDD acceptSecond = second.restrict(acceptVar);
    BDD rejectFirst = first.restrict(acceptVar.not());
    BDD rejectSecond = second.restrict(acceptVar.not());
    BDD notEquivalent = acceptFirst.and(rejectSecond).or(acceptSecond.and(rejectFirst));

    if (notEquivalent.isZero()) {
      // System.out.println("No Difference");
    } else {
      BDD linesNotEquivalent = notEquivalent.exist(getPacketHeaderFields(_packet));
      List<IpAccessList> aclList = new ArrayList<>(accessLists.values());
      AclDiffToPrefix aclDiffToPrefix = new AclDiffToPrefix(
          routers.get(0), routers.get(1),
          aclList.get(0), aclList.get(1));
      while (!linesNotEquivalent.isZero()) {
        BDD lineSat = linesNotEquivalent.satOne();
        BDD counterexample = notEquivalent.and(lineSat).satOne();

        int i = 0;
        IpAccessListLine[] lineDiff = new IpAccessListLine[2];
        for (IpAccessList acl : aclList) {
          for (IpAccessListLine line : acl.getLines()) {
            if (counterexample.andSat(_packet.getAclLine(routers.get(i), acl, line))) {
              lineDiff[i] = line;
            }
          }
          i++;
        }

        // diffToPrefix.printDifferenceInPrefix();
        AclDiffReport report = aclDiffToPrefix.getReport(lineDiff[0], lineDiff[1]);
        // report.print(_batfish, _printMore, differential);
        differences.add(report.toLineDifference(_batfish, _printMore, differential));
        BDD cond = counterexample.exist(getPacketHeaderFields(_packet)).not();
        linesNotEquivalent = linesNotEquivalent.and(cond);
      }
    }
    return differences;
  }

  public SortedSet<LineDifference> getTimeDiff() {
    _batfish.pushBaseSnapshot();
    SpecifierContext currentContext = _batfish.specifierContext();
    _batfish.popSnapshot();

    _batfish.pushDeltaSnapshot();
    SpecifierContext referenceContext = _batfish.specifierContext();
    _batfish.popSnapshot();

    SortedSet<LineDifference> differences = new TreeSet<>();
    Set<String> routers = new TreeSet<>(_specifier.getMatchingNodes(currentContext));
    routers.retainAll(_specifier.getMatchingNodes(referenceContext));

    for (String router : routers) {
      Set<String> currentInterfaces =
          currentContext.getConfigs().get(router).getAllInterfaces().keySet();
      Set<String> referenceInterfaces =
          referenceContext.getConfigs().get(router).getAllInterfaces().keySet();

      Set<String> intersection = new HashSet<>(currentInterfaces);
      intersection.retainAll(referenceInterfaces);
      for (String intfName : intersection) {
        Interface intf1 = currentContext.getConfigs().get(router).getAllInterfaces().get(intfName);
        Interface intf2 =
            referenceContext.getConfigs().get(router).getAllInterfaces().get(intfName);

        IpAccessList inAcl1 = intf1.getIncomingFilter();
        IpAccessList inAcl2 = intf2.getIncomingFilter();

        IpAccessList outAcl1 = intf1.getOutgoingFilter();
        IpAccessList outAcl2 = intf2.getOutgoingFilter();

        if (inAcl1 != null
            && inAcl2 != null
            && inAcl1.getName().matches(_aclRegex)
            && inAcl2.getName().matches(_aclRegex)) {
          String fullIntfName = intfName + "-Incoming";
          differences.addAll(compareAclPair(router, fullIntfName, inAcl1, inAcl2));
        } else if (inAcl1 != null && inAcl1.getName().matches(_aclRegex)) {
          // TODO: Handle case where only first router has ACL
        } else if (inAcl2 != null && inAcl2.getName().matches(_aclRegex)) {
          // TODO: Handle case where only second router has ACL
        }

        if (outAcl1 != null
            && outAcl2 != null
            && outAcl1.getName().matches(_aclRegex)
            && outAcl2.getName().matches(_aclRegex)) {
          String fullIntfName = intfName + "-Outgoing";
          differences.addAll(compareAclPair(router, fullIntfName, outAcl1, outAcl2));
        } else if (outAcl1 != null && outAcl1.getName().matches(_aclRegex)) {
          // TODO: Handle case where only first router has ACL
        } else if (outAcl2 != null && outAcl2.getName().matches(_aclRegex)) {
          // TODO: Handle case where only second router has ACL
        }
      }
    }

    return differences;
  }

  private SortedSet<LineDifference> compareAclPair(String router, String intfName, IpAccessList acl1, IpAccessList acl2) {
    Map<String, IpAccessList> accessLists = new HashMap<>();
    accessLists.put(router + "-current", acl1);
    accessLists.put(router + "-reference", acl2);
    SortedSet<LineDifference> lineDifferences = compareAcls(accessLists, true);
    lineDifferences.forEach(ld -> ld.setInterface(intfName));
    return lineDifferences;
  }

  /** Get filters specified by the given filter _specifier. */
  public static Multimap<String, String> getSpecifiedFilters(
      SpecifierContext specifierContext, NodesSpecifier nodesSpecifier, String aclRegex) {
    Set<String> nodes = nodesSpecifier.getMatchingNodes(specifierContext);
    ImmutableMultimap.Builder<String, String> filters = ImmutableMultimap.builder();
    Map<String, Configuration> configs = specifierContext.getConfigs();
    FilterSpecifier filterSpecifier =
        SpecifierFactories.getFilterSpecifierOrDefault(
            aclRegex, AllFiltersFilterSpecifier.INSTANCE);
    nodes.stream()
        .map(configs::get)
        .forEach(
            config ->
                filterSpecifier.resolve(config.getHostname(), specifierContext)
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
      for (Entry<String, IpAccessList> entry : cc.getIpAccessLists().entrySet()) {
        IpAccessList acl = entry.getValue();
        List<IpAccessList> lists = aclNameToAcls.getOrDefault(entry.getKey(), new ArrayList<>());
        lists.add(acl);
        aclNameToAcls.put(entry.getKey(), lists);
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
              pfxNotEquivalent.andWith(matchPrefix(packet, pfx));
              long dstIp = packet.getDstIp().getValueSatisfying(pfxNotEquivalent).get();
              long srcIp = packet.getSrcIp().getValueSatisfying(pfxNotEquivalent).get();
              long dstPort = packet.getDstPort().getValueSatisfying(pfxNotEquivalent).get();
              long srcPort = packet.getSrcPort().getValueSatisfying(pfxNotEquivalent).get();
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

  private BDD restrictBDD(BDD bdd, BDDPacket pkt, ConjunctHeaderSpace region) {
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

package org.batfish.symbolic.bdd;

import java.security.acl.Acl;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.BDDPairing;
import org.batfish.common.bdd.BDDInteger;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.BDDPacketWithLines;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.DefinedStructureInfo;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.answers.ConvertConfigurationAnswerElement;
import org.batfish.datamodel.answers.ParseVendorConfigurationAnswerElement;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.main.Batfish;
import org.batfish.symbolic.CommunityVar;
import org.batfish.symbolic.Graph;

public class BDDTest {

  private BDDRoute computeBDD(
      Graph g, Configuration conf, RoutingPolicy pol, boolean ignoreNetworks) {
    Set<Prefix> networks = null;
    if (ignoreNetworks) {
      networks = Graph.getOriginatedNetworks(conf);
    }
    TransferBDD t = new TransferBDD(g, conf, pol.getStatements(), new PolicyQuotient(g));
    return t.compute(networks);
  }

  private BDD getPacketHeaderFields(BDDPacket packet) {
    BDD headerVars = packet.getFactory().one();
    BDD bddDstIp = Arrays.stream(packet.getDstIp().getBitvec())
        .reduce(packet.getFactory().one(), BDD::and);
    BDD bddSrcIp = Arrays.stream(packet.getSrcIp().getBitvec())
        .reduce(packet.getFactory().one(), BDD::and);
    BDD bddDstPort = Arrays.stream(packet.getDstPort().getBitvec())
        .reduce(packet.getFactory().one(), BDD::and);
    BDD bddSrcPort = Arrays.stream(packet.getSrcPort().getBitvec())
        .reduce(packet.getFactory().one(), BDD::and);
    BDD bddProtocol = Arrays.stream(packet.getIpProtocol().getBitvec())
        .reduce(packet.getFactory().one(), BDD::and);
    BDD bddIcmpType = Arrays.stream(packet.getIcmpType().getBitvec())
        .reduce(packet.getFactory().one(), BDD::and);
    BDD bddIcmpCode = Arrays.stream(packet.getIcmpCode().getBitvec())
        .reduce(packet.getFactory().one(), BDD::and);
    BDD bddDscp = Arrays.stream(packet.getDscp().getBitvec())
        .reduce(packet.getFactory().one(), BDD::and);
    BDD bddEcn = Arrays.stream(packet.getEcn().getBitvec())
        .reduce(packet.getFactory().one(), BDD::and);
    BDD bddFragOffset = Arrays.stream(packet.getFragmentOffset().getBitvec())
        .reduce(packet.getFactory().one(), BDD::and);
    BDD bddTcpBits = packet.getTcpAck()
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
  public void doTestWithLines(IBatfish  batfish, NodesSpecifier  nodesSpecifier) {
    Set<String> routerNames = nodesSpecifier.getMatchingNodes(batfish);
    SortedMap<String, Configuration> configs = batfish.loadConfigurations();
    Map<String, Map<String, IpAccessList>> aclNameToAcls = new TreeMap<>();
    Map<IpAccessList, String> aclToRouterName = new HashMap<>();

    for (String rr : routerNames) {
      Configuration cc = configs.get(rr);
      for (Entry<String, IpAccessList> entry : cc.getIpAccessLists().entrySet()) {
        IpAccessList acl = entry.getValue();
        Map<String, IpAccessList> routerToAcl = aclNameToAcls.getOrDefault(entry.getKey(), new HashMap<>());
        routerToAcl.put(rr, entry.getValue());
        aclNameToAcls.put(entry.getKey(), routerToAcl);
        aclToRouterName.put(acl, rr);
      }
    }

    for (Entry<String, Map<String, IpAccessList>> entry : aclNameToAcls.entrySet()) {
      Map<String, IpAccessList> accessLists = entry.getValue();
      BDDPacketWithLines  packet = new BDDPacketWithLines();
      if (accessLists.size() == 2) {
        List<String> routers = new ArrayList<>(accessLists.keySet());
        BDDAcl acl1 = BDDAcl.createWithLines(packet, routers.get(0), accessLists.get(routers.get(0)));
        BDDAcl acl2 = BDDAcl.createWithLines(packet, routers.get(1), accessLists.get(routers.get(1)));
        BDD first = acl1.getBdd();
        BDD second = acl2.getBdd();

        BDD acceptVar= packet.getAccept();

        BDD acceptFirst = first.restrict(acceptVar);
        BDD acceptSecond = second.restrict(acceptVar);
        BDD rejectFirst = first.restrict(acceptVar.not());
        BDD rejectSecond = second.restrict(acceptVar.not());
        BDD notEquivalent = acceptFirst.and(rejectSecond).or(acceptSecond.and(rejectFirst));

        if (notEquivalent.isZero()) {
          //System.out.println(entry.getKey() + " is consistent");
        } else {
          System.out.println("**************************");
          System.out.println(entry.getKey());
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
                System.out.print(routers.get(i) + " " );
                System.out.println(acl.getName());
                System.out.println("No matching lines");
              }
              i++;
            }
            AclLineDiffToPrefix diffToPrefix = new AclLineDiffToPrefix(
                aclList.get(0), aclList.get(1), lineDiff[0], lineDiff[1]);

            AclDiffReport report = diffToPrefix.getAclDiffReport(routers.get(0), routers.get(1));
            report.print((Batfish) batfish);
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
            System.out.println();
            linesNotEquivalent.andWith(counterexample.exist(getPacketHeaderFields(packet)).not());
          }
          //for (AclDiffReport r : reportList) {
          //  r.print((Batfish) batfish);
          //}
          System.out.println("**************************");
        }
      } else {
        System.out.print(entry.getKey() + " is present in ");
        accessLists.keySet()
            .forEach(x -> System.out.print(x + " " ));
        System.out.println();
      }
    }
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
          //System.out.println(entry.getKey() + " is consistent");
        } else {
          Queue<Prefix> prefixQueue = new ArrayDeque<>();
          prefixQueue.add(Prefix.ZERO);
          while (!prefixQueue.isEmpty()) {
            Prefix pfx = prefixQueue.remove();

            isEquivalent = first.and(second).or(first.not().and(second.not()));
            BDD pfxIsEquivalent = matchPrefix(packet, pfx).imp(isEquivalent);

            BDD bddDstIp = Arrays.stream(packet.getDstIp().getBitvec()).reduce(packet.getFactory().one(), BDD::and);
            BDD pfxNotEquivalent = matchPrefix(packet, pfx).imp(isEquivalent.not());
            BDD forallNotEquivalent = pfxNotEquivalent.forAll(bddDstIp);
            if (pfxIsEquivalent.isOne()) {
              //System.out.println(entry.getKey() + " is consistent on " + pfx);
            } else if (!forallNotEquivalent.isZero()){
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

  private static class RouteMapWrapper {
    public String router;
    public Configuration config;
    public RoutingPolicy policy;

    public RouteMapWrapper(String router, Configuration config, RoutingPolicy policy) {
      this.router = router;
      this.config = config;
      this.policy = policy;
    }
  }

  public void checkRoutingPolicy(IBatfish batfish, NodesSpecifier nodeRegex) {
    BDDPacket packet = new BDDPacket();

    Set<String> routers = nodeRegex.getMatchingNodes(batfish);
    SortedMap<String, Configuration> configs = batfish.loadConfigurations();
    Map<String, List<RouteMapWrapper>> nameToRoutePolicy = new TreeMap<>();
    Graph graph = new Graph(batfish);

    for (String rr : routers) {
      Configuration cc = configs.get(rr);
      System.out.println(cc.getHostname());
      for (Entry<String, RoutingPolicy> entry : cc.getRoutingPolicies().entrySet()) {
        String name = entry.getKey();
        RoutingPolicy policy = entry.getValue();
        RouteMapWrapper rmw = new RouteMapWrapper(rr, cc, policy);

        List<RouteMapWrapper> policyList = nameToRoutePolicy.getOrDefault(name, new ArrayList<>());
        policyList.add(rmw);
        nameToRoutePolicy.put(name, policyList);
      }
    }

    for (Entry<String, List<RouteMapWrapper>> entry : nameToRoutePolicy.entrySet()) {
      List<RouteMapWrapper> policyList = entry.getValue();
      if (policyList.size() == 2) {
        BDDRoute first =
            computeBDD(graph, policyList.get(0).config, policyList.get(0).policy, false);
        BDDRoute second =
            computeBDD(graph, policyList.get(1).config, policyList.get(1).policy, false);
        checkRoutingEquivalence(first, second, entry.getKey());
      }
    }
  }

  private void checkRoutingEquivalence(BDDRoute first, BDDRoute second, String name) {
    BDD result = first.getPrefix().getFactory().one();

    if (!first.getCommunities().keySet().equals(second.getCommunities().keySet())) {
      System.out.println("Routers disagree on " + name + " because of communities");
      return;
    }
    result.andWith(checkBDDIntegerEquivalence(first.getMetric(), second.getMetric()));
    result.andWith(checkBDDIntegerEquivalence(first.getLocalPref(), second.getLocalPref()));
    result.andWith(checkBDDIntegerEquivalence(first.getPrefix(), second.getPrefix()));
    result.andWith(checkBDDIntegerEquivalence(first.getPrefixLength(), second.getPrefixLength()));
    result.andWith(checkBDDIntegerEquivalence(first.getMed(), second.getMed()));
    result.andWith(checkBDDIntegerEquivalence(first.getAdminDist(), second.getAdminDist()));
    result.andWith(checkBDDIntegerEquivalence(first.getProtocolHistory().getInteger(), second.getProtocolHistory().getInteger()));
    result.andWith(checkBDDIntegerEquivalence(first.getOspfMetric().getInteger(), second.getOspfMetric().getInteger()));
    for(CommunityVar cvar : first.getCommunities().keySet()) {
      BDD bdd1 = first.getCommunities().get(cvar);
      BDD bdd2 = second.getCommunities().get(cvar);
      BDD isEquivalent = bdd1.and(bdd2).or(bdd1.not().and(bdd2.not()));
      result.andWith(isEquivalent);
    }

    // printBDD(result);
    if (result.isOne()) {
      System.out.println(name + " is consistent");
    } else {
      System.out.println("Routers disagree on " + name);
      BDD counterexample = result.not().fullSatOne();
      System.out.println(first.dot(counterexample));
      System.out.println(second.dot(counterexample));
    }
  }

  private BDD checkBDDIntegerEquivalence(BDDInteger first, BDDInteger second) {
    int length = first.getBitvec().length;
    if (second.getBitvec().length != length) {
      System.err.println("Comparing BDDIntegers with different lengths");
      return null;
    }

    BDD result = first.getFactory().one();
    for (int i = 0; i < length; i++) {
      BDD bit1 = first.getBitvec()[i];
      BDD bit2 = second.getBitvec()[i];
      BDD isEquivalent = bit1.and(bit2).or(bit1.not().and(bit2.not()));
      result.andWith(isEquivalent);
    }
    return result;
  }

  private void printRouteSatisfying(BDD route) {
    BDDRoute reference = new BDDRoute(new TreeSet<>());
    Optional<Long> prefix = reference.getPrefix().getValueSatisfying(route);
    Optional<Long> prefixLen = reference.getPrefixLength().getValueSatisfying(route);
    Optional<Long> metric = reference.getMetric().getValueSatisfying(route);
    Optional<Long> med = reference.getMed().getValueSatisfying(route);

    if (prefix.isPresent()) {
      System.out.println("PREFIX: " + prefix.get());
    }
    if (prefixLen.isPresent()) {
      System.out.println("PREFIX LENGTH: " + prefixLen.get());
    }
    if (metric.isPresent() && metric.get() != 0) {
      System.out.println(metric.get());
    }
    if (med.isPresent() && med.get() != 0) {
      System.out.println(med.get());
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
      BDD bitBDD = Ip.getBitAtPosition(bits, i) ?  dstIpBDD : dstIpBDD.not();
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

    BDD dstPortBDD = pkt.getDstPort().leq(region.getDstPort().getEnd())
        .and(pkt.getDstPort().geq(region.getDstPort().getStart()));

    BDD srcPortBDD = pkt.getSrcPort().leq(region.getSrcPort().getEnd())
        .and(pkt.getSrcPort().geq(region.getSrcPort().getStart()));

    BDD protoBDD = pkt.getIpProtocol().value(region.getProtocol().number());

    return bdd.and(dstPortBDD).and(srcPortBDD).and(protoBDD);
  }

}

package org.batfish.symbolic.bdd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.JFactory;
import org.batfish.common.bdd.BDDInteger;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.BDDPacketWithLines;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.specifier.NodeSpecifier;
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

  public void forallTest() {
    Random random = new Random();
    BDDFactory factory = JFactory.init(1000, 1000);

    factory.setVarNum(20);
    BDD result = factory.ithVar(1);
    for(int i = 2; i <= 10; i++) {
      BDD var = factory.ithVar(i);
      if (random.nextBoolean()) {
        result.andWith(var);
      } else {
        result.orWith(var);
      }
    }
    result.printDot();
    result.forAll(factory.ithVar(1).and(factory.ithVar(7))).printDot();
  }

  public void doTestWithLines(IBatfish  batfish, NodesSpecifier  nodesSpecifier) {
    BDDPacketWithLines  packet = new BDDPacketWithLines();
    Set<String> routers = nodesSpecifier.getMatchingNodes(batfish);
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
      }
    }

    for (Entry<String, List<IpAccessList>> entry : aclNameToAcls.entrySet()) {
      List<IpAccessList> accessLists = entry.getValue();
      if (accessLists.size() == 2) {
        System.out.println(entry.getKey());
        BDDAcl acl1 = BDDAcl.createWithLines(packet, "router1", accessLists.get(0));
        BDDAcl acl2 = BDDAcl.createWithLines(packet, "router2", accessLists.get(1));
        BDD first = acl1.getBdd();
        BDD second = acl2.getBdd();

        for(IpAccessListLine line: accessLists.get(0).getLines()) {
          System.out.println(line);
          System.out.println(packet.getAclLine("router1", accessLists.get(0), line).var());
        }
        for(IpAccessListLine line: accessLists.get(1).getLines()) {
          System.out.println(line);
          System.out.println(packet.getAclLine("router2", accessLists.get(1), line).var());
        }
        System.out.println("ACCEPT");
        System.out.println(packet.getAccept().var());
        BDD acceptVar= packet.getAccept();

        BDD acceptFirst = first.restrict(acceptVar);
        BDD acceptSecond = second.restrict(acceptVar);
        BDD rejectFirst = first.restrict(acceptVar.not());
        BDD rejectSecond = second.restrict(acceptVar.not());
        BDD isEquivalent = acceptFirst.biimp(acceptSecond).and(rejectFirst.biimp(rejectSecond));

        isEquivalent.printDot();
        if (isEquivalent.isOne()) {
          System.out.println(entry.getKey() + " is consistent");
        } else {
          BDD counterexample = isEquivalent.not().fullSatOne();
          long dstIp = packet.getDstIp().getValueSatisfying(counterexample).get();
          long srcIp = packet.getSrcIp().getValueSatisfying(counterexample).get();
          long dstPort = packet.getDstPort().getValueSatisfying(counterexample).get();
          long srcPort = packet.getSrcPort().getValueSatisfying(counterexample).get();
          System.out.println("EXAMPLE:");
          System.out.println("\tDST-IP:    " + Ip.create(dstIp));
          System.out.println("\tSRC-IP:    " + Ip.create(srcIp));
          System.out.println("\tDST-PORT:  " + dstPort);
          System.out.println("\tSRC-PORT:  " + srcPort);
          int i = 1;
          for(IpAccessList acl : accessLists) {
            for (IpAccessListLine line : acl.getLines()) {
              if (!counterexample.and(packet.getAclLine("router"+i, acl, line)).isZero()) {
                System.out.println("router"+i);
                System.out.println(acl.getName() + ":");
                System.out.println(line.getName());
                break;
              }
            }
            i++;
          }
          System.out.println();


          /*
          Queue<Prefix> prefixQueue = new ArrayDeque<>();
          prefixQueue.add(Prefix.ZERO);
          while (!prefixQueue.isEmpty()) {
            Prefix pfx = prefixQueue.remove();

            isEquivalent = first.and(second).or(first.not().and(second.not()));
            BDD pfxIsEquivalent = matchPrefix(packet, pfx).imp(isEquivalent);

            BDD bddDstIp = Arrays.stream(packet.getDstIp().getBitvec())
                .reduce(packet.getFactory().one(), BDD::and);
            BDD pfxNotEquivalent = matchPrefix(packet, pfx).imp(isEquivalent.not());
            BDD forallNotEquivalent = pfxNotEquivalent.forAll(bddDstIp);
            if (pfxIsEquivalent.isOne()) {
              //System.out.println(entry.getKey() + " is consistent on " + pfx);
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
          */
        }
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
          long dstIp = packet.getDstIp().getValueSatisfying(bddAcl.getBdd()).get();
          long srcIp = packet.getSrcIp().getValueSatisfying(bddAcl.getBdd()).get();
          System.out.println(acl);
          System.out.println("DST: " + Ip.create(dstIp));
          System.out.println("SRC: " + Ip.create(srcIp));
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


/*
    network.getInAcls().entrySet().forEach(ent -> {
      System.out.println(ent.getKey());
      System.out.println(ent.getKey().getStart().getOutgoingFilter());
      System.out.println(ent.getKey().getEnd().getIncomingFilter());
      BDDAcl acl = ent.getValue();
      System.out.println("PACKET");
      System.out.println(acl.getPkt()._bitNames);
      System.out.println();
      System.out.println("ACL");
      System.out.println(acl.getBdd());
      acl.getBdd().printSetWithDomains();
      acl.getBdd().printDot();
      System.out.println();
    });
    */
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

  private void printBDD(BDD bdd) {
    if (bdd.isOne()) {
      System.out.println("Always True");
    } else if (bdd.isZero()) {
      System.out.println("Always False");
    } else {
      bdd.printDot();
    }
  }
}

package org.batfish.symbolic.bdd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import net.sf.javabdd.BDD;
import org.batfish.common.bdd.BDDInteger;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.BDDPacketWithLines;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.AclIpSpace;
import org.batfish.datamodel.AclIpSpaceLine;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.EmptyIpSpace;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.IpIpSpace;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.IpSpaceReference;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.IpWildcardIpSpace;
import org.batfish.datamodel.IpWildcardSetIpSpace;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixIpSpace;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.UniverseIpSpace;
import org.batfish.datamodel.acl.AclLineMatchExpr;
import org.batfish.datamodel.acl.AndMatchExpr;
import org.batfish.datamodel.acl.FalseExpr;
import org.batfish.datamodel.acl.GenericAclLineMatchExprVisitor;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.datamodel.acl.MatchSrcInterface;
import org.batfish.datamodel.acl.NotMatchExpr;
import org.batfish.datamodel.acl.OrMatchExpr;
import org.batfish.datamodel.acl.OriginatingFromDevice;
import org.batfish.datamodel.acl.PermittedByAcl;
import org.batfish.datamodel.acl.TrueExpr;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.visitors.GenericIpSpaceVisitor;
import org.batfish.symbolic.CommunityVar;
import org.batfish.symbolic.Graph;
import scala.Int;

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
          System.out.println(entry.getKey() + " is consistent");
        } else {
          System.out.println("**************************");
          System.out.println(entry.getKey());
          BDD linesNotEquivalent = notEquivalent.exist(getPacketHeaderFields(packet));
          //linesNotEquivalent.printDot();
          while (!linesNotEquivalent.isZero()) {
            BDD lineSat = linesNotEquivalent.satOne();
            BDD counterexample = notEquivalent.and(lineSat).satOne();
            // counterexample.printDot();
            long dstIp = packet.getDstIp().getValueSatisfying(counterexample).get();
            long srcIp = packet.getSrcIp().getValueSatisfying(counterexample).get();
            long dstPort = packet.getDstPort().getValueSatisfying(counterexample).get();
            long srcPort = packet.getSrcPort().getValueSatisfying(counterexample).get();
            System.out.println("EXAMPLE:");
            System.out.println("\tDST-IP:    " + Ip.create(dstIp));
            System.out.println("\tSRC-IP:    " + Ip.create(srcIp));
            System.out.println("\tDST-PORT:  " + dstPort);
            System.out.println("\tSRC-PORT:  " + srcPort);
            int i = 0;
            IpAccessListLine[] lineDiff = new IpAccessListLine[2];
            List<IpAccessList> aclList = new ArrayList<>(accessLists.values());
            for (IpAccessList acl : accessLists.values()) {
              boolean found = false;
              for (IpAccessListLine line : acl.getLines()) {
                if (!counterexample.and(packet.getAclLine(routers.get(i), acl, line)).isZero()) {
                  System.out.print(routers.get(i) + " ");
                  System.out.println(acl.getName() + ":");
                  System.out.println(line.getName());
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
            getDifferenceInPrefixes(aclList.get(0), aclList.get(1), lineDiff[0], lineDiff[1]);
            System.out.println();
            linesNotEquivalent.andWith(lineSat.not());
          }
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

  private static class PrefixSpace {
    Prefix dstIp;
    Prefix srcIp;
    SubRange dstPort;
    SubRange srcPort;

    private static final SubRange DEFAULT_PORT_RANGE;

    static {
      DEFAULT_PORT_RANGE = new SubRange(0, 65535);
    }

    private static class IpSpaceVisitor implements GenericIpSpaceVisitor<List<Prefix>> {

      @SuppressWarnings("unchecked")
      @Override public List<Prefix> castToGenericIpSpaceVisitorReturnType(Object o) {
        return (List<Prefix>) o;
      }

      @Override public List<Prefix> visitAclIpSpace(AclIpSpace aclIpSpace) {
        ArrayList<Prefix> ret = new ArrayList<>();
        for (AclIpSpaceLine line : aclIpSpace.getLines()) {
          if (line.getAction().equals(LineAction.DENY)) {
            System.err.println("AclIpSpace uses DENY action");
            return getAllPrefix();
          }
          ret.addAll(line.getIpSpace().accept(this));
        }
        return ret;
      }

      @Override public List<Prefix> visitEmptyIpSpace(EmptyIpSpace emptyIpSpace) {
        return new ArrayList<>();
      }

      @Override public List<Prefix> visitIpIpSpace(IpIpSpace ipIpSpace) {
        List<Prefix> ret = new ArrayList<>();
        ret.add(Prefix.create(ipIpSpace.getIp(), Prefix.MAX_PREFIX_LENGTH));
        return ret;

      }

      @Override public List<Prefix> visitIpSpaceReference(IpSpaceReference ipSpaceReference) {
        System.err.println("Uses IpSpaceReference");
        return getAllPrefix();
      }

      @Override public List<Prefix> visitIpWildcardIpSpace(
          IpWildcardIpSpace ipWildcardIpSpace) {
        if (ipWildcardIpSpace.getIpWildcard().isPrefix()) {
          List<Prefix> ret = new ArrayList<>();
          ret.add(ipWildcardIpSpace.getIpWildcard().toPrefix());
          return ret;
        }
        System.err.println("IpWildCard is not prefix: " + ipWildcardIpSpace.getIpWildcard());
        return getAllPrefix();
      }

      @Override public List<Prefix> visitIpWildcardSetIpSpace(
          IpWildcardSetIpSpace ipWildcardSetIpSpace) {
        if (ipWildcardSetIpSpace.getBlacklist().size() > 0) {
          System.err.println("IpWildcardSetIpSpace has blacklist");
          return getAllPrefix();
        }
        ArrayList<Prefix> ret = new ArrayList<>();
        for (IpWildcard wildcard : ipWildcardSetIpSpace.getWhitelist()) {
          if (wildcard.isPrefix()) {
            ret.add(wildcard.toPrefix());
          } else {
            System.err.println("IpWildCard is not prefix: " + wildcard);
            return getAllPrefix();
          }
        }
        return ret;
      }

      @Override public List<Prefix> visitPrefixIpSpace(PrefixIpSpace prefixIpSpace) {
        List<Prefix> ret = new ArrayList<>();
        ret.add(prefixIpSpace.getPrefix());
        return ret;
      }

      @Override public List<Prefix> visitUniverseIpSpace(UniverseIpSpace universeIpSpace) {
        return getAllPrefix();
      }

    }

    private static class AclLineVisitor implements GenericAclLineMatchExprVisitor<List<PrefixSpace>> {

      @Override public List<PrefixSpace> visitAndMatchExpr(AndMatchExpr andMatchExpr) {
        List<PrefixSpace> ret = new ArrayList<>();
        List<PrefixSpace> temp = new ArrayList<>();
        ret.add(getUniverseSpace());
        for(AclLineMatchExpr line : andMatchExpr.getConjuncts()) {
          for (PrefixSpace ps1 : ret) {
            for (PrefixSpace ps2 : visit(line)) {
              Optional<PrefixSpace> optional = ps1.intersection(ps2);
              if(optional.isPresent()) {
                temp.add(optional.get());
              }
            }
          }
          ret = temp;
        }
        return ret;
      }

      @Override public List<PrefixSpace> visitFalseExpr(FalseExpr falseExpr) {
        List<PrefixSpace> ret = new ArrayList<>();
        return ret;
      }

      @Override public List<PrefixSpace> visitMatchHeaderSpace(MatchHeaderSpace matchHeaderSpace) {
        HeaderSpace space = matchHeaderSpace.getHeaderspace();
        List<Prefix> dstPrefixes;
        List<Prefix> srcPrefixes;
        List<SubRange> dstPorts;
        List<SubRange> srcPorts;

        if (space.getDstIps() != null) {
          dstPrefixes = space.getDstIps().accept(new IpSpaceVisitor());
        } else {
          dstPrefixes = getAllPrefix();
        }
        if (space.getSrcIps() != null) {
          srcPrefixes = space.getSrcIps().accept(new IpSpaceVisitor());
        } else {
          srcPrefixes = getAllPrefix();
        }
        if (space.getDstPorts() == null || space.getDstPorts().size() == 0) {
          dstPorts = new ArrayList<>();
          dstPorts.add(DEFAULT_PORT_RANGE);
        } else {
          dstPorts = new ArrayList<>(space.getDstPorts());
        }
        if (space.getSrcPorts() == null || space.getSrcPorts().size() == 0) {
          srcPorts = new ArrayList<>();
          srcPorts.add(DEFAULT_PORT_RANGE);
        } else {
          srcPorts = new ArrayList<>(space.getSrcPorts());
        }

        List<PrefixSpace> ret = new ArrayList<>();


        for (Prefix dst : dstPrefixes) {
          for (Prefix src : srcPrefixes) {
            for (SubRange dstPort : dstPorts) {
              for (SubRange srcPort : srcPorts) {
                ret.add(new PrefixSpace(dst, src, dstPort, srcPort));
              }
            }
          }
        }

        return ret;
      }

      @Override public List<PrefixSpace> visitMatchSrcInterface(MatchSrcInterface matchSrcInterface) {
        System.err.println("Uses MatchSrcExpr in ACL");
        return getAllPackets();
      }

      @Override public List<PrefixSpace> visitNotMatchExpr(NotMatchExpr notMatchExpr) {
        System.err.println("Uses NotMatchExpr in ACL");
        return getAllPackets();
      }

      @Override public List<PrefixSpace> visitOriginatingFromDevice(
          OriginatingFromDevice originatingFromDevice) {
        System.err.println("Uses OriginatingFromDevice in ACL");
        return getAllPackets();
      }

      @Override public List<PrefixSpace> visitOrMatchExpr(OrMatchExpr orMatchExpr) {
        List<PrefixSpace> ret = new ArrayList<>();
        for (AclLineMatchExpr expr : orMatchExpr.getDisjuncts()) {
          ret.addAll(visit(expr));
        }
        return ret;
      }

      @Override public List<PrefixSpace> visitPermittedByAcl(PermittedByAcl permittedByAcl) {
        System.err.println("Uses PermittedByAcl in ACL");
        return getAllPackets();
      }

      @Override public List<PrefixSpace> visitTrueExpr(TrueExpr trueExpr) {
        return getAllPackets();
      }

    }

    private static List<PrefixSpace> getAllPackets() {
      List<PrefixSpace> ret = new ArrayList<>();
      ret.add(new PrefixSpace(Prefix.ZERO, Prefix.ZERO, DEFAULT_PORT_RANGE, DEFAULT_PORT_RANGE));
      return ret;
    }

    private static List<Prefix> getAllPrefix() {
      List<Prefix> ret = new ArrayList<>();
      ret.add(Prefix.ZERO);
      return ret;
    }

    public PrefixSpace(Prefix dstIp, Prefix srcIp, SubRange dstPort, SubRange srcPort) {
      this.dstIp = dstIp;
      this.srcIp = srcIp;
      this.dstPort = dstPort;
      this.srcPort = srcPort;
    }

    public PrefixSpace(PrefixSpace other) {
      this(other.dstIp,other.srcIp, other.dstPort, other.srcPort);
    }

    public static List<PrefixSpace> createPrefixSpace(IpAccessListLine line) {
      return line.getMatchCondition().accept(new AclLineVisitor());
    }

    public static PrefixSpace getUniverseSpace() {
      return new PrefixSpace(Prefix.ZERO, Prefix.ZERO, DEFAULT_PORT_RANGE, DEFAULT_PORT_RANGE);
    }

    public boolean contains(PrefixSpace other) {
      if (this.srcIp.containsPrefix(other.srcIp)
          && this.dstIp.containsPrefix(other.dstIp)
          && this.dstPort.contains(other.dstPort)
          && this.srcPort.contains(other.srcPort)) {
        return true;
      }
      return false;
    }

    public Optional<PrefixSpace> intersection(PrefixSpace other) {
      Prefix smallerDst;
      Prefix smallerSrc;
      if (this.dstIp.containsPrefix(other.dstIp)) {
        smallerDst = other.dstIp;
      } else if (other.dstIp.containsPrefix(this.dstIp)) {
        smallerDst = this.dstIp;
      } else {
        return Optional.empty();
      }

      if (this.srcIp.containsPrefix(other.srcIp)) {
        smallerSrc = other.srcIp;
      } else if (other.srcIp.containsPrefix(this.srcIp)) {
        smallerSrc = this.srcIp;
      } else {
        return Optional.empty();
      }

      Optional<SubRange> dstPortRange = this.dstPort.intersection(other.dstPort);
      Optional<SubRange> srcPortRange = this.srcPort.intersection(other.srcPort);
      if (!dstPortRange.isPresent() || !srcPortRange.isPresent()) {
        return Optional.empty();
      }

      return Optional.of(
          new PrefixSpace(smallerDst, smallerSrc, dstPortRange.get(), srcPortRange.get()));

    }

    @Override public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PrefixSpace that = (PrefixSpace) o;
      return dstIp.equals(that.dstIp) && srcIp.equals(that.srcIp)
          && dstPort.equals(that.dstPort) && srcPort.equals(that.srcPort);
    }

    @Override public int hashCode() {
      int ret = 0;
      ret ^= Integer.rotateLeft(dstIp.hashCode(), 0);
      ret ^= Integer.rotateLeft(srcIp.hashCode(), 8);
      ret ^= Integer.rotateLeft(dstPort.hashCode(), 16);
      ret ^= Integer.rotateLeft(srcPort.hashCode(), 24);
      return ret;
    }

    @Override public String toString() {
      if (dstPort.equals(DEFAULT_PORT_RANGE) && srcPort.equals(DEFAULT_PORT_RANGE)) {
        return "<" + "dstIp=" + dstIp + ", srcIp=" + srcIp + '>';
      } else {
        return "<" + "dstIp=" + dstIp
            + ", srcIp=" + srcIp
            + ", dstPort=" + dstPort
            + ", srcPort=" + srcPort + '>';
      }

    }
  }

  /**
   * Given two ACL lines in corresponding ACLs, prints out the intersection of the lines minus the
   * prefixes in the previous lines
   */
  private void getDifferenceInPrefixes(IpAccessList acl1, IpAccessList acl2,
      IpAccessListLine line1, IpAccessListLine line2) {
    List<PrefixSpace> spaces1 = PrefixSpace.createPrefixSpace(line1);
    List<PrefixSpace> spaces2 = PrefixSpace.createPrefixSpace(line2);

    System.out.println("DIFFERENCES");
    List<PrefixSpace> resultSpaces = new ArrayList<>();

    for (PrefixSpace ps1 : spaces1) {
      for (PrefixSpace ps2 : spaces2) {
        Optional<PrefixSpace> optional = ps1.intersection(ps2);
        if (optional.isPresent()) {
          resultSpaces.add(optional.get());
        }
      }
    }
    for (PrefixSpace resultSpace : resultSpaces) {
      boolean doPrint = true;
      List<PrefixSpace> diffs = new ArrayList<>();
      List<IpAccessListLine> aclLines = new ArrayList<>(acl1.getLines());
      aclLines.addAll(acl2.getLines());
      for (IpAccessListLine line : aclLines) {
        if (line.equals(line1)) {
          break;
        }
        List<PrefixSpace> lineSpaces = PrefixSpace.createPrefixSpace(line);
        for (PrefixSpace lineSpace : lineSpaces) {
          if (lineSpace.contains(resultSpace)) {
            doPrint = false;
            break;
          }
          Optional<PrefixSpace> optional = lineSpace.intersection(resultSpace);
          if (optional.isPresent()) {
            PrefixSpace intersection = optional.get();
            boolean skip = false;
            for (int i = 0; i < diffs.size(); i++) {
              if (intersection.contains(diffs.get(i))) {
                diffs.set(i, intersection);
                break;
              } else if (diffs.get(i).contains(intersection)) {
                skip = true;
                break;
              }
            }
            if (!skip) {
              diffs.add(intersection);
            }
          }
        }
      }
      if (doPrint) {
        System.out.println(resultSpace);
        for (PrefixSpace sp : diffs) {
          System.out.println("\t- " + sp);
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

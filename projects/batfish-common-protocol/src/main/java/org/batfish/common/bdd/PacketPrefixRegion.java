package org.batfish.common.bdd;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import org.batfish.datamodel.AclIpSpace;
import org.batfish.datamodel.AclIpSpaceLine;
import org.batfish.datamodel.EmptyIpSpace;
import org.batfish.datamodel.FlowState;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.IpIpSpace;
import org.batfish.datamodel.IpProtocol;
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
import org.batfish.datamodel.visitors.GenericIpSpaceVisitor;

public class PacketPrefixRegion {

  private Prefix _dstIp;
  private Prefix _srcIp;
  private SubRange _dstPort;
  private SubRange _srcPort;
  private IpProtocol _protocol;
  private FlowState _flowState;

  private static final SubRange DEFAULT_PORT_RANGE;

  static {
    DEFAULT_PORT_RANGE = new SubRange(0, 65535);
  }

  private static class IpSpaceVisitor implements GenericIpSpaceVisitor<List<Prefix>> {

    @SuppressWarnings("unchecked")
    @Override
    public List<Prefix> castToGenericIpSpaceVisitorReturnType(Object o) {
      return (List<Prefix>) o;
    }

    @Override
    public List<Prefix> visitAclIpSpace(AclIpSpace aclIpSpace) {
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

    @Override
    public List<Prefix> visitEmptyIpSpace(EmptyIpSpace emptyIpSpace) {
      return new ArrayList<>();
    }

    @Override
    public List<Prefix> visitIpIpSpace(IpIpSpace ipIpSpace) {
      List<Prefix> ret = new ArrayList<>();
      ret.add(Prefix.create(ipIpSpace.getIp(), Prefix.MAX_PREFIX_LENGTH));
      return ret;
    }

    @Override
    public List<Prefix> visitIpSpaceReference(IpSpaceReference ipSpaceReference) {
      System.err.println("Uses IpSpaceReference");
      return getAllPrefix();
    }

    @Override
    public List<Prefix> visitIpWildcardIpSpace(IpWildcardIpSpace ipWildcardIpSpace) {
      if (ipWildcardIpSpace.getIpWildcard().isPrefix()) {
        List<Prefix> ret = new ArrayList<>();
        ret.add(ipWildcardIpSpace.getIpWildcard().toPrefix());
        return ret;
      }
      System.err.println("IpWildCard is not prefix: " + ipWildcardIpSpace.getIpWildcard());
      return getAllPrefix();
    }

    @Override
    public List<Prefix> visitIpWildcardSetIpSpace(IpWildcardSetIpSpace ipWildcardSetIpSpace) {
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

    @Override
    public List<Prefix> visitPrefixIpSpace(PrefixIpSpace prefixIpSpace) {
      List<Prefix> ret = new ArrayList<>();
      ret.add(prefixIpSpace.getPrefix());
      return ret;
    }

    @Override
    public List<Prefix> visitUniverseIpSpace(UniverseIpSpace universeIpSpace) {
      return getAllPrefix();
    }
  }

  private static class AclLineVisitor
      implements GenericAclLineMatchExprVisitor<List<PacketPrefixRegion>> {

    @Override
    public List<PacketPrefixRegion> visitAndMatchExpr(AndMatchExpr andMatchExpr) {
      List<PacketPrefixRegion> ret = new ArrayList<>();
      List<PacketPrefixRegion> temp = new ArrayList<>();
      ret.add(getUniverseSpace());
      for (AclLineMatchExpr line : andMatchExpr.getConjuncts()) {
        for (PacketPrefixRegion ps1 : ret) {
          for (PacketPrefixRegion ps2 : visit(line)) {
            Optional<PacketPrefixRegion> optional = ps1.intersection(ps2);
            if (optional.isPresent()) {
              temp.add(optional.get());
            }
          }
        }
        ret = temp;
      }
      return ret;
    }

    @Override
    public List<PacketPrefixRegion> visitFalseExpr(FalseExpr falseExpr) {
      List<PacketPrefixRegion> ret = new ArrayList<>();
      return ret;
    }

    @Override
    public List<PacketPrefixRegion> visitMatchHeaderSpace(MatchHeaderSpace matchHeaderSpace) {
      HeaderSpace space = matchHeaderSpace.getHeaderspace();
      List<Prefix> dstPrefixes;
      List<Prefix> srcPrefixes;
      List<SubRange> dstPorts;
      List<SubRange> srcPorts;
      List<IpProtocol> protocols;

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
      if (space.getIpProtocols() == null || space.getIpProtocols().size() == 0) {
        protocols = new ArrayList<>();
        protocols.add(IpProtocol.ISO_IP);
      } else {
        protocols = new ArrayList<>(space.getIpProtocols());
      }
      // TODO: Properly handle other packet attributes
      if (space.getTcpFlags() != null && space.getTcpFlags().size() > 0) {
        return new ArrayList<>();
      }

      List<PacketPrefixRegion> ret = new ArrayList<>();

      for (Prefix dst : dstPrefixes) {
        for (Prefix src : srcPrefixes) {
          for (SubRange dstPort : dstPorts) {
            for (SubRange srcPort : srcPorts) {
              for (IpProtocol proto : protocols)
                ret.add(new PacketPrefixRegion(dst, src, dstPort, srcPort, proto));
            }
          }
        }
      }

      return ret;
    }

    @Override
    public List<PacketPrefixRegion> visitMatchSrcInterface(MatchSrcInterface matchSrcInterface) {
      System.err.println("Uses MatchSrcExpr in ACL");
      return getAllPackets();
    }

    @Override
    public List<PacketPrefixRegion> visitNotMatchExpr(NotMatchExpr notMatchExpr) {
      System.err.println("Uses NotMatchExpr in ACL");
      return getAllPackets();
    }

    @Override
    public List<PacketPrefixRegion> visitOriginatingFromDevice(
        OriginatingFromDevice originatingFromDevice) {
      System.err.println("Uses OriginatingFromDevice in ACL");
      return getAllPackets();
    }

    @Override
    public List<PacketPrefixRegion> visitOrMatchExpr(OrMatchExpr orMatchExpr) {
      List<PacketPrefixRegion> ret = new ArrayList<>();
      for (AclLineMatchExpr expr : orMatchExpr.getDisjuncts()) {
        ret.addAll(visit(expr));
      }
      return ret;
    }

    @Override
    public List<PacketPrefixRegion> visitPermittedByAcl(PermittedByAcl permittedByAcl) {
      System.err.println("Uses PermittedByAcl in ACL");
      return getAllPackets();
    }

    @Override
    public List<PacketPrefixRegion> visitTrueExpr(TrueExpr trueExpr) {
      return getAllPackets();
    }
  }

  private static List<PacketPrefixRegion> getAllPackets() {
    List<PacketPrefixRegion> ret = new ArrayList<>();
    ret.add(getUniverseSpace());
    return ret;
  }

  private static List<Prefix> getAllPrefix() {
    List<Prefix> ret = new ArrayList<>();
    ret.add(Prefix.ZERO);
    return ret;
  }

  public PacketPrefixRegion(
      Prefix dstIp, Prefix srcIp, SubRange dstPort, SubRange srcPort, IpProtocol proto) {
    this._dstIp = dstIp;
    this._srcIp = srcIp;
    this._dstPort = dstPort;
    this._srcPort = srcPort;
    this._protocol = proto;
  }

  public PacketPrefixRegion(PacketPrefixRegion other) {
    this(other._dstIp, other._srcIp, other._dstPort, other._srcPort, other._protocol);
  }

  public static List<PacketPrefixRegion> createPrefixSpace(IpAccessListLine line) {
    if (line == null) {
      List<PacketPrefixRegion> ret = new ArrayList<>();
      ret.add(getUniverseSpace());
      return ret;
    }
    return line.getMatchCondition().accept(new AclLineVisitor());
  }

  public static PacketPrefixRegion getUniverseSpace() {
    return new PacketPrefixRegion(
        Prefix.ZERO, Prefix.ZERO, DEFAULT_PORT_RANGE, DEFAULT_PORT_RANGE, IpProtocol.ISO_IP);
  }

  public boolean contains(PacketPrefixRegion other) {
    if (this._srcIp.containsPrefix(other._srcIp)
        && this._dstIp.containsPrefix(other._dstIp)
        && this._dstPort.contains(other._dstPort)
        && this._srcPort.contains(other._srcPort)
        && (this._protocol.equals(other._protocol) || this._protocol.equals(IpProtocol.ISO_IP))) {
      return true;
    }
    return false;
  }

  public Optional<PacketPrefixRegion> intersection(PacketPrefixRegion other) {
    Prefix smallerDst;
    Prefix smallerSrc;
    if (this._dstIp.containsPrefix(other._dstIp)) {
      smallerDst = other._dstIp;
    } else if (other._dstIp.containsPrefix(this._dstIp)) {
      smallerDst = this._dstIp;
    } else {
      return Optional.empty();
    }

    if (this._srcIp.containsPrefix(other._srcIp)) {
      smallerSrc = other._srcIp;
    } else if (other._srcIp.containsPrefix(this._srcIp)) {
      smallerSrc = this._srcIp;
    } else {
      return Optional.empty();
    }

    IpProtocol proto;
    if (this._protocol.equals(IpProtocol.ISO_IP) || this._protocol.equals(other._protocol)) {
      proto = other._protocol;
    } else if (other._protocol.equals(IpProtocol.ISO_IP)) {
      proto = this._protocol;
    } else {
      return Optional.empty();
    }

    Optional<SubRange> dstPortRange = this._dstPort.intersection(other._dstPort);
    Optional<SubRange> srcPortRange = this._srcPort.intersection(other._srcPort);
    if (!dstPortRange.isPresent() || !srcPortRange.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(
        new PacketPrefixRegion(
            smallerDst, smallerSrc, dstPortRange.get(), srcPortRange.get(), proto));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PacketPrefixRegion that = (PacketPrefixRegion) o;
    return _dstIp.equals(that._dstIp)
        && _srcIp.equals(that._srcIp)
        && _dstPort.equals(that._dstPort)
        && _srcPort.equals(that._srcPort)
        && _protocol.equals(that._protocol);
  }

  @Override
  public int hashCode() {
    int ret = 0;
    ret ^= Integer.rotateLeft(_dstIp.hashCode(), 0);
    ret ^= Integer.rotateLeft(_srcIp.hashCode(), 8);
    ret ^= Integer.rotateLeft(_dstPort.hashCode(), 16);
    ret ^= Integer.rotateLeft(_srcPort.hashCode(), 24);
    return ret;
  }

  @Override
  public String toString() {
    return "<" + ipAddrToStr() + portNumToStr() + protoToStr() + ">";
  }

  private String ipAddrToStr() {
    return "srcIp=" + _srcIp + ", dstIp=" + _dstIp;
  }

  private String portNumToStr() {
    if (_dstPort.equals(DEFAULT_PORT_RANGE) && _srcPort.equals(DEFAULT_PORT_RANGE)) {
      return "";
    }
    return ", srcPort=" + _srcPort + ", dstPort=" + _dstPort;
  }

  private String protoToStr() {
    if (_protocol.equals(IpProtocol.ISO_IP)) {
      return "";
    }
    return ", " + _protocol;
  }

  public Prefix getDstIp() {
    return _dstIp;
  }

  public Prefix getSrcIp() {
    return _srcIp;
  }

  public SubRange getDstPort() {
    return _dstPort;
  }

  public SubRange getSrcPort() {
    return _srcPort;
  }

  public IpProtocol getProtocol() {
    return _protocol;
  }
}

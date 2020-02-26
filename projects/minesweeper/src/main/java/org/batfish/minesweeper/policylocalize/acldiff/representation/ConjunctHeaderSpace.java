package org.batfish.minesweeper.policylocalize.acldiff.representation;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.SubRange;

/*
Represents a conjunction of constraints on IP addresses and ports of headers.
*/
public class ConjunctHeaderSpace extends AbstractHeaderSpace implements Comparable<ConjunctHeaderSpace> {

  private Prefix _dstIp;
  private Prefix _srcIp;
  private SubRange _dstPort;
  private SubRange _srcPort;
  private IpProtocol _protocol;

  public static final SubRange DEFAULT_PORT_RANGE;

  static {
    DEFAULT_PORT_RANGE = new SubRange(0, 65535);
  }


  public static ConjunctHeaderSpace getUniverseSpace() {
    return new ConjunctHeaderSpace(
        Prefix.ZERO, Prefix.ZERO, DEFAULT_PORT_RANGE, DEFAULT_PORT_RANGE, IpProtocol.ISO_IP);
  }

  public ConjunctHeaderSpace(
      Prefix dstIp, Prefix srcIp, SubRange dstPort, SubRange srcPort, IpProtocol proto) {
    this._dstIp = dstIp;
    this._srcIp = srcIp;
    this._dstPort = dstPort;
    this._srcPort = srcPort;
    this._protocol = proto;
  }

  public ConjunctHeaderSpace(ConjunctHeaderSpace other) {
    this(other._dstIp, other._srcIp, other._dstPort, other._srcPort, other._protocol);
  }

  public boolean contains(ConjunctHeaderSpace other) {
    return this._srcIp.containsPrefix(other._srcIp) && this._dstIp.containsPrefix(other._dstIp)
        && this._dstPort.contains(other._dstPort) && this._srcPort.contains(other._srcPort)
        && (this._protocol.equals(other._protocol) || this._protocol.equals(IpProtocol.ISO_IP));
  }

  private Optional<Prefix> intersectPrefixes(Prefix a, Prefix b) {
    if (a.containsPrefix(b)) {
      return Optional.of(b);
    }
    if (b.containsPrefix(a)) {
      return Optional.of(a);
    }
    return Optional.empty();
  }

  private Optional<IpProtocol> intersectProtocol(IpProtocol a, IpProtocol b) {

    if (a.equals(IpProtocol.ISO_IP) || a.equals(b)) {
      return Optional.of(b);
    }
    if (b.equals(IpProtocol.ISO_IP)) {
      return Optional.of(a);
    }
    return Optional.empty();
  }

  public Optional<ConjunctHeaderSpace> intersection(ConjunctHeaderSpace other) {
    Optional<Prefix> smallerDst;
    Optional<Prefix> smallerSrc;
    smallerDst = intersectPrefixes(this._dstIp, other._dstIp);
    smallerSrc = intersectPrefixes(this._srcIp, other._srcIp);

    if (!smallerDst.isPresent() || !smallerSrc.isPresent()) {
      return Optional.empty();
    }

    Optional<IpProtocol> proto = intersectProtocol(this._protocol, other._protocol);
    if (!proto.isPresent()) {
      return Optional.empty();
    }

    Optional<SubRange> dstPortRange = this._dstPort.intersection(other._dstPort);
    Optional<SubRange> srcPortRange = this._srcPort.intersection(other._srcPort);
    if (!dstPortRange.isPresent() || !srcPortRange.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(
        new ConjunctHeaderSpace(
            smallerDst.get(),
            smallerSrc.get(),
            dstPortRange.get(),
            srcPortRange.get(),
            proto.get()));
  }

  public boolean intersects(ConjunctHeaderSpace other) {
    return intersection(other).isPresent();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConjunctHeaderSpace that = (ConjunctHeaderSpace) o;
    return _dstIp.equals(that._dstIp)
        && _srcIp.equals(that._srcIp)
        && _dstPort.equals(that._dstPort)
        && _srcPort.equals(that._srcPort)
        && _protocol.equals(that._protocol);
  }

  @Override public int hashCode() {
    return Objects.hash(_dstIp, _srcIp, _dstPort, _srcPort, _protocol);
  }

  @Override
  public String toString() {
    return ipAddrToStr() + portNumToStr() + protoToStr();
  }

  private String ipAddrToStr() {
    return String.format("srcIp: %s\ndstIP: %s", _srcIp, _dstIp);
  }

  private String portNumToStr() {
    if (_dstPort.equals(DEFAULT_PORT_RANGE) && _srcPort.equals(DEFAULT_PORT_RANGE)) {
      return "";
    }
    return String.format("\nsrcPort: %s\ndstPort: %s", _srcPort, _dstPort);
  }

  private String protoToStr() {
    if (_protocol.equals(IpProtocol.ISO_IP)) {
      return "";
    }
    return "\n" + _protocol;
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

  @Override public int compareTo(@Nonnull ConjunctHeaderSpace conjunctHeaderSpace) {
    return Comparator.comparing(ConjunctHeaderSpace::getDstIp)
        .thenComparing(ConjunctHeaderSpace::getSrcIp)
        .thenComparing(ConjunctHeaderSpace::getDstPort)
        .thenComparing(ConjunctHeaderSpace::getSrcPort)
        .thenComparing(ConjunctHeaderSpace::getProtocol)
        .compare(this, conjunctHeaderSpace);
  }

  @Override public ConjunctHeaderSpace getIncluded() {
    return this;
  }

  @Override public SortedSet<ConjunctHeaderSpace> getExcluded() {
    return new TreeSet<>();
  }
}

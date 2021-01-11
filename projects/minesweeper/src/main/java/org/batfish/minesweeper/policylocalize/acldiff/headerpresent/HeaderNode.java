package org.batfish.minesweeper.policylocalize.acldiff.headerpresent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.datamodel.IpProtocol;
import org.batfish.minesweeper.policylocalize.acldiff.representation.ConjunctHeaderSpace;

public class HeaderNode {
  @Nonnull private final List<HeaderNode> _parents;
  @Nonnull private final List<HeaderNode> _children;
  @Nonnull private final List<HeaderNode> _intersects;
  @Nonnull private final BDDPacket _record;
  @Nonnull private ConjunctHeaderSpace _space;
  @Nullable private BDD _bdd;
  @Nullable private HeaderRemainderNode _remainder;
  private boolean _isIntersection;

  public HeaderNode(@Nonnull ConjunctHeaderSpace space, @Nonnull BDDPacket packet, boolean isIntersection) {
    _parents = new ArrayList<>();
    _children = new ArrayList<>();
    _intersects = new ArrayList<>();
    _space = space;
    _isIntersection = isIntersection;
    _record = packet;
  }


  public void addParent(@Nonnull HeaderNode p) {
    _parents.add(p);
  }

  public void removeParent(@Nonnull HeaderNode p) {
    _parents.remove(p);
  }

  @Nonnull
  public List<HeaderNode> getParents() {
    return _parents;
  }

  @Nonnull
  public BDD getBDD() {
    if (_bdd != null) {
      return _bdd;
    }
    _bdd = _record.getFactory().one();
    BDD dstIp = _record.getDstIp().range(_space.getDstIp().getStartIp().asLong(), _space.getDstIp().getEndIp().asLong());
    BDD srcIp = _record.getSrcIp().range(_space.getSrcIp().getStartIp().asLong(), _space.getSrcIp().getEndIp().asLong());
    BDD dstPort = _record.getDstPort().range(_space.getDstPort().getStart(), _space.getDstPort().getEnd());
    BDD srcPort = _record.getSrcPort().range(_space.getSrcPort().getStart(), _space.getSrcPort().getEnd());
    BDD proto = _record.getIpProtocol().value(_space.getProtocol());
    _bdd.andWith(dstIp);
    _bdd.andWith(srcIp);
    _bdd.andWith(dstPort);
    _bdd.andWith(srcPort);
    if (!_space.getProtocol().equals(IpProtocol.ISO_IP)) {
      _bdd.andWith(proto);
    }
    return _bdd;
  }

  @Nonnull
  public boolean intersectsBDD(BDD bdd) {
    return getBDD().andSat(bdd);
  }

  private void generateRemainder() {
    HeaderRemainderNode remainderNode = new HeaderRemainderNode(this, _children, _record);
    _remainder = remainderNode;
  }

  @Nullable
  public HeaderRemainderNode getRemainder() {
    if (_remainder == null) {
      generateRemainder();
    }
    return _remainder;
  }

  public void addChild(@Nonnull HeaderNode ch) {
    _children.add(ch);
  }

  public void removeChild(@Nonnull HeaderNode ch) {
    _children.remove(ch);
  }

  @Nonnull
  public List<HeaderNode> getChildren() {
    return _children;
  }

  public void addIntersects(@Nonnull HeaderNode intersect) {
    _intersects.add(intersect);
  }

  @Nonnull
  public List<HeaderNode> getIntersects() {
    return _intersects;
  }

  @Nonnull
  public ConjunctHeaderSpace getHeaderSpace() {
    return _space;
  }

  public boolean equalHeaderSpace(ConjunctHeaderSpace space) {
    return _space.equals(space);
  }

  public boolean contains(ConjunctHeaderSpace other) {
    return _space.contains(other);
  }

  public Optional<ConjunctHeaderSpace> getIntersection(ConjunctHeaderSpace other) {
    return _space.intersection(other);
  }

  public boolean isIntersection() {
    return _isIntersection;
  }

  /*
Recursive method for determining which prefix ranges must be included and excluded to match
a BDD. For the subgraph reachable from the current node, this method returns a list of included
and excluded prefix ranges so that the union of included prefixes removing the excluded prefixes
covers all prefixes in the BDD which are also part of this nodes prefix range
 */
  public IncludedExcludedHeaderSpaces getRangesMatchingBDD(BDD bdd, BDDPacket record,
      ConjunctHeaderSpace hintSpace) {
    IncludedExcludedHeaderSpaces ranges = new IncludedExcludedHeaderSpaces();
    if (_children.isEmpty()) {
      if (intersectsBDD(bdd)) {
        ranges.getIncludedRanges().add(_space);
      }
      return ranges;
    }

    if (!getHeaderSpace().intersects(hintSpace)) {
      return ranges;
    }

    // !_children.isEmpty()
    BDD conjunct = getBDD().and(bdd);
    if (conjunct.isZero()) {
      return ranges;
    }

    assert getRemainder() != null;
    boolean remainderMatches = getRemainder().intersectsBDD(bdd);
    if (remainderMatches) {
      ranges.getIncludedRanges().add(getHeaderSpace());
    }
    for (HeaderNode child : getChildren()) {
      if (remainderMatches) {
        IncludedExcludedHeaderSpaces notMatchRanges = child.getRangesNotMatchingBDD(conjunct, record,
            hintSpace);
        ranges.addExcludedRanges(Collections.singleton(notMatchRanges));
      } else {
        IncludedExcludedHeaderSpaces childRanges = child.getRangesMatchingBDD(conjunct, record,
            hintSpace);
        ranges.addIncludedRanges(childRanges.getIncludedRanges());
        ranges.addExcludedRanges(childRanges.getFullExcludedRanges());
      }
    }
    return ranges;
  }

  /*
  Dual of getRangesMatchingBDD finds representation of prefixes that do not match BDD
   */
  public IncludedExcludedHeaderSpaces getRangesNotMatchingBDD(BDD bdd, BDDPacket record,
      ConjunctHeaderSpace hintSpace) {
    IncludedExcludedHeaderSpaces ranges = new IncludedExcludedHeaderSpaces();
    if (_children.isEmpty()) {
      if (!intersectsBDD(bdd)) {
        ranges.getIncludedRanges().add(_space);
      }
      return ranges;
    }

    if (!getHeaderSpace().intersects(hintSpace)) {
      return ranges;
    }

    // !_children.isEmpty()
    BDD conjunct = getBDD().and(bdd);
    if (conjunct.isZero()) {
      ranges.getIncludedRanges().add(_space);
      return ranges;
    }

    assert getRemainder() != null;
    boolean remainderMatches = getRemainder().getBDD().isZero() || getRemainder().intersectsBDD(bdd);
    if (!remainderMatches) {
      ranges.getIncludedRanges().add(getHeaderSpace());
    }
    for (HeaderNode child : getChildren()) {
      if (!remainderMatches) {
        IncludedExcludedHeaderSpaces matchRanges = child.getRangesMatchingBDD(conjunct, record,
            hintSpace);
        ranges.addExcludedRanges(Collections.singleton(matchRanges));
      } else {
        IncludedExcludedHeaderSpaces childRanges = child.getRangesNotMatchingBDD(conjunct, record,
            hintSpace);
        ranges.addIncludedRanges(childRanges.getIncludedRanges());
        ranges.addExcludedRanges(childRanges.getFullExcludedRanges());
      }
    }
    return ranges;
  }

}

package org.batfish.minesweeper.policylocalize.resultrepr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.SubRange;
import org.batfish.minesweeper.bdd.BDDRoute;
import org.batfish.minesweeper.bdd.TransferBDD;

/*
A node in a PrefixRangeDAG. Each node represents a set of prefixes. The parents represents supersets
of the node's set. The children nodes represent subsets of the node's set. The graph only contains
nodes representing sets of interest, that is, prefix ranges obtained from the configuration, and
sets formed by their complements.
 */
public class PrefixRangeNode extends AbstractPrefixRangeNode {

  @Nonnull private final List<PrefixRangeNode> _parents;
  @Nonnull private final List<PrefixRangeNode> _children;
  @Nonnull private final List<PrefixRangeNode> _intersects;
  @Nonnull private PrefixRange _prefixRange;
  @Nullable private RemainderNode _remainder;
  private final boolean _isIntersection;

  PrefixRangeNode(PrefixRange prefixRange, boolean isIntersection) {
    _parents = new ArrayList<>();
    _children = new ArrayList<>();
    _intersects = new ArrayList<>();
    _prefixRange = prefixRange;
    _isIntersection = isIntersection;
  }

  public static Optional<PrefixRange> prefixRangeIntersection(
      PrefixRange range1, PrefixRange range2) {
    Prefix myPrefix = range1.getPrefix();
    Prefix otherPrefix = range2.getPrefix();

    Prefix resultPrefix;
    if (myPrefix.containsPrefix(otherPrefix)) {
      resultPrefix = otherPrefix;
    } else if (otherPrefix.containsPrefix(myPrefix)) {
      resultPrefix = myPrefix;
    } else {
      return Optional.empty();
    }

    Optional<SubRange> subRange = range1.getLengthRange().intersection(range2.getLengthRange());
    if (!subRange.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(new PrefixRange(resultPrefix, subRange.get()));
  }

  public void addParent(@Nonnull PrefixRangeNode p) {
    _parents.add(p);
  }

  public void removeParent(@Nonnull PrefixRangeNode p) {
    _parents.remove(p);
  }

  @Override
  @Nonnull
  public List<PrefixRangeNode> getParents() {
    return _parents;
  }

  @Nonnull
  @Override
  public BDD getBDD(BDDRoute record) {
    return TransferBDD.isRelevantFor(record, _prefixRange);
  }

  @Override
  public boolean hasPrefixRange() {
    return true;
  }

  public void addChild(@Nonnull PrefixRangeNode ch) {
    _children.add(ch);
  }

  public void removeChild(@Nonnull PrefixRangeNode ch) {
    _children.remove(ch);
  }

  @Nonnull
  public List<PrefixRangeNode> getChildren() {
    return _children;
  }

  public void addIntersects(@Nonnull PrefixRangeNode intersect) {
    _intersects.add(intersect);
  }

  @Nonnull
  public List<PrefixRangeNode> getIntersects() {
    return _intersects;
  }

  @Nonnull
  public PrefixRange getPrefixRange() {
    return _prefixRange;
  }

  private void generateRemainder() {
    List<PrefixRange> excluded =
            getChildren().stream().map(PrefixRangeNode::getPrefixRange).collect(Collectors.toList());
    RemainderNode remainderNode = new RemainderNode(this, excluded);
    setRemainder(remainderNode);
  }

  @Nullable
  public RemainderNode getRemainder() {
    if (_remainder == null) {
      generateRemainder();
    }
    return _remainder;
  }

  public void setRemainder(@Nullable RemainderNode remainder) {
    _remainder = remainder;
  }

  public boolean equalPrefixRange(PrefixRange range) {
    return _prefixRange.equals(range);
  }

  public boolean containsPrefixRange(PrefixRange range) {
    return _prefixRange.includesPrefixRange(range);
  }

  public Optional<PrefixRange> getIntersection(PrefixRange range) {
    return PrefixRangeNode.prefixRangeIntersection(_prefixRange, range);
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
  public IncludedExcludedPrefixRanges getRangesMatchingBDD(BDD bdd, BDDRoute record) {
    IncludedExcludedPrefixRanges ranges = new IncludedExcludedPrefixRanges();
    if (_children.isEmpty()) {
      if (intersectsBDD(bdd, record)) {
        ranges.getIncludedRanges().add(_prefixRange);
      }
      return ranges;
    }

    // !_children.isEmpty()
    BDD conjunct = getBDD(record).and(bdd);
    if (conjunct.isZero()) {
      return ranges;
    }

    assert getRemainder() != null;
    boolean remainderMatches = getRemainder().intersectsBDD(bdd, record);
    if (remainderMatches) {
      ranges.getIncludedRanges().add(getPrefixRange());
    }
    for (PrefixRangeNode child : getChildren()) {
      if (remainderMatches) {
        IncludedExcludedPrefixRanges notMatchRanges = child.getRangesNotMatchingBDD(conjunct, record);
        ranges.addExcludedRanges(Collections.singleton(notMatchRanges));
      } else {
        IncludedExcludedPrefixRanges childRanges = child.getRangesMatchingBDD(conjunct, record);
        ranges.addIncludedRanges(childRanges.getIncludedRanges());
        ranges.addExcludedRanges(childRanges.getFullExcludedRanges());
      }
    }
    return ranges;
  }

  /*
  Dual of getRangesMatchingBDD finds representation of prefixes that do not match BDD
   */
  public IncludedExcludedPrefixRanges getRangesNotMatchingBDD(BDD bdd, BDDRoute record) {
    IncludedExcludedPrefixRanges ranges = new IncludedExcludedPrefixRanges();
    if (_children.isEmpty()) {
      if (!intersectsBDD(bdd, record)) {
        ranges.getIncludedRanges().add(_prefixRange);
      }
      return ranges;
    }

    // !_children.isEmpty()
    BDD conjunct = getBDD(record).and(bdd);
    if (conjunct.isZero()) {
      ranges.getIncludedRanges().add(_prefixRange);
      return ranges;
    }

    assert getRemainder() != null;
    boolean remainderMatches = getRemainder().getBDD(record).isZero() || getRemainder().intersectsBDD(bdd, record);
    if (!remainderMatches) {
      ranges.getIncludedRanges().add(getPrefixRange());
    }
    for (PrefixRangeNode child : getChildren()) {
      if (!remainderMatches) {
        IncludedExcludedPrefixRanges matchRanges = child.getRangesMatchingBDD(conjunct, record);
        ranges.addExcludedRanges(Collections.singleton(matchRanges));
      } else {
        IncludedExcludedPrefixRanges childRanges = child.getRangesNotMatchingBDD(conjunct, record);
        ranges.addIncludedRanges(childRanges.getIncludedRanges());
        ranges.addExcludedRanges(childRanges.getFullExcludedRanges());
      }
    }
    return ranges;
  }

}

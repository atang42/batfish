package org.batfish.minesweeper.policylocalize.acldiff.representation;

import java.util.Collection;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nonnull;

/*
Represents spaces of packets that can be represented as A1 + A2 + ... - B1 - B2 - ...
where all of the As are disjoint, and all Bs are contained in at least one A
 */
public class DifferenceHeaderSpace extends AbstractHeaderSpace {

  @Nonnull private final SortedSet<ConjunctHeaderSpace> _included;
  @Nonnull private final SortedSet<ConjunctHeaderSpace> _excluded;

  public DifferenceHeaderSpace(@Nonnull Collection<ConjunctHeaderSpace> included, Collection<ConjunctHeaderSpace> excluded) {

    // Ensure all As are disjoint
    this._included = new TreeSet<>();
    for (ConjunctHeaderSpace space : included) {
      boolean add = true;
      for (ConjunctHeaderSpace inc : _included) {
        if (inc.intersects(space)) {
          System.err.format("DifferenceHeaderSpace: Spaces overlap %s %s", inc, space);
          add = false;
        }
      }
      if (add) {
        _included.add(space);
      }
    }

    // Ensure all Bs are contained in at least one A
    this._excluded = new TreeSet<>();
    for (ConjunctHeaderSpace space : excluded) {
      for (ConjunctHeaderSpace inc : included) {
        Optional<ConjunctHeaderSpace> intersection = inc.intersection(space);
        intersection.ifPresent(_excluded::add);
      }
    }
  }

  @Override
  @Nonnull public SortedSet<ConjunctHeaderSpace> getIncluded() {
    return _included;
  }

  @Override
  @Nonnull public SortedSet<ConjunctHeaderSpace> getExcluded() {
    return _excluded;
  }


}

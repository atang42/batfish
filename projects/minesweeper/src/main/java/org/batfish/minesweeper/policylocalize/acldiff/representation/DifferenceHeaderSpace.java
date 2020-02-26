package org.batfish.minesweeper.policylocalize.acldiff.representation;

import java.util.Collection;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nonnull;

public class DifferenceHeaderSpace extends AbstractHeaderSpace {

  @Nonnull private final ConjunctHeaderSpace _included;
  @Nonnull private final SortedSet<ConjunctHeaderSpace> _excluded;

  public DifferenceHeaderSpace(@Nonnull ConjunctHeaderSpace included, Collection<ConjunctHeaderSpace> excluded) {
    this._included = included;
    this._excluded = new TreeSet<>();
    for (ConjunctHeaderSpace space : excluded) {
      Optional<ConjunctHeaderSpace> intersection = _included.intersection(space);
      if (intersection.isPresent()) {
        _excluded.add(space);
      }
    }
  }

  @Override
  @Nonnull public ConjunctHeaderSpace getIncluded() {
    return _included;
  }

  @Override
  @Nonnull public SortedSet<ConjunctHeaderSpace> getExcluded() {
    return _excluded;
  }


}

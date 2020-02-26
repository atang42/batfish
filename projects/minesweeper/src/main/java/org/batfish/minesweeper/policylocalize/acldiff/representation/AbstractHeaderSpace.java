package org.batfish.minesweeper.policylocalize.acldiff.representation;

import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/* Represents either a ConjunctHeaderSpace or a DifferenceHeaderSpace */
public abstract class AbstractHeaderSpace {

  public abstract ConjunctHeaderSpace getIncluded();

  public abstract SortedSet<ConjunctHeaderSpace> getExcluded();

  public Optional<AbstractHeaderSpace> intersection(AbstractHeaderSpace other) {
    Optional<ConjunctHeaderSpace> newIncluded =
        getIncluded().intersection(other.getIncluded());
    if (!newIncluded.isPresent()) {
      return Optional.empty();
    }
    TreeSet<ConjunctHeaderSpace> newExcluded = new TreeSet<>(getExcluded());
    newExcluded.addAll(other.getExcluded());

    DifferenceHeaderSpace result = new DifferenceHeaderSpace(newIncluded.get(), newExcluded);
    if (result.getExcluded().isEmpty()) {
      return Optional.of(result.getIncluded());
    }
    return Optional.of(result);
  }

}

package org.batfish.minesweeper.policylocalize.acldiff.representation;

import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/* Represents either a ConjunctHeaderSpace or a DifferenceHeaderSpace */
public abstract class AbstractHeaderSpace {

  public abstract SortedSet<ConjunctHeaderSpace> getIncluded();

  public abstract SortedSet<ConjunctHeaderSpace> getExcluded();

  public Optional<AbstractHeaderSpace> intersection(AbstractHeaderSpace other) {
    SortedSet<ConjunctHeaderSpace> newIncluded = new TreeSet<>();
    for (ConjunctHeaderSpace thisIncluded : getIncluded()) {
      for (ConjunctHeaderSpace otherIncluded : other.getIncluded()) {
        thisIncluded.intersection(otherIncluded).map(newIncluded::add);
      }
    }
    if (newIncluded.isEmpty()) {
      return Optional.empty();
    }
    TreeSet<ConjunctHeaderSpace> newExcluded = new TreeSet<>(getExcluded());
    newExcluded.addAll(other.getExcluded());

    DifferenceHeaderSpace result = new DifferenceHeaderSpace(newIncluded, newExcluded);
    if (result.getExcluded().isEmpty() && result.getIncluded().size() == 1) {
      return Optional.of(result.getIncluded().first());
    }
    return Optional.of(result);
  }

  public boolean intersects(AbstractHeaderSpace other) {
    return intersection(other).isPresent();
  }

  public boolean contains(AbstractHeaderSpace other) {
    // Each space included in other is contained by at least one included of this
    for (ConjunctHeaderSpace otherIncluded : other.getIncluded()) {
      boolean found = false;
      for (ConjunctHeaderSpace thisIncluded : this.getIncluded()) {
        if (thisIncluded.contains(otherIncluded)) {
          found = true;
        }
      }
      if (!found) {
        return false;
      }
    }

    // Other does not intersect any excluded set
    for (ConjunctHeaderSpace excluded : this.getExcluded()) {
      if (excluded.intersects(other)) {
        return false;
      }
    }
    return true;
  }

}

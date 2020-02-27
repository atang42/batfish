package org.batfish.minesweeper.policylocalize.acldiff.representation;

import java.util.SortedSet;
import java.util.TreeSet;

public class NoneHeaderSpace extends AbstractHeaderSpace{

  @Override public SortedSet<ConjunctHeaderSpace> getIncluded() {
    return new TreeSet<>();
  }

  @Override public SortedSet<ConjunctHeaderSpace> getExcluded() {
    return new TreeSet<>();
  }
}

package org.batfish.minesweeper.policylocalize.acldiff.headerpresent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.batfish.minesweeper.policylocalize.acldiff.representation.ConjunctHeaderSpace;

/*
Class for representing output: prefix ranges matching and excluded from BDD
 */
public class IncludedExcludedHeaderSpaces {
  @Nonnull private final List<ConjunctHeaderSpace> _includedRanges;
  @Nonnull private final List<IncludedExcludedHeaderSpaces> _excludedRanges;

  public IncludedExcludedHeaderSpaces() {
    _includedRanges = new ArrayList<>();
    _excludedRanges = new ArrayList<>();
  }

  public IncludedExcludedHeaderSpaces(
      @Nonnull List<ConjunctHeaderSpace> included, @Nonnull List<ConjunctHeaderSpace> excluded) {
    _includedRanges = new ArrayList<>(included);
    _excludedRanges = new ArrayList<>();
    if (!excluded.isEmpty()) {
      _excludedRanges.add(new IncludedExcludedHeaderSpaces(excluded, Collections.emptyList()));
    }
  }

  public void addIncludedRanges(@Nonnull Collection<ConjunctHeaderSpace> range) {
    _includedRanges.addAll(range);
  }

  public void addExcludedRanges(@Nonnull Collection<IncludedExcludedHeaderSpaces> range) {
    _excludedRanges.addAll(range);
  }

  @Nonnull
  public List<ConjunctHeaderSpace> getIncludedRanges() {
    return _includedRanges;
  }

  @Nonnull
  public List<IncludedExcludedHeaderSpaces> getFullExcludedRanges() {
    return _excludedRanges;
  }

  @Nonnull
  public List<ConjunctHeaderSpace> getExcludedRanges() {
    return _excludedRanges.stream()
        .map(IncludedExcludedHeaderSpaces::getIncludedRanges)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }


  public String getIncludedPrefixString() {
    StringBuilder builder = new StringBuilder();
    getIncludedRanges().forEach(ConjunctHeaderSpace -> builder.append(ConjunctHeaderSpace).append("\n"));
    return builder.toString().trim();
  }

  public String getExcludedPrefixString() {
    StringBuilder builder = new StringBuilder();
    getExcludedRanges().forEach(ConjunctHeaderSpace -> builder.append(ConjunctHeaderSpace).append("\n"));
    return builder.toString().trim();
  }

  /*
  Returns true if each element of _excludedRanges only has includes and no excludes
   */
  public boolean isFlattened() {
    if (_excludedRanges.isEmpty()) {
      return true;
    }
    for (IncludedExcludedHeaderSpaces range : _excludedRanges) {
      if (!range._excludedRanges.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public List<IncludedExcludedHeaderSpaces> getFlattenedRanges() {
    List<IncludedExcludedHeaderSpaces> result = new ArrayList<>();
    result.add(new IncludedExcludedHeaderSpaces(getIncludedRanges(), getExcludedRanges()));
    for (IncludedExcludedHeaderSpaces excluded : _excludedRanges) {
      for (IncludedExcludedHeaderSpaces included : excluded._excludedRanges) {
        result.addAll(included.getFlattenedRanges());
      }
    }
    return result;
  }

  @Override
  public String toString() {
    return "INCLUDED:\n"
        + getIncludedPrefixString()
        + "\n"
        + "EXCLUDED:\n"
        + getExcludedPrefixString();
  }
}

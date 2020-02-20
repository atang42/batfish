package org.batfish.minesweeper.policylocalize.resultrepr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.batfish.datamodel.PrefixRange;

/*
Class for representing output: prefix ranges matching and excluded from BDD
 */
public class IncludedExcludedPrefixRanges {
  @Nonnull private final List<PrefixRange> _includedRanges;
  @Nonnull private final List<IncludedExcludedPrefixRanges> _excludedRanges;

  public IncludedExcludedPrefixRanges() {
    _includedRanges = new ArrayList<>();
    _excludedRanges = new ArrayList<>();
  }

  public IncludedExcludedPrefixRanges(
      @Nonnull List<PrefixRange> included, @Nonnull List<PrefixRange> excluded) {
    _includedRanges = new ArrayList<>(included);
    _excludedRanges = new ArrayList<>();
    if (!excluded.isEmpty()) {
      _excludedRanges.add(new IncludedExcludedPrefixRanges(excluded, Collections.emptyList()));
    }
  }

  public void addIncludedRanges(@Nonnull Collection<PrefixRange> range) {
    _includedRanges.addAll(range);
  }

  public void addExcludedRanges(@Nonnull Collection<IncludedExcludedPrefixRanges> range) {
    _excludedRanges.addAll(range);
  }

  @Nonnull
  public List<PrefixRange> getIncludedRanges() {
    return _includedRanges;
  }

  @Nonnull
  public List<IncludedExcludedPrefixRanges> getFullExcludedRanges() {
    return _excludedRanges;
  }

  @Nonnull
  public List<PrefixRange> getExcludedRanges() {
    return _excludedRanges.stream()
        .map(IncludedExcludedPrefixRanges::getIncludedRanges)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }


  public String getIncludedPrefixString() {
    StringBuilder builder = new StringBuilder();
    getIncludedRanges().forEach(prefixRange -> builder.append(prefixRange).append("\n"));
    return builder.toString().trim();
  }

  public String getExcludedPrefixString() {
    StringBuilder builder = new StringBuilder();
    getExcludedRanges().forEach(prefixRange -> builder.append(prefixRange).append("\n"));
    return builder.toString().trim();
  }

  /*
  Returns true if each element of _excludedRanges only has includes and no excludes
   */
  public boolean isFlattened() {
    if (_excludedRanges.isEmpty()) {
      return true;
    }
    for (IncludedExcludedPrefixRanges range : _excludedRanges) {
      if (!range._excludedRanges.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public List<IncludedExcludedPrefixRanges> getFlattenedRanges() {
    List<IncludedExcludedPrefixRanges> result = new ArrayList<>();
    result.add(new IncludedExcludedPrefixRanges(getIncludedRanges(), getExcludedRanges()));
    for (IncludedExcludedPrefixRanges excluded : _excludedRanges) {
      for (IncludedExcludedPrefixRanges included : excluded._excludedRanges) {
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

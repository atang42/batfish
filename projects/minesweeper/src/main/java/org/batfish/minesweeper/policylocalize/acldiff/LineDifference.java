package org.batfish.minesweeper.policylocalize.acldiff;

import java.util.Comparator;
import java.util.SortedSet;
import javax.annotation.Nonnull;
import org.batfish.minesweeper.policylocalize.SymbolicResult;

public final class LineDifference implements Comparable<LineDifference> {

  private @Nonnull String _router1;
  private @Nonnull String _router2;
  private @Nonnull String _interface;
  private @Nonnull String _filter1;
  private @Nonnull String _filter2;
  private @Nonnull String _snippet1;
  private @Nonnull String _snippet2;
  private @Nonnull SortedSet<String> _difference;
  private @Nonnull SortedSet<String> _diffSub;
  private @Nonnull SymbolicResult _action1;
  private @Nonnull SymbolicResult _action2;

  public LineDifference(@Nonnull String router1,
      @Nonnull String router2,
      @Nonnull String filter1,
      @Nonnull String filter2,
      @Nonnull String snippet1,
      @Nonnull String snippet2,
      @Nonnull SortedSet<String> difference,
      @Nonnull SortedSet<String> diffSub,
      @Nonnull SymbolicResult action1,
      @Nonnull SymbolicResult action2) {
    if ((router1 + filter1).compareTo(router2 + filter2) <= 0) {
      this._router1 = router1;
      this._router2 = router2;
      this._filter1 = filter1;
      this._filter2 = filter2;
      this._snippet1 = snippet1;
      this._snippet2 = snippet2;
      this._action1 = action1;
      this._action2 = action2;
    } else {
      this._router1 = router2;
      this._router2 = router1;
      this._filter1 = filter2;
      this._filter2 = filter1;
      this._snippet1 = snippet2;
      this._snippet2 = snippet1;
      this._action1 = action2;
      this._action2 = action1;
    }
    this._difference = difference;
    this._diffSub = diffSub;
  }

  @Nonnull
  public String getRouter1() {
    return _router1;
  }

  public void setRouter1(@Nonnull String router1) {
    this._router1 = router1;
  }

  @Nonnull
  public String getRouter2() {
    return _router2;
  }

  public void setRouter2(@Nonnull String router2) {
    this._router2 = router2;
  }

  @Nonnull
  public String getFilter1() {
    return _filter1;
  }

  public void setFilter1(@Nonnull String filter1) {
    this._filter1 = filter1;
  }

  @Nonnull
  public String getFilter2() {
    return _filter2;
  }

  public void setFilter2(@Nonnull String filter2) {
    this._filter2 = filter2;
  }

  @Nonnull
  public String getSnippet1() {
    return _snippet1;
  }

  public void set_snippet1(@Nonnull String snippet1) {
    this._snippet1 = _snippet1;
  }

  @Nonnull
  public String getSnippet2() {
    return _snippet2;
  }

  public void setSnippet2(@Nonnull String snippet2) {
    this._snippet2 = snippet2;
  }

  public SortedSet<String> getDifference() {
    return _difference;
  }

  public void setDifference(@Nonnull SortedSet<String> difference) {
    this._difference = difference;
  }

  @Nonnull
  public SortedSet<String> getDiffSub() {
    return _diffSub;
  }

  public void setDiffSub(@Nonnull SortedSet<String> diffSub) {
    this._diffSub = diffSub;
  }

  @Nonnull public SymbolicResult getAction1() {
    return _action1;
  }

  public void setAction1(@Nonnull SymbolicResult action1) {
    this._action1 = action1;
  }

  @Nonnull public SymbolicResult getAction2() {
    return _action2;
  }

  public void setAction2(@Nonnull SymbolicResult action2) {
    this._action2 = action2;
  }

  private String concatString(SortedSet<String> ss1) {
    StringBuilder builder1 = new StringBuilder();
    for (String s : ss1) {
      builder1.append(s);
    }
    return builder1.toString();
  }

  @Override
  public int compareTo(LineDifference lineDifference) {
    return Comparator.comparing(LineDifference::getRouter1)
        .thenComparing(LineDifference::getRouter2)
        .thenComparing(LineDifference::getFilter1)
        .thenComparing(LineDifference::getFilter2)
        .thenComparing(LineDifference::getAction1)
        .thenComparing(LineDifference::getAction2)
        .thenComparing(LineDifference::getSnippet1)
        .thenComparing(LineDifference::getSnippet2)
        .thenComparing(ld -> concatString(ld.getDifference()))
        .thenComparing(ld -> concatString(ld.getDiffSub()))
        .compare(this, lineDifference);
  }

  @Nonnull public String getInterface() {
    return _interface;
  }

  public void setInterface(@Nonnull String _interface) {
    this._interface = _interface;
  }
}

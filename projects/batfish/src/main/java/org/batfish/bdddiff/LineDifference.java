package org.batfish.bdddiff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.SortedSet;
import javax.annotation.Nonnull;

public final class LineDifference implements Comparable<LineDifference> {

  private static final String PROP_ROUTER1 = "router1";
  private static final String PROP_ROUTER2 = "router2";
  private static final String PROP_FILTER1 = "filter1";
  private static final String PROP_FILTER2 = "filter2";
  private static final String PROP_SNIPPET1 = "snippet1";
  private static final String PROP_SNIPPET2 = "snippet2";
  private static final String PROP_DIFFERENCE = "difference";
  private static final String PROP_DIFF_SUB = "diff-sub";

  private @Nonnull String _router1;
  private @Nonnull String _router2;
  private @Nonnull String _filter1;
  private @Nonnull String _filter2;
  private @Nonnull String _snippet1;
  private @Nonnull String _snippet2;
  private @Nonnull SortedSet<String> _difference;
  private @Nonnull SortedSet<String> _diffSub;

  @JsonCreator
  public LineDifference(
      @Nonnull @JsonProperty(PROP_ROUTER1) String router1,
      @Nonnull @JsonProperty(PROP_ROUTER2) String router2,
      @Nonnull @JsonProperty(PROP_FILTER1) String filter1,
      @Nonnull @JsonProperty(PROP_FILTER2) String filter2,
      @Nonnull @JsonProperty(PROP_SNIPPET1) String snippet1,
      @Nonnull @JsonProperty(PROP_SNIPPET2) String snippet2,
      @Nonnull @JsonProperty(PROP_SNIPPET2) SortedSet<String> difference,
      @Nonnull @JsonProperty(PROP_SNIPPET2) SortedSet<String> diffSub
      ) {
    if ((router1+filter1).compareTo(router2+filter2) <= 0) {
      this._router1 = router1;
      this._router2 = router2;
      this._filter1 = filter1;
      this._filter2 = filter2;
      this._snippet1 = snippet1;
      this._snippet2 = snippet2;
    } else {
      this._router1 = router2;
      this._router2 = router1;
      this._filter1 = filter2;
      this._filter2 = filter1;
      this._snippet1 = snippet2;
      this._snippet2 = snippet1;
    }
    this._difference = difference;
    this._diffSub = diffSub;
  }

  @JsonProperty(PROP_ROUTER1)
  @Nonnull public String getRouter1() {
    return _router1;
  }

  @JsonProperty(PROP_ROUTER1)
  public void setRouter1(@Nonnull String router1) {
    this._router1 = router1;
  }

  @JsonProperty(PROP_ROUTER2)
  @Nonnull public String getRouter2() {
    return _router2;
  }

  @JsonProperty(PROP_ROUTER2)
  public void setRouter2(@Nonnull String router2) {
    this._router2 = router2;
  }

  @JsonProperty(PROP_FILTER1)
  @Nonnull public String getFilter1() {
    return _filter1;
  }

  @JsonProperty(PROP_FILTER1)
  public void setFilter1(@Nonnull String filter1) {
    this._filter1 = filter1;
  }

  @JsonProperty(PROP_FILTER2)
  @Nonnull public String getFilter2() {
    return _filter2;
  }

  @JsonProperty(PROP_FILTER2)
  public void setFilter2(@Nonnull String filter2) {
    this._filter2 = filter2;
  }

  @JsonProperty(PROP_SNIPPET1)
  @Nonnull public String getSnippet1() {
    return _snippet1;
  }

  @JsonProperty(PROP_SNIPPET1)
  public void set_snippet1(@Nonnull String snippet1) {
    this._snippet1 = _snippet1;
  }

  @JsonProperty(PROP_SNIPPET2)
  @Nonnull public String getSnippet2() {
    return _snippet2;
  }

  @JsonProperty(PROP_SNIPPET2)
  public void setSnippet2(@Nonnull String snippet2) {
    this._snippet2 = snippet2;
  }

  @JsonProperty(PROP_DIFFERENCE)
  public SortedSet<String> getDifference() {
    return _difference;
  }

  @JsonProperty(PROP_DIFFERENCE)
  public void setDifference(@Nonnull SortedSet<String> difference) {
    this._difference = difference;
  }

  @JsonProperty(PROP_DIFF_SUB)
  @Nonnull public SortedSet<String> getDiffSub() {
    return _diffSub;
  }

  @JsonProperty(PROP_DIFF_SUB)
  public void setDiffSub(@Nonnull SortedSet<String> diffSub) {
    this._diffSub = diffSub;
  }

  @Override
  public int compareTo(LineDifference lineDifference) {
    if (_router1.compareTo(lineDifference._router1) != 0) {
      return _router1.compareTo(lineDifference._router1);
    } else if (_router2.compareTo(lineDifference._router2) != 0) {
      return _router2.compareTo(lineDifference._router2);
    } else if (_filter1.compareTo(lineDifference._filter1) != 0) {
      return _filter1.compareTo(lineDifference._filter1);
    } else if (_filter2.compareTo(lineDifference._filter2) != 0) {
      return _filter2.compareTo(lineDifference._filter2);
    } else if (_snippet1.compareTo(lineDifference._snippet1) != 0) {
      return _snippet2.compareTo(lineDifference._snippet2);
    }
    return 0;
  }
}

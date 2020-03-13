package org.batfish.minesweeper.policylocalize;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.batfish.minesweeper.CommunityVar;

public class PolicyAction {
  private SymbolicResult _result;
  private boolean _setsCommunities;
  private boolean _setsMetric;
  private boolean _setsLocalPref;

  @Nonnull private List<CommunityVar> _addedCommunities;
  private long _metricValue;
  private long _localPrefValue;

  public PolicyAction() {
    _result = SymbolicResult.UNKNOWN;
    _addedCommunities = new ArrayList<>();
  }

  public PolicyAction(PolicyAction action) {
    _result = action._result;
    _setsCommunities = action._setsCommunities;
    _setsMetric = action._setsMetric;
    _setsLocalPref = action._setsLocalPref;

    _addedCommunities = new ArrayList<>(action._addedCommunities);
    _metricValue = action._metricValue;
    _localPrefValue = action._localPrefValue;
  }

  public SymbolicResult getResult() {
    return _result;
  }

  private void setZeroAction() {
    _setsCommunities = false;
    _setsLocalPref = false;
    _setsMetric = false;
    _addedCommunities = new ArrayList<>();
    _metricValue = 0;
    _localPrefValue = 0;
  }

  public void setResult(SymbolicResult result) {
    this._result = result;
    if (result == SymbolicResult.REJECT) {
      setZeroAction();
    }
  }

  public void setMetric(long metric) {
    if (_result != SymbolicResult.REJECT) {
      _metricValue = metric;
      _setsMetric = true;
    }
  }

  public boolean setsMetric() {
    return _setsMetric;
  }

  public long getMetricValue() {
    if (_setsMetric) {
      return _metricValue;
    }
    return -1;
  }

  public void setLocalPref(long localPref) {
    if (_result != SymbolicResult.REJECT) {
      _setsLocalPref = true;
      _localPrefValue = localPref;
    }
  }

  public boolean setsLocalPref() {
    return _setsLocalPref;
  }

  public long getLocalPrefValue() {
    return _localPrefValue;
  }

  public void setAddedCommunities(Collection<CommunityVar> communityVars) {
    if (_result != SymbolicResult.REJECT) {
      _setsCommunities = true;
      _addedCommunities = new ArrayList<>(communityVars);
    }
  }

  public boolean setsCommunities() {
    return _setsCommunities;
  }

  public List<CommunityVar> getCommunities() {
    return _addedCommunities;
  }

  @Override public String toString() {
    StringBuilder builder = new StringBuilder();
    if (setsMetric()) {
      builder.append("SET METRIC ").append(_metricValue).append("\n");
    }
    if (setsLocalPref()) {
      builder.append("SET LOCAL PREF ").append(_localPrefValue).append("\n");
    }
    if (setsCommunities()) {
      builder.append("ADD COMMUNITIES\n");
      //TODO: Add community values to string
    }
    builder.append(_result.name());
    return builder.toString();
  }

  @Override public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PolicyAction action = (PolicyAction) o;
    return _setsCommunities == action._setsCommunities && _setsMetric == action._setsMetric
        && _setsLocalPref == action._setsLocalPref && _metricValue == action._metricValue
        && _localPrefValue == action._localPrefValue && _result == action._result && Objects.equals(
        _addedCommunities,
        action._addedCommunities);
  }

  @Override public int hashCode() {

    return Objects.hash(_result, _setsCommunities,
        _setsMetric,
        _setsLocalPref,
        _addedCommunities,
        _metricValue,
        _localPrefValue);
  }
}

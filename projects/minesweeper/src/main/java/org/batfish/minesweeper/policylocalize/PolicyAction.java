package org.batfish.minesweeper.policylocalize;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.routing_policy.expr.DiscardNextHop;
import org.batfish.datamodel.routing_policy.expr.IpNextHop;
import org.batfish.datamodel.routing_policy.expr.NextHopExpr;
import org.batfish.datamodel.routing_policy.expr.SelfNextHop;
import org.batfish.minesweeper.CommunityVar;

public class PolicyAction {
  private SymbolicResult _result;
  private boolean _setsCommunities;
  private boolean _setsMetric;
  private boolean _incrementsMetric;
  private boolean _setsLocalPref;
  private boolean _setsAsPath;

  @Nonnull private List<CommunityVar> _addedCommunities;
  private long _metricValue;
  private long _localPrefValue;
  private NextHopEnum _nextHop;
  private Ip _nextHopIp;

  public enum NextHopEnum {
    NONE,
    SELF,
    DISCARD,
    IP,
    OTHER
  }

  public PolicyAction() {
    _result = SymbolicResult.UNKNOWN;
    _addedCommunities = new ArrayList<>();
    _nextHop = NextHopEnum.NONE;
    _nextHopIp = null;
  }

  public PolicyAction(PolicyAction action) {
    _result = action._result;
    _setsCommunities = action._setsCommunities;
    _setsMetric = action._setsMetric;
    _setsLocalPref = action._setsLocalPref;

    _addedCommunities = new ArrayList<>(action._addedCommunities);
    _metricValue = action._metricValue;
    _localPrefValue = action._localPrefValue;
    _nextHop = action._nextHop;
    _nextHopIp = action._nextHopIp;
  }

  public SymbolicResult getResult() {
    return _result;
  }

  private void setZeroAction() {
    _setsCommunities = false;
    _setsLocalPref = false;
    _setsMetric = false;
    _incrementsMetric = false;
    _addedCommunities = new ArrayList<>();
    _metricValue = 0;
    _localPrefValue = 0;
    _nextHopIp = null;
    _nextHop = NextHopEnum.NONE;
  }

  public void setResult(SymbolicResult result) {
    this._result = result;
    if (result == SymbolicResult.REJECT) {
      setZeroAction();
    }
  }

  public void incMetric(long metric) {
    if (_result != SymbolicResult.REJECT) {
      _metricValue = metric;
      _setsMetric = false;
      _incrementsMetric = true;
    }
  }

  public void setMetric(long metric) {
    if (_result != SymbolicResult.REJECT) {
      _metricValue = metric;
      _setsMetric = true;
      _incrementsMetric = false;
    }
  }

  public boolean setsMetric() {
    return _setsMetric;
  }

  public boolean incrementsMetric() {
    return _incrementsMetric;
  }

  public long getMetricValue() {
    return _metricValue;
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

  public void setNextHop(NextHopExpr expr) {
    if (expr instanceof DiscardNextHop) {
      _nextHop = NextHopEnum.DISCARD;
      _nextHopIp = null;
    } else if (expr instanceof SelfNextHop) {
      _nextHop = NextHopEnum.SELF;
      _nextHopIp = null;
    } else if (expr instanceof IpNextHop) {
      _nextHop = NextHopEnum.IP;
      if (((IpNextHop) expr).getIps().size() == 1) {
        _nextHopIp = ((IpNextHop) expr).getIps().get(0);
      } else {
        _nextHopIp = null;
      }
    } else {
      _nextHop = NextHopEnum.OTHER;
    }
  }

  public NextHopEnum getNextHop() {
    return _nextHop;
  }

  public Optional<Ip> getNextHopIp() {
    return Optional.ofNullable(_nextHopIp);
  }

  @Override public String toString() {
    StringBuilder builder = new StringBuilder();
    if (setsMetric()) {
      builder.append("SET METRIC ").append(_metricValue).append("\n");
    } else if (incrementsMetric()) {
      if (_metricValue >= 0) {
        builder.append("INC METRIC ").append(_metricValue).append("\n");
      } else {
        builder.append("DEC METRIC ").append(-_metricValue).append("\n");
      }
    }
    if (setsLocalPref()) {
      builder.append("SET LOCAL PREF ").append(_localPrefValue).append("\n");
    }
    if (setsCommunities()) {
      builder.append("ADD COMMUNITIES [");
      for (CommunityVar var : getCommunities()) {
        builder.append(var.getRegex()).append(" ");
        builder.append("]\n");
      }
    }
    if (_nextHop != NextHopEnum.NONE) {
      builder.append("SET NEXT HOP ").append(_nextHop.name());
      if (_nextHopIp != null) {
        builder.append(" ").append(_nextHopIp.toString());
      }
      builder.append("\n");
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

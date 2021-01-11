package org.batfish.minesweeper.question.OspfDiff;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.Topology;
import org.batfish.datamodel.collections.NodeInterfacePair;
import org.batfish.datamodel.ospf.OspfNeighborConfig;
import org.batfish.datamodel.ospf.OspfNeighborConfigId;
import org.batfish.datamodel.ospf.OspfNetworkType;
import org.batfish.datamodel.ospf.OspfProcess;
import org.batfish.datamodel.ospf.StubType;

public class OspfInterfaceFeatures {

  private final String _neighbor;
  private final OspfProcess _ospfProcess;
  private final Configuration _configuration;
  private final Interface _interface;

  private final long _area;
  private final StubType _areaType;
  private final boolean _isPassive;
  private final int _cost;
  private final OspfNetworkType _networkType;

  public OspfInterfaceFeatures(Configuration configuration, OspfProcess ospfProcess,
      OspfNeighborConfigId id, String neighbor) {
    _neighbor = neighbor;
    _configuration = configuration;
    _ospfProcess = ospfProcess;
    _interface = configuration.getAllInterfaces().get(id.getInterfaceName());

    OspfNeighborConfig neighborConfig = ospfProcess.getOspfNeighborConfigs().get(id);
    _area = neighborConfig.getArea();
    _areaType = ospfProcess.getAreas().get(_area).getStubType();
    _isPassive = neighborConfig.isPassive();

    assert _interface.getOspfEnabled();
    assert _interface.getOspfPassive() == _isPassive;
    assert _interface.getOspfAreaName() != null && _interface.getOspfAreaName() == _area;
    if (_interface.getOspfCost() == null || !_interface.getActive()) {
      System.out.println(_interface.getName());
    }

    _cost = Optional.ofNullable(_interface.getOspfCost()).orElse(-1);
    _networkType = _interface.getOspfNetworkType();
  }

  public OspfProcess getOspfProcess() {
    return _ospfProcess;
  }

  public String getNode() {
    return _configuration.getHostname();
  }

  public Configuration getConfiguration() {
    return _configuration;
  }

  public Interface getInterface() {
    return _interface;
  }

  public long getArea() {
    return _area;
  }

  public StubType getAreaType() {
    return _areaType;
  }

  public String getIp() {
    if (_interface.getConcreteAddress() != null) {
      return _interface.getConcreteAddress().toString();
    }
    return "Null";
  }

  public boolean isPassive() {
    return _isPassive;
  }

  public int getCost() {
    return _cost;
  }

  public OspfNetworkType getNetworkType() {
    return _networkType;
  }

  public boolean equalAttributes(OspfInterfaceFeatures other) {
    if (other == null) {
      return false;
    }
    boolean areaEqual = (this._area == other._area);
    boolean areaTypeEqual = (this._areaType == other._areaType);
    boolean costEqual = (this._cost == other._cost);
    boolean passiveEqual = (this._isPassive == other._isPassive);
    boolean netTypeEqual = (this._networkType == other._networkType);

    return areaEqual && areaTypeEqual && costEqual && passiveEqual && netTypeEqual;
  }

  public String getNeighbor() {
    return _neighbor;
  }
}

package org.batfish.minesweeper.question.BgpEdgeDiff;

import java.util.Objects;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.BgpTieBreaker;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.bgp.AddressFamilyCapabilities;

public class BgpEdgeFeatures {

  private static final String[] CAPABILITY = new String[] {
      "additionalPathsReceive",
      "additionalPathsSelectAll",
      "additionalPathsSend",
      "advertiseExternal",
      "advertiseInactive",
      "allowLocalAsIn",
      "allowRemoteAsOut",
      "sendCommunity",
      "sendExtendedCommunity"
  };

  private final BgpProcess _bgpProcess;
  private final BgpActivePeerConfig _peerConfig;

  private final Ip _ip;
  private final Long _localAs;
  private final BgpTieBreaker _tieBreaker;
  private final AddressFamilyCapabilities _capabilities;
  private final boolean _routeReflectorClient;

  public BgpEdgeFeatures(BgpProcess bgpProcess, BgpActivePeerConfig peerConfig) {
    _bgpProcess = bgpProcess;
    _peerConfig = peerConfig;

    _ip = peerConfig.getPeerAddress();
    _localAs = peerConfig.getLocalAs();
    _tieBreaker = _bgpProcess.getTieBreaker();
    if (_peerConfig.getIpv4UnicastAddressFamily() != null) {
      _capabilities = _peerConfig.getIpv4UnicastAddressFamily().getAddressFamilyCapabilities();
      _routeReflectorClient = _peerConfig.getIpv4UnicastAddressFamily().getRouteReflectorClient();
    } else {
      _capabilities = null;
      _routeReflectorClient = _peerConfig.getIpv4UnicastAddressFamily().getRouteReflectorClient();
    }

  }

  public BgpProcess getBgpProcess() {
    return _bgpProcess;
  }

  public BgpActivePeerConfig getPeerConfig() {
    return _peerConfig;
  }

  public Ip getIp() {
    return _ip;
  }

  public Long getLocalAs() {
    return _localAs;
  }

  public BgpTieBreaker getTieBreaker() {
    return _tieBreaker;
  }

  public AddressFamilyCapabilities getCapabilities() {
    return _capabilities;
  }

  public String getCapabilitiesString() {
    boolean[] capabilities = new boolean[] {
        _capabilities.getAdditionalPathsReceive(),
        _capabilities.getAdditionalPathsSelectAll(),
        _capabilities.getAdditionalPathsSend(),
        _capabilities.getAdvertiseExternal(),
        _capabilities.getAdvertiseInactive(),
        _capabilities.getAllowLocalAsIn(),
        _capabilities.getAllowRemoteAsOut(),
        _capabilities.getSendCommunity(),
        _capabilities.getSendExtendedCommunity()
    };
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < CAPABILITY.length; i++) {
      if (capabilities[i]) {
        builder.append(CAPABILITY[i]).append(", ");
      }
    }
    return builder.toString().trim();
  }

  public boolean isRouteReflectorClient() {
    return _routeReflectorClient;
  }

  public boolean equalAttributes(
      BgpEdgeFeatures other) {
    if (other == null) {
      return false;
    }
    boolean localAsEqual = (Objects.equals(this._localAs, other._localAs));
    boolean tieBreakerEqual = (this._tieBreaker == other._tieBreaker);
    boolean rrcEqual = (this._routeReflectorClient == other._routeReflectorClient);
    boolean capabilitiesEqual = (this._capabilities.equals(other._capabilities));

    return localAsEqual && tieBreakerEqual && rrcEqual && capabilitiesEqual;
  }
}

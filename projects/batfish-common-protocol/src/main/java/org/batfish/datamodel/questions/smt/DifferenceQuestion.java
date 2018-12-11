package org.batfish.datamodel.questions.smt;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.batfish.datamodel.SubRange;

public class DifferenceQuestion extends HeaderQuestion {

  private static final String NODE_REGEX_VAR = "nodeRegex";
  private static final String NODE_SRC_PREFIX_VAR = "srcPrefix";
  private static final String NODE_DST_PREFIX_VAR = "dstPrefix";
  private static final String NODE_SRC_PORT_RANGE_VAR = "srcPortRange";
  private static final String NODE_DST_PORT_RANGE_VAR = "dstPortRange";
  private static final String NODE_MAX_LENGTH_VAR = "maxLength";
  private static final String IGNORE_INTERFACES_VAR = "ignoreInterfaces";

  private String _routerRegex;
  private String _srcPrefix;
  private String _dstPrefix;

  private SubRange _srcPortRange;
  private SubRange _dstPortRange;
  private int _maxLength;
  private String _ignoreInterfaces;

  public DifferenceQuestion() {
    _routerRegex = ".*";
    _srcPrefix = "0.0.0.0/0";
    _dstPrefix = "0.0.0.0/0";
    _srcPortRange = new SubRange(0, 65535);
    _dstPortRange = new SubRange(0, 65535);
    _maxLength = 32;
    _ignoreInterfaces = "exact";
  }

  @JsonProperty(NODE_REGEX_VAR)
  public String getRouterRegex() {
    return _routerRegex;
  }

  @JsonProperty(NODE_REGEX_VAR)
  public void setRouterRegex(String routerRegex) {
    this._routerRegex = routerRegex;
  }

  @JsonProperty(NODE_SRC_PREFIX_VAR)
  public String getSrcPrefix() {
    return _srcPrefix;
  }

  @JsonProperty(NODE_SRC_PREFIX_VAR)
  public void setSrcPrefix(String prefix) {
    this._srcPrefix = prefix;
  }

  @JsonProperty(NODE_DST_PREFIX_VAR)
  public String getDstPrefix() {
    return _dstPrefix;
  }

  @JsonProperty(NODE_DST_PREFIX_VAR)
  public void setDstPrefix(String prefix) {
    this._dstPrefix = prefix;
  }

  @JsonIgnore
  public SubRange getSrcPortRange() {
    return _srcPortRange;
  }

  @JsonProperty(NODE_SRC_PORT_RANGE_VAR)
  public void setSrcPortRange(SubRange srcPorts) {
    this._srcPortRange = srcPorts;
  }

  @JsonProperty(NODE_DST_PORT_RANGE_VAR)
  public void setDstPortRange(SubRange dstPorts) {
    this._dstPortRange = dstPorts;
  }

  @JsonIgnore
  public SubRange getDstPortRange() {
    return _dstPortRange;
  }

  @JsonProperty(NODE_MAX_LENGTH_VAR)
  public int getMaxLength() {
    return _maxLength;
  }

  @JsonProperty(NODE_MAX_LENGTH_VAR)
  public void setMaxLength(int maxLength) {
    _maxLength = maxLength;
  }

  @JsonProperty(IGNORE_INTERFACES_VAR)
  public String getIgnoreInterfaces() {
    return _ignoreInterfaces;
  }

  @JsonProperty(IGNORE_INTERFACES_VAR)
  public void setIgnoreInterfaces(String ignoreInterfaces) {
    _ignoreInterfaces = ignoreInterfaces;
  }

  @Override
  public boolean getDataPlane() {
    return false;
  }


}

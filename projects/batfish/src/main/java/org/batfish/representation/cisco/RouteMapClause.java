package org.batfish.representation.cisco;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.batfish.datamodel.LineAction;

public class RouteMapClause implements Serializable {

  private LineAction _action;

  private RouteMapContinue _continueLine;

  private boolean _ignore;

  private String _mapName;

  private List<RouteMapMatchLine> _matchList;

  private int _seqNum;

  private List<RouteMapSetLine> _setList;

  private String _text;

  private SortedSet<Integer> _lineNums;

  public RouteMapClause(LineAction action, String name, int num, String text, Set<Integer> lineNums) {
    _action = action;
    _mapName = name;
    _seqNum = num;
    _matchList = new ArrayList<>();
    _setList = new ArrayList<>();
    _text = text;
    _lineNums = new TreeSet<>(lineNums);
  }

  public void addMatchLine(RouteMapMatchLine line) {
    _matchList.add(line);
  }

  public void addSetLine(RouteMapSetLine line) {
    _setList.add(line);
  }

  public LineAction getAction() {
    return _action;
  }

  public RouteMapContinue getContinueLine() {
    return _continueLine;
  }

  public boolean getIgnore() {
    return _ignore;
  }

  public String getMapName() {
    return _mapName;
  }

  public List<RouteMapMatchLine> getMatchList() {
    return _matchList;
  }

  public int getSeqNum() {
    return _seqNum;
  }

  public List<RouteMapSetLine> getSetList() {
    return _setList;
  }

  public void setContinueLine(RouteMapContinue continueLine) {
    _continueLine = continueLine;
  }

  public void setIgnore(boolean b) {
    _ignore = b;
  }

  public String getText() {
    return _text;
  }

  public void setText(String text) {
    this._text = text;
  }

  public SortedSet<Integer> getLineNumbers() {
    return _lineNums;
  }
}

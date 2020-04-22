package org.batfish.minesweeper.policylocalize.acldiff.representation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.AclLine;
import org.batfish.minesweeper.policylocalize.acldiff.AclLineDiffToPrefix;
import org.batfish.minesweeper.policylocalize.acldiff.AclToConfigLines;

public class AclSpacesMap {
  @Nonnull private IpAccessList _acl;
  @Nonnull private Map<AclLine, List<ConjunctHeaderSpace>> _linesToSpaces;

  public AclSpacesMap(@Nonnull IpAccessList acl) {
    _acl = acl;
    _linesToSpaces = new HashMap<>();
    for (AclLine line : _acl.getLines()) {
      List<ConjunctHeaderSpace> spaces = AclToDescribedHeaderSpaces.createPrefixSpaces(line);
      _linesToSpaces.put(line, spaces);
    }
  }

  @Nonnull
  public IpAccessList getAcl() {
    return _acl;
  }

  @Nonnull
  public List<ConjunctHeaderSpace> getSpace(@Nonnull AclLine line) {
    return _linesToSpaces.getOrDefault(line, new ArrayList<>());
  }

  @Nonnull
  public Set<AclLine> getRelevantLinesUptoLine(
      @Nonnull Collection<ConjunctHeaderSpace> spaces, @Nullable AclLine limit) {
    Set<AclLine> result = new HashSet<>();
    for (AclLine line : _linesToSpaces.keySet()) {
      if (Objects.equals(line, limit)) {
        return result;
      }
      for (ConjunctHeaderSpace lineSpace : _linesToSpaces.get(line)) {
        for (ConjunctHeaderSpace space : spaces) {
          if (lineSpace.intersects(space)) {
            result.add(line);
            break;
          }
        }
      }
    }
    return result;
  }

  @Nonnull
  public Set<AclLine> getRelevantLines(@Nonnull Collection<ConjunctHeaderSpace> spaces) {
    return getRelevantLinesUptoLine(spaces, null);
  }

  @Nonnull
  public DifferenceHeaderSpace getDifferenceSpace(@Nullable AclLine line) {
    List<ConjunctHeaderSpace> fullSpace = null;
    SortedSet<ConjunctHeaderSpace> spacesSoFar = new TreeSet<>();
    for (AclLine prev : _acl.getLines()) {
      if (Objects.equals(prev, line)) {
        fullSpace = getSpace(prev);
        break;
      }
      spacesSoFar.addAll(_linesToSpaces.get(prev));
    }
    if (fullSpace == null) {
      fullSpace = AclToDescribedHeaderSpaces.getAllPackets();
    }
    return new DifferenceHeaderSpace(fullSpace, spacesSoFar);
  }
}

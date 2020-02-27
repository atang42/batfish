package org.batfish.minesweeper.policylocalize.acldiff;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.minesweeper.policylocalize.acldiff.representation.AbstractHeaderSpace;
import org.batfish.minesweeper.policylocalize.acldiff.representation.AclSpacesMap;
import org.batfish.minesweeper.policylocalize.acldiff.representation.ConjunctHeaderSpace;
import org.batfish.minesweeper.policylocalize.acldiff.representation.NoneHeaderSpace;

public class AclDiffToPrefix {

  @Nonnull private final String _router1;
  @Nonnull private final String _router2;
  @Nonnull private final IpAccessList _acl1;
  @Nonnull private final IpAccessList _acl2;
  @Nonnull private final AclSpacesMap _map1;
  @Nonnull private final AclSpacesMap _map2;

  public AclDiffToPrefix(@Nonnull String router1, @Nonnull String router2, @Nonnull IpAccessList acl1,
      @Nonnull IpAccessList acl2) {
    _router1 = router1;
    _router2 = router2;
    _acl1 = acl1;
    _acl2 = acl2;
    _map1 = new AclSpacesMap(_acl1);
    _map2 = new AclSpacesMap(_acl2);
  }

  @Nonnull
  public AbstractHeaderSpace computeDifference(@Nullable IpAccessListLine line1, @Nullable IpAccessListLine line2) {

    Optional<AbstractHeaderSpace> diffSpace = _map1.getDifferenceSpace(line1)
        .intersection(_map2.getDifferenceSpace(line2));
    if (diffSpace.isPresent()) {
      return diffSpace.get();
    }
    System.err.format("Cannot compute difference:\n%s\n%s", line1, line2);
    return new NoneHeaderSpace();
  }

  @Nonnull
  public AclSpacesMap getAclSpaceMap1() {
    return _map1;
  }

  @Nonnull
  public AclSpacesMap getAclSpaceMap2() {
    return _map2;
  }

  @Nonnull
  public Set<IpAccessListLine> getAcl1ReleventLines(
      @Nonnull Collection<ConjunctHeaderSpace> spaces, @Nullable IpAccessListLine upto) {
    return _map1.getRelevantLinesUptoLine(spaces, upto);
  }

  @Nonnull
  public Set<IpAccessListLine> getAcl2ReleventLines(
      @Nonnull Collection<ConjunctHeaderSpace> spaces, @Nullable IpAccessListLine upto) {
    return _map2.getRelevantLinesUptoLine(spaces, upto);
  }

  @Nonnull
  AclDiffReport getReport(@Nullable IpAccessListLine line1, @Nullable IpAccessListLine line2) {
    AbstractHeaderSpace diffSpace = computeDifference(line1, line2);
    return new AclDiffReport(
        diffSpace.getIncluded(),
        diffSpace.getExcluded(),
        _router1,
        _acl1,
        Collections.singletonList(line1),
        getAcl1ReleventLines(diffSpace.getIncluded(), line1),
        _router2,
        _acl2,
        Collections.singletonList(line2),
        getAcl2ReleventLines(diffSpace.getIncluded(), line2));
  }
}

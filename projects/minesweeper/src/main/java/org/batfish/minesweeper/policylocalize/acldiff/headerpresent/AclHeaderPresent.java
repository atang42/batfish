package org.batfish.minesweeper.policylocalize.acldiff.headerpresent;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import net.sf.javabdd.BDD;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.datamodel.AclLine;
import org.batfish.datamodel.IpAccessList;
import org.batfish.minesweeper.policylocalize.acldiff.representation.AclToDescribedHeaderSpaces;
import org.batfish.minesweeper.policylocalize.acldiff.representation.ConjunctHeaderSpace;
import org.batfish.minesweeper.policylocalize.resultrepr.IncludedExcludedPrefixRanges;
import org.batfish.minesweeper.policylocalize.resultrepr.PrefixRangeDAG;

public class AclHeaderPresent {
  private @Nonnull Set<ConjunctHeaderSpace> _headerSpaces;
  private HeaderDAG _graph;
  boolean _changesOccurred;
  private BDDPacket _packet;

  public AclHeaderPresent(Collection<IpAccessList> aclList, BDDPacket packet) {
    _headerSpaces = new HashSet<>();
    for (IpAccessList acl : aclList) {
      for (AclLine line : acl.getLines()) {
        _headerSpaces.addAll(AclToDescribedHeaderSpaces.createPrefixSpaces(line));
      }
    }
    _packet = packet;
  }

  public List<IncludedExcludedHeaderSpaces> getIncludedExcludedHeaderSpaces(BDD bdd, ConjunctHeaderSpace hintSpace) {
    if (_graph == null || _changesOccurred) {
      _graph = HeaderDAG.build(_headerSpaces, _packet);
      _changesOccurred = false;
    }
    return _graph.getRangesMatchingBDD(bdd, _packet);
  }

}

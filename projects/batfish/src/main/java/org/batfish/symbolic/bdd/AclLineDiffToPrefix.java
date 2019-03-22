package org.batfish.symbolic.bdd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;

public class AclLineDiffToPrefix {

  IpAccessList _acl1;
  IpAccessList _acl2;
  IpAccessListLine _line1;
  IpAccessListLine _line2;

  Map<PacketPrefixRegion, List<PacketPrefixRegion>> _differences;

  public AclLineDiffToPrefix(IpAccessList acl1, IpAccessList acl2,
      IpAccessListLine line1, IpAccessListLine line2) {
    _acl1 = acl1;
    _acl2 = acl2;
    _line1 = line1;
    _line2 = line2;
    _differences = new HashMap<>();
  }

  /**
   * Given two ACL lines in corresponding ACLs, prints out the intersection of the lines minus the
   * prefixes in the previous lines
   */
  public void getDifferenceInPrefixes() {
    List<PacketPrefixRegion> spaces1 = PacketPrefixRegion.createPrefixSpace(_line1);
    List<PacketPrefixRegion> spaces2 = PacketPrefixRegion.createPrefixSpace(_line2);

    System.out.println("DIFFERENCES");
    List<PacketPrefixRegion> resultSpaces = new ArrayList<>();

    for (PacketPrefixRegion ps1 : spaces1) {
      for (PacketPrefixRegion ps2 : spaces2) {
        Optional<PacketPrefixRegion> optional = ps1.intersection(ps2);
        if (optional.isPresent()) {
          resultSpaces.add(optional.get());
        }
      }
    }
    for (PacketPrefixRegion resultSpace : resultSpaces) {
      boolean doPrint = true;
      List<PacketPrefixRegion> diffs = new ArrayList<>();
      List<IpAccessListLine> aclLines = new ArrayList<>(_acl1.getLines());
      aclLines.addAll(_acl2.getLines());
      for (IpAccessListLine line : aclLines) {
        if (line.equals(_line1)) {
          break;
        }
        List<PacketPrefixRegion> lineSpaces = PacketPrefixRegion.createPrefixSpace(line);
        for (PacketPrefixRegion lineSpace : lineSpaces) {
          if (lineSpace.contains(resultSpace)) {
            doPrint = false;
            break;
          }
          Optional<PacketPrefixRegion> optional = lineSpace.intersection(resultSpace);
          if (optional.isPresent()) {
            PacketPrefixRegion intersection = optional.get();
            boolean skip = false;
            for (int i = 0; i < diffs.size(); i++) {
              if (intersection.contains(diffs.get(i))) {
                diffs.set(i, intersection);
                break;
              } else if (diffs.get(i).contains(intersection)) {
                skip = true;
                break;
              }
            }
            if (!skip) {
              diffs.add(intersection);
            }
          }
        }
      }

      _differences.put(resultSpace, new ArrayList<>());
      for (PacketPrefixRegion sp : diffs) {
        _differences.get(resultSpace).add(sp);
      }

      if (doPrint) {
        System.out.println(resultSpace);
        for (PacketPrefixRegion sp : diffs) {
          System.out.println("\t- " + sp);
        }
      }
    }
  }

  public Map<PacketPrefixRegion, List<PacketPrefixRegion>> getDifferences() {
    return _differences;
  }


}

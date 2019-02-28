package org.batfish.common.bdd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;

public class BDDPacketWithLines extends BDDPacket{

  private BDD _accept;
  private Map<String, Map<AclLineRepr, BDD>> _aclLineVars;

  private class AclLineRepr {
    IpAccessList acl;
    IpAccessListLine line;

    AclLineRepr(IpAccessList acl, IpAccessListLine line) {
      this.acl = acl;
      this.line = line;
    }

    @Override public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      AclLineRepr that = (AclLineRepr) o;
      return acl.equals(that.acl) && line.equals(that.line);
    }

    @Override public int hashCode() {
      return Objects.hash(acl, line);
    }
  }

  public BDDPacketWithLines() {
    super();

    _accept = allocateBDDBit("accept");
    _aclLineVars = new HashMap<>();
  }


  public BDD getAccept() {
    return _accept;
  }

  public void setAccept(BDD accept) {
    this._accept = accept;
  }

  public void addAcl(String router, IpAccessList acl) {
    _aclLineVars.put(router, new HashMap<>());
    for (IpAccessListLine line : acl.getLines()) {
      BDD var = allocateBDDBit(line.getName());
      AclLineRepr repr = new AclLineRepr(acl, line);
      _aclLineVars.get(router).put(repr, var);
    }
  }

  public BDD getAclLine(String router, IpAccessList acl, IpAccessListLine line) {
    AclLineRepr repr = new AclLineRepr(acl, line);
    if (!_aclLineVars.containsKey(router) || !_aclLineVars.get(router).containsKey(repr)) {
      System.err.println("Cannot get variable for:");
      System.err.println(router);
      System.err.println(acl.getName());
      System.err.println(line.getName());
      System.err.println();
      return null;
    }
    BDD result = _aclLineVars.get(router).get(repr);
    return result;
  }

  public List<BDD> getAclLines(String router, IpAccessList acl) {
    List<BDD> bddList = new ArrayList<>();
    for(Entry<AclLineRepr, BDD> bdds : _aclLineVars.get(router).entrySet()) {
      if (bdds.getKey().acl.equals(acl)) {
        bddList.add(bdds.getValue());
      }
    }
    return bddList;
  }

}

package org.batfish.common.bdd;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;

public class BDDPacketWithLines extends BDDPacket{

  private BDD _accept;
  private Map<AclLineRepr, BDD> _aclLineVars;
  private Map<IpAccessListLine, IpAccessList> _linesToLists;

  private class AclLineRepr {
    String router;
    IpAccessList acl;
    IpAccessListLine line;

    public AclLineRepr(String router, IpAccessList acl, IpAccessListLine line) {
      this.router = router;
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
      return router.equals(that.router) && acl.equals(that.acl) && line.equals(that.line);
    }

    @Override public int hashCode() {
      return Objects.hash(router, acl, line);
    }
  }

  public BDDPacketWithLines() {
    super();

    _accept = allocateBDDBit("accept");
    _aclLineVars = new HashMap<>();
    _linesToLists = new HashMap<>();
  }


  public BDD getAccept() {
    return _accept;
  }

  public void setAccept(BDD accept) {
    this._accept = accept;
  }

  public void addAcl(String router, IpAccessList acl) {
    for (IpAccessListLine line : acl.getLines()) {
      BDD var = allocateBDDBit(line.getName());
      AclLineRepr repr = new AclLineRepr(router, acl, line);
      _aclLineVars.put(repr, var);
      _linesToLists.put(line, acl);
    }
  }

  public BDD getAclLine(String router, IpAccessList acl, IpAccessListLine line) {
    AclLineRepr repr = new AclLineRepr(router, acl, line);
    if (!_aclLineVars.containsKey(repr)) {
      System.err.println("Cannot get variable for:");
      System.err.println(repr.router);
      System.err.println(acl.getName());
      System.err.println(line.getName());
      System.err.println();
      _aclLineVars.entrySet().stream().forEach(System.err::println);
    }
    return _aclLineVars.get(repr);
  }

  public IpAccessList getAclFromLine(IpAccessListLine line) {
    return _linesToLists.get(line);
  }

}

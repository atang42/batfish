package org.batfish.common.bdd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;

public class BDDPacketWithLines extends BDDPacket{

  private BDD _accept;
  private Map<String, Map<AclLineRepr, BDD>> _aclLineVars;
  private Map<String, Map<Long, AclLineRepr>> _numToAclLine;
  private Map<String, Map<String, BDDInteger>> _aclVars;

  private class AclLineRepr {
    final IpAccessList acl;
    final IpAccessListLine line;

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

      return Objects.equals(acl.getName(), that.acl.getName()) && Objects.equals(line, that.line);
    }

    @Override public int hashCode() {
      return Objects.hash(acl.getName(), line);
    }
  }

  public BDDPacketWithLines() {
    super();

    _accept = allocateBDDBit("accept");
    _aclLineVars = new HashMap<>();
    _aclVars = new HashMap<>();
    _numToAclLine = new HashMap<>();
  }


  public BDD getAccept() {
    return _accept;
  }

  public void setAccept(BDD accept) {
    this._accept = accept;
  }

  public void addAcl(String router, IpAccessList acl) {
    _aclVars.computeIfAbsent(router, x -> new HashMap<>());
    int bits = 1+(int)Math.ceil(Math.log(acl.getLines().size()+1) / Math.log(2));
    _aclVars.get(router).put(acl.getName(), allocateBDDInteger(router+acl.getName(), bits, false));
    _aclLineVars.computeIfAbsent(router, x -> new HashMap<>());
    _numToAclLine.computeIfAbsent(router, x -> new HashMap<>());
    // BDDs for each line
    for (int i = 0; i < acl.getLines().size(); i++) {
      IpAccessListLine line = acl.getLines().get(i);
      BDD var = _aclVars.get(router).get(acl.getName()).value(i);
      AclLineRepr repr = new AclLineRepr(acl, line);
      _aclLineVars.get(router).put(repr, var);
      _numToAclLine.get(router).put((long)i, repr);
    }

    // BDD for implicit action
    BDD var = _aclVars.get(router).get(acl.getName()).value(acl.getLines().size());
    AclLineRepr repr = new AclLineRepr(acl, null);
    _aclLineVars.get(router).put(repr, var);
    _numToAclLine.get(router).put((long)acl.getLines().size(), repr);
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

  public BDD getAclNoLine(String router, IpAccessList acl) {
    return _aclVars.get(router)
        .get(acl.getName())
        .value(acl.getLines().size());
  }

  public IpAccessListLine getLineFromSolution(String router, IpAccessList acl, BDD solution) {
    Optional<Long> optional = _aclVars.get(router).get(acl.getName()).getValueSatisfying(solution);
    if (optional.isPresent()) {
      return _numToAclLine.get(router).get(optional.get()).line;
    }
    return null;
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

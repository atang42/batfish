package org.batfish.minesweeper.policylocalize.acldiff.representation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.batfish.datamodel.AclIpSpace;
import org.batfish.datamodel.AclIpSpaceLine;
import org.batfish.datamodel.AclLine;
import org.batfish.datamodel.EmptyIpSpace;
import org.batfish.datamodel.ExprAclLine;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.IpIpSpace;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.IpSpaceReference;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.IpWildcardIpSpace;
import org.batfish.datamodel.IpWildcardSetIpSpace;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixIpSpace;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.UniverseIpSpace;
import org.batfish.datamodel.acl.AclLineMatchExpr;
import org.batfish.datamodel.acl.AndMatchExpr;
import org.batfish.datamodel.acl.DeniedByAcl;
import org.batfish.datamodel.acl.FalseExpr;
import org.batfish.datamodel.acl.GenericAclLineMatchExprVisitor;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.datamodel.acl.MatchSrcInterface;
import org.batfish.datamodel.acl.NotMatchExpr;
import org.batfish.datamodel.acl.OrMatchExpr;
import org.batfish.datamodel.acl.OriginatingFromDevice;
import org.batfish.datamodel.acl.PermittedByAcl;
import org.batfish.datamodel.acl.TrueExpr;
import org.batfish.datamodel.visitors.GenericIpSpaceVisitor;

public class AclToDescribedHeaderSpaces {

  private static class IpSpaceVisitor implements GenericIpSpaceVisitor<List<Prefix>> {

    @SuppressWarnings("unchecked")
    @Override
    public List<Prefix> castToGenericIpSpaceVisitorReturnType(Object o) {
      return (List<Prefix>) o;
    }

    @Override
    public List<Prefix> visitAclIpSpace(AclIpSpace aclIpSpace) {
      ArrayList<Prefix> ret = new ArrayList<>();
      for (AclIpSpaceLine line : aclIpSpace.getLines()) {
        if (line.getAction().equals(LineAction.DENY)) {
          System.err.println("AclIpSpace uses DENY action");
          return getAllPrefix();
        }
        ret.addAll(line.getIpSpace().accept(this));
      }
      return ret;
    }

    @Override
    public List<Prefix> visitEmptyIpSpace(EmptyIpSpace emptyIpSpace) {
      return new ArrayList<>();
    }

    @Override
    public List<Prefix> visitIpIpSpace(IpIpSpace ipIpSpace) {
      List<Prefix> ret = new ArrayList<>();
      ret.add(Prefix.create(ipIpSpace.getIp(), Prefix.MAX_PREFIX_LENGTH));
      return ret;
    }

    @Override
    public List<Prefix> visitIpSpaceReference(IpSpaceReference ipSpaceReference) {
      System.err.println("Uses IpSpaceReference");
      return getAllPrefix();
    }

    @Override
    public List<Prefix> visitIpWildcardIpSpace(IpWildcardIpSpace ipWildcardIpSpace) {
      if (ipWildcardIpSpace.getIpWildcard().isPrefix()) {
        List<Prefix> ret = new ArrayList<>();
        ret.add(ipWildcardIpSpace.getIpWildcard().toPrefix());
        return ret;
      }
      System.err.println("IpWildCard is not prefix: " + ipWildcardIpSpace.getIpWildcard());
      return getAllPrefix();
    }

    @Override
    public List<Prefix> visitIpWildcardSetIpSpace(IpWildcardSetIpSpace ipWildcardSetIpSpace) {
      if (ipWildcardSetIpSpace.getBlacklist().size() > 0) {
        System.err.println("IpWildcardSetIpSpace has blacklist");
        return getAllPrefix();
      }
      ArrayList<Prefix> ret = new ArrayList<>();
      for (IpWildcard wildcard : ipWildcardSetIpSpace.getWhitelist()) {
        if (wildcard.isPrefix()) {
          ret.add(wildcard.toPrefix());
        } else {
          System.err.println("IpWildCard is not prefix: " + wildcard);
          return getAllPrefix();
        }
      }
      return ret;
    }

    @Override
    public List<Prefix> visitPrefixIpSpace(PrefixIpSpace prefixIpSpace) {
      List<Prefix> ret = new ArrayList<>();
      ret.add(prefixIpSpace.getPrefix());
      return ret;
    }

    @Override
    public List<Prefix> visitUniverseIpSpace(UniverseIpSpace universeIpSpace) {
      return getAllPrefix();
    }
  }

  private static class AclLineVisitor
      implements GenericAclLineMatchExprVisitor<List<ConjunctHeaderSpace>> {

    @Override
    public List<ConjunctHeaderSpace> visitAndMatchExpr(AndMatchExpr andMatchExpr) {
      List<ConjunctHeaderSpace> ret = new ArrayList<>();
      ret.add(ConjunctHeaderSpace.getUniverseSpace());
      for (AclLineMatchExpr line : andMatchExpr.getConjuncts()) {
        List<ConjunctHeaderSpace> temp = new ArrayList<>();
        for (ConjunctHeaderSpace ps1 : ret) {
          for (ConjunctHeaderSpace ps2 : visit(line)) {
            Optional<ConjunctHeaderSpace> optional = ps1.intersection(ps2);
            if (optional.isPresent()) {
              temp.add(optional.get());
            }
          }
        }
        ret = new ArrayList<>(temp);
      }
      return ret;
    }

    @Override public List<ConjunctHeaderSpace> visitDeniedByAcl(DeniedByAcl deniedByAcl) {
      System.err.println("Uses PermittedByAcl in ACL");
      return getAllPackets();
    }

    @Override
    public List<ConjunctHeaderSpace> visitFalseExpr(FalseExpr falseExpr) {
      return new ArrayList<>();
    }

    @Override
    public List<ConjunctHeaderSpace> visitMatchHeaderSpace(MatchHeaderSpace matchHeaderSpace) {
      HeaderSpace space = matchHeaderSpace.getHeaderspace();
      List<Prefix> dstPrefixes;
      List<Prefix> srcPrefixes;
      List<SubRange> dstPorts;
      List<SubRange> srcPorts;
      List<IpProtocol> protocols;

      if (space.getDstIps() != null) {
        dstPrefixes = space.getDstIps().accept(new IpSpaceVisitor());
      } else {
        dstPrefixes = getAllPrefix();
      }
      if (space.getSrcIps() != null) {
        srcPrefixes = space.getSrcIps().accept(new IpSpaceVisitor());
      } else {
        srcPrefixes = getAllPrefix();
      }
      if (space.getDstPorts() == null || space.getDstPorts().size() == 0) {
        dstPorts = new ArrayList<>();
        dstPorts.add(ConjunctHeaderSpace.DEFAULT_PORT_RANGE);
      } else {
        dstPorts = new ArrayList<>(space.getDstPorts());
      }
      if (space.getSrcPorts() == null || space.getSrcPorts().size() == 0) {
        srcPorts = new ArrayList<>();
        srcPorts.add(ConjunctHeaderSpace.DEFAULT_PORT_RANGE);
      } else {
        srcPorts = new ArrayList<>(space.getSrcPorts());
      }
      if (space.getIpProtocols() == null || space.getIpProtocols().size() == 0) {
        protocols = new ArrayList<>();
        protocols.add(IpProtocol.ISO_IP);
      } else {
        protocols = new ArrayList<>(space.getIpProtocols());
      }
      // TODO: Properly handle other packet attributes
      if (space.getTcpFlags() != null && space.getTcpFlags().size() > 0) {
        // return new ArrayList<>();
      }

      List<ConjunctHeaderSpace> ret = new ArrayList<>();

      for (Prefix dst : dstPrefixes) {
        for (Prefix src : srcPrefixes) {
          for (SubRange dstPort : dstPorts) {
            for (SubRange srcPort : srcPorts) {
              for (IpProtocol proto : protocols) {
                ret.add(new ConjunctHeaderSpace(dst, src, dstPort, srcPort, proto));
              }
            }
          }
        }
      }

      return ret;
    }

    @Override
    public List<ConjunctHeaderSpace> visitMatchSrcInterface(MatchSrcInterface matchSrcInterface) {
      System.err.println("Uses MatchSrcExpr in ACL");
      return getAllPackets();
    }

    @Override
    public List<ConjunctHeaderSpace> visitNotMatchExpr(NotMatchExpr notMatchExpr) {
      System.err.println("Uses NotMatchExpr in ACL");
      return getAllPackets();
    }

    @Override
    public List<ConjunctHeaderSpace> visitOriginatingFromDevice(
        OriginatingFromDevice originatingFromDevice) {
      System.err.println("Uses OriginatingFromDevice in ACL");
      return getAllPackets();
    }

    @Override
    public List<ConjunctHeaderSpace> visitOrMatchExpr(OrMatchExpr orMatchExpr) {
      List<ConjunctHeaderSpace> ret = new ArrayList<>();
      for (AclLineMatchExpr expr : orMatchExpr.getDisjuncts()) {
        ret.addAll(visit(expr));
      }
      return ret;
    }

    @Override
    public List<ConjunctHeaderSpace> visitPermittedByAcl(PermittedByAcl permittedByAcl) {
      System.err.println("Uses PermittedByAcl in ACL");
      return getAllPackets();
    }

    @Override
    public List<ConjunctHeaderSpace> visitTrueExpr(TrueExpr trueExpr) {
      return getAllPackets();
    }
  }

  @Nonnull public static List<ConjunctHeaderSpace> getAllPackets() {
    List<ConjunctHeaderSpace> ret = new ArrayList<>();
    ret.add(ConjunctHeaderSpace.getUniverseSpace());
    return ret;
  }

  @Nonnull private static List<Prefix> getAllPrefix() {
    List<Prefix> ret = new ArrayList<>();
    ret.add(Prefix.ZERO);
    return ret;
  }

  @Nonnull public static List<ConjunctHeaderSpace> createPrefixSpaces(AclLine line) {
    if (line == null) {
      List<ConjunctHeaderSpace> ret = new ArrayList<>();
      ret.add(ConjunctHeaderSpace.getUniverseSpace());
      return ret;
    }
    if (line instanceof ExprAclLine) {
      return ((ExprAclLine) line).getMatchCondition().accept(new AclLineVisitor());
    }
    return new ArrayList<>();
  }

}

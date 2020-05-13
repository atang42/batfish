package org.batfish.minesweeper.question.AclTrace;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.bdd.BDDAcl;
import org.batfish.common.bdd.BDDPacketWithLines;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.AclLine;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ExprAclLine;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.Row.TypedRowBuilder;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.policylocalize.acldiff.AclDiffReport;
import org.batfish.minesweeper.policylocalize.acldiff.AclDiffToPrefix;
import org.batfish.minesweeper.policylocalize.acldiff.LineDifference;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public final class AclTraceAnswerer extends Answerer {

  private static final String COL_INTERFACE = "Interface";
  private static final String COL_ROUTE_INCLUDED_PREFIXES = "Included_Packets";
  private static final String COL_ROUTE_EXCLUDED_PREFIXES = "Excluded_Packets";
  private static final String COL_NODE1 = "Node1";
  private static final String COL_FILTER1 = "Filter1";
  private static final String COL_TEXT1 = "Text1";
  private static final String COL_ACTION1 = "Action1";

  private static final int MAX_INCLUDED_EXCLUDED = 5;

  @Nonnull private BDDPacketWithLines _packet;
  @Nullable private BDD _headerVars = null;

  private static final List<ColumnMetadata> COLUMN_METADATA =
      ImmutableList.of(
          new ColumnMetadata(COL_ROUTE_INCLUDED_PREFIXES, Schema.STRING, "Included Prefixes", true, false),
          new ColumnMetadata(COL_ROUTE_EXCLUDED_PREFIXES, Schema.STRING, "Excluded Prefixes", true, false),
          new ColumnMetadata(COL_NODE1, Schema.STRING, "Node", true, false),
          new ColumnMetadata(COL_FILTER1, Schema.STRING, "Filter name", true, false),
          new ColumnMetadata(COL_TEXT1, Schema.STRING, "Line text", true, false),
          new ColumnMetadata(
              COL_ACTION1,
              Schema.STRING,
              "Action performed by the line (e.g., PERMIT or DENY)",
              true,
              false));

  private static final Map<String, ColumnMetadata> METADATA_MAP =
      TableMetadata.toColumnMap(COLUMN_METADATA);

  public AclTraceAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  private static Row lineDifferenceToRow(LineDifference ld) {

    StringBuilder included = new StringBuilder();
    int count = 0;
    for (String s : ld.getDifference()) {
      if (count >= MAX_INCLUDED_EXCLUDED) {
        included.append("+ ")
            .append(ld.getDifference().size() - MAX_INCLUDED_EXCLUDED)
            .append(" more");
        break;
      }
      included.append(s).append("\n");
      count++;
    }
    StringBuilder excluded = new StringBuilder();
    count = 0;
    for (String s : ld.getDiffSub()) {
      if (count >= MAX_INCLUDED_EXCLUDED) {
        excluded.append("+ ")
            .append(ld.getDiffSub().size() - MAX_INCLUDED_EXCLUDED)
            .append(" more");
        break;
      }
      excluded.append(s).append("\n\n");
      count++;
    }

    TypedRowBuilder builder =
        Row.builder(METADATA_MAP)
            .put(COL_NODE1, ld.getRouter1())
            .put(COL_FILTER1, ld.getFilter1())
            .put(COL_ROUTE_INCLUDED_PREFIXES, included)
            .put(COL_ROUTE_EXCLUDED_PREFIXES, excluded)
            .put(COL_TEXT1, ld.getMatchingLines1())
            .put(COL_ACTION1, ld.getMatchingLines2());
    return builder.build();
  }

  @Override
  public TableAnswerElement answer(NetworkSnapshot snapshot) {
    AclTraceQuestion question = (AclTraceQuestion) _question;
    NodeSpecifier nodeSpecifier = question.getNodeSpecifier();
    Prefix dstPrefix = question.getPrefix();
    SpecifierContext context = _batfish.specifierContext(snapshot);
    Set<String> nodes = nodeSpecifier.resolve(context);
    TreeSet<LineDifference> diffs = new TreeSet<>();
    for (String node : nodes) {
      Configuration config = context.getConfigs().get(node);
      for (IpAccessList accessList : config.getIpAccessLists().values()) {
        diffs.addAll(compareAcls(node, accessList, true, dstPrefix));
        diffs.addAll(compareAcls(node, accessList, false, dstPrefix));
      }
    }
    TableAnswerElement result =
        new TableAnswerElement(new TableMetadata(COLUMN_METADATA));
    for (LineDifference diff : diffs) {
      result.addRow(lineDifferenceToRow(diff));
    }
    return result;
  }

  private static IpAccessList allAccept() {
    return IpAccessList.builder().setName("ACCEPT").setLines(ExprAclLine.ACCEPT_ALL).build();
  }

  private static IpAccessList allReject() {
    return IpAccessList.builder().setName("REJECT").setLines(ExprAclLine.REJECT_ALL).build();
  }

  private void initPacket() {
    _packet = new BDDPacketWithLines();
    _headerVars = null;
  }

  public SortedSet<LineDifference> compareAcls(String name, IpAccessList accessList, boolean result, Prefix dstPrefix) {

    Map<String, IpAccessList> accessLists = new TreeMap<>();
    accessLists.put(name, accessList);
    accessLists.put("~NONE~", result ? allAccept() : allReject());
    initPacket();
    SortedSet<LineDifference> differences = new TreeSet<>();
    List<String> routers = Arrays.asList(name, "~NONE~");
    BDDAcl acl1 = BDDAcl.createWithLines(_packet, routers.get(0), accessLists.get(routers.get(0)));
    BDDAcl acl2 = BDDAcl.createWithLines(_packet, routers.get(1), accessLists.get(routers.get(1)));
    BDD first = acl1.getBdd();
    BDD second = acl2.getBdd();

    BDD acceptVar = _packet.getAccept();

    BDD acceptFirst = first.restrict(acceptVar);
    BDD acceptSecond = second.restrict(acceptVar);
    BDD rejectFirst = first.restrict(acceptVar.not());
    BDD rejectSecond = second.restrict(acceptVar.not());
    BDD notEquivalent = acceptFirst.and(rejectSecond).or(acceptSecond.and(rejectFirst));

    BDD relevantPkts = _packet.getDstIp().geq(dstPrefix.getStartIp().asLong());
    relevantPkts.andWith(_packet.getDstIp().leq(dstPrefix.getEndIp().asLong()));
    notEquivalent.andWith(relevantPkts);
    if (notEquivalent.isZero()) {
      // System.out.println("No Difference");
    } else {
      BDD linesNotEquivalent = notEquivalent.exist(getPacketHeaderFields());
      List<IpAccessList> aclList = new ArrayList<>(accessLists.values());
      AclDiffToPrefix aclDiffToPrefix = new AclDiffToPrefix(routers.get(0),
          routers.get(1),
          aclList.get(0),
          aclList.get(1));
      while (!linesNotEquivalent.isZero()) {
        BDD lineSat = linesNotEquivalent.satOne();
        BDD counterexample = notEquivalent.and(lineSat).satOne();
        int i = 0;
        AclLine[] lineDiff = new AclLine[2];
        for (IpAccessList acl : aclList) {
          for (AclLine line : acl.getLines()) {
            if (counterexample.andSat(_packet.getAclLine(routers.get(i), acl, line))) {
              lineDiff[i] = line;
            }
          }
          i++;
        }
        // diffToPrefix.printDifferenceInPrefix();
        AclDiffReport report = aclDiffToPrefix.getReport(lineDiff[0], lineDiff[1]);
        // report.print(_batfish, _printMore, differential);
        differences.add(report.toLineDifference(_batfish, false, false));
        BDD cond = counterexample.exist(getPacketHeaderFields()).not();
        linesNotEquivalent = linesNotEquivalent.and(cond);
      }

    }
    return differences;
  }

  private BDD getPacketHeaderFields() {
    if (_headerVars != null) {
      return _headerVars;
    }
    _headerVars = _packet.getFactory().one();
    BDD bddDstIp = Arrays.stream(_packet.getDstIp().getBitvec())
        .reduce(_packet.getFactory().one(), BDD::and);
    BDD bddSrcIp = Arrays.stream(_packet.getSrcIp().getBitvec())
        .reduce(_packet.getFactory().one(), BDD::and);
    BDD bddDstPort = Arrays.stream(_packet.getDstPort().getBitvec())
        .reduce(_packet.getFactory().one(), BDD::and);
    BDD bddSrcPort = Arrays.stream(_packet.getSrcPort().getBitvec())
        .reduce(_packet.getFactory().one(), BDD::and);
    BDD bddProtocol = Arrays.stream(_packet.getIpProtocol().getBDDInteger().getBitvec())
        .reduce(_packet.getFactory().one(), BDD::and);
    BDD bddIcmpType = Arrays.stream(_packet.getIcmpType().getBDDInteger().getBitvec())
        .reduce(_packet.getFactory().one(), BDD::and);
    BDD bddIcmpCode = Arrays.stream(_packet.getIcmpCode().getBDDInteger().getBitvec())
        .reduce(_packet.getFactory().one(), BDD::and);
    BDD bddDscp = Arrays.stream(_packet.getDscp().getBitvec())
        .reduce(_packet.getFactory().one(), BDD::and);
    BDD bddEcn = Arrays.stream(_packet.getEcn().getBitvec())
        .reduce(_packet.getFactory().one(), BDD::and);
    BDD bddFragOffset = Arrays.stream(_packet.getFragmentOffset().getBitvec())
        .reduce(_packet.getFactory().one(), BDD::and);
    BDD bddTcpBits = _packet.getTcpAck()
        .and(_packet.getTcpEce())
        .and(_packet.getTcpFin())
        .and(_packet.getTcpCwr())
        .and(_packet.getTcpRst())
        .and(_packet.getTcpPsh())
        .and(_packet.getTcpUrg())
        .and(_packet.getTcpSyn());
    _headerVars.andWith(bddDstIp);
    _headerVars.andWith(bddSrcIp);
    _headerVars.andWith(bddDstPort);
    _headerVars.andWith(bddSrcPort);
    _headerVars.andWith(bddProtocol);
    _headerVars.andWith(bddIcmpType);
    _headerVars.andWith(bddIcmpCode);
    _headerVars.andWith(bddDscp);
    _headerVars.andWith(bddEcn);
    _headerVars.andWith(bddFragOffset);
    _headerVars.andWith(bddTcpBits);
    return _headerVars;
  }


}

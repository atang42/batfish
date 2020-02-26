package org.batfish.minesweeper.question.acltimediff;

import java.util.SortedSet;
import org.batfish.common.Answerer;
import org.batfish.common.BatfishException;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.policylocalize.acldiff.AclDiffToRow;
import org.batfish.minesweeper.policylocalize.acldiff.BddDiff;
import org.batfish.minesweeper.policylocalize.acldiff.LineDifference;

public final class AclTimeDiffAnswerer extends Answerer {

  public AclTimeDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public AnswerElement answer() {
    throw new BatfishException(
        String.format("%s can only be run in differential mode.", _question.getName()));
  }

  @Override
  public AnswerElement answerDiff() {
    AclTimeDiffQuestion question = (AclTimeDiffQuestion) _question;
    NodesSpecifier regex = question.getNodeRegex();
    String aclRegex = question.getAclRegex();
    boolean printMore = question.getPrintMore();
    BddDiff diffChecker = new BddDiff();
    SortedSet<LineDifference> diffs = diffChecker.getTimeDiff(_batfish, regex, aclRegex, printMore);
    TableAnswerElement result =
        new TableAnswerElement(new TableMetadata(AclDiffToRow.COLUMN_METADATA));
    AclDiffToRow toRow = new AclDiffToRow();
    for (LineDifference diff : diffs) {
      result.addRow(toRow.lineDifferenceToRow(diff));
    }
    return result;
  }
}

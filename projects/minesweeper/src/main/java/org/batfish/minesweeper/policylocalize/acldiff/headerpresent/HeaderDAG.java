package org.batfish.minesweeper.policylocalize.acldiff.headerpresent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.sf.javabdd.BDD;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.minesweeper.policylocalize.acldiff.representation.ConjunctHeaderSpace;

public class HeaderDAG {

  private static ConjunctHeaderSpace ALL_PACKETS = ConjunctHeaderSpace.getUniverseSpace();

  private HeaderNode _start;
  private int _intersections;
  private BDDPacket _packet;
  private Map<ConjunctHeaderSpace, HeaderNode> _nodeMap = new HashMap<>();

  private HeaderDAG(BDDPacket packet) {
    _start = new HeaderNode(ALL_PACKETS, packet, false);
    _intersections = 0;
    _packet = packet;
  }

  public HeaderNode getStart() {
    return _start;
  }

  public List<IncludedExcludedHeaderSpaces> getRangesMatchingBDD(BDD bdd, BDDPacket record) {
    return _start.getRangesMatchingBDD(bdd, record, ConjunctHeaderSpace.getUniverseSpace()).getFlattenedRanges();
  }

  public int getIntersectionCount() {
    return _intersections;
  }

  /*
  Add prefix range node to subgraph starting at parent.
  Nodes must be added in order by prefix length of the prefix ranges
  */
  private static void addNode(HeaderNode parent, HeaderNode newNode, HeaderDAG graph) {
    List<HeaderNode> children = parent.getChildren();
    List<HeaderNode> containingNodes = new ArrayList<>();
    List<HeaderNode> containedNodes = new ArrayList<>();
    List<HeaderNode> intersectingNodes = new ArrayList<>();
    if (parent == newNode) {
      // Don't insert node as child of self
      return;
    }
    ConjunctHeaderSpace ConjunctHeaderSpace = newNode.getHeaderSpace();
    for (HeaderNode node : children) {
      if (node.contains(ConjunctHeaderSpace)) {
        containingNodes.add(node);
      } else if (newNode.contains(node.getHeaderSpace())) {
        containedNodes.add(node);
      }
      else if (node.getIntersection(ConjunctHeaderSpace).isPresent()) {
        intersectingNodes.add(node);
      }
    }

    if (containingNodes.isEmpty()) {
      parent.addChild(newNode);
      newNode.addParent(parent);
    } else {
      for (HeaderNode containingNode : containingNodes) {
        addNode(containingNode, newNode, graph);
      }
    }

    for (HeaderNode contained : containedNodes) {
      contained.removeParent(parent);
      parent.removeChild(contained);
      newNode.addChild(contained);
      contained.addParent(contained);
    }

    for (HeaderNode intersecting : intersectingNodes) {
      intersecting.addIntersects(newNode);
      newNode.addIntersects(intersecting);
      Optional<ConjunctHeaderSpace> intersection = newNode.getIntersection(intersecting.getHeaderSpace());
      assert intersection.isPresent();
      if (!graph._nodeMap.containsKey(intersection.get())) {
        HeaderNode intersectionNode = new HeaderNode(intersection.get(), graph._packet, true);
        addNode(parent, intersectionNode, graph);
      }
    }
  }

  public static HeaderDAG build(Collection<ConjunctHeaderSpace> ranges, BDDPacket packet) {
    HeaderDAG graph = new HeaderDAG(packet);
    List<ConjunctHeaderSpace> ConjunctHeaderSpaceList = new ArrayList<>(ranges);
    graph._nodeMap.put(ALL_PACKETS, graph._start);

    // Add all prefix ranges as nodes with parent, children, intersect relationships
    // Sorting ensures that if A contains B then A comes before B
    ConjunctHeaderSpaceList.sort(ConjunctHeaderSpace::compareTo);
    for (ConjunctHeaderSpace range : ConjunctHeaderSpaceList) {
      if (!range.equals(ALL_PACKETS)) {
        HeaderNode newNode = new HeaderNode(range, graph._packet,false);
        if (graph._nodeMap.containsKey(range)) {
          continue;
        }
        graph._nodeMap.put(range, newNode);
        addNode(graph.getStart(), newNode, graph);
      }
    }

    // Mark ends and create generated nodes based on remainders
    int intersections = 0;
    for (HeaderNode node : graph._nodeMap.values()) {
      intersections += node.getIntersects().size();
    }
    // Avoid double counting intersections
    intersections /= 2;
    graph._intersections = intersections;

    return graph;
  }
}

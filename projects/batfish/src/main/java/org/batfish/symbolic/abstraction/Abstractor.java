package org.batfish.symbolic.abstraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import javafx.util.Pair;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.symbolic.Graph;
import org.batfish.symbolic.GraphEdge;
import org.batfish.symbolic.Protocol;

/**
 * <p>Creates an abstraction(s) of the network by splitting the network into a collection of
 * equivalence classes and compressing the representation of each equivalence class. Each
 * equivalence class has the property that all of its stable solutions are bisimilar to the original
 * network. That is, there is a bug in the abstracted network iff there is a bug in the concrete
 * network.</p>
 *
 * <p>How the compression occurs does depend on the property we want to check, since we can only
 * check properties for all concrete nodes that map to the same abstract node. For example, if we
 * want to check reachability between two concrete nodes, then these 2 nodes must remain distinct in
 * the compressed form.</p>
 */
public class Abstractor {

  // TODO: take a parameter indicating the set of devices that must remain concrete.

  private static final String EXTERNAL_NAME = "PEER";

  public static AnswerElement computeAbstraction(IBatfish batfish) {

    Graph g = new Graph(batfish);

    // Figure out which protocols are running on which devices
    Map<String, List<Protocol>> protocols = new HashMap<>();
    g.getConfigurations()
        .forEach(
            (router, conf) -> {
              List<Protocol> protos = new ArrayList<>();
              protocols.put(router, protos);

              if (conf.getDefaultVrf().getOspfProcess() != null) {
                protos.add(Protocol.OSPF);
              }

              if (conf.getDefaultVrf().getBgpProcess() != null) {
                protos.add(Protocol.BGP);
              }

              if (!conf.getDefaultVrf().getStaticRoutes().isEmpty()) {
                protos.add(Protocol.STATIC);
              }

              if (!conf.getInterfaces().isEmpty()) {
                protos.add(Protocol.CONNECTED);
              }
            });

    // Create the trie
    PrefixTrieMap pt = new PrefixTrieMap();

    // Iterate through the destinations
    g.getConfigurations()
        .forEach(
            (router, conf) -> {
              // System.out.println("Looking at router: " + router);
              for (Protocol proto : protocols.get(router)) {
                // System.out.println("  Looking at protocol: " + proto.name());
                List<Prefix> destinations = Graph.getOriginatedNetworks(conf, proto);
                for (Prefix p : destinations) {
                  //System.out.println(
                  //    "Destination for " + router + "," + proto.name() + " has: " + p);
                  pt.add(p, router);
                }
              }
            });

    // Map collections of devices to the destination IP ranges that are rooted there
    Map<Set<String>, List<Prefix>> destMap = pt.createDestinationMap();

    System.out.println("Destination Map:");
    destMap.forEach(
        (devices, prefixes) -> System.out.println("Devices: " + devices + " --> " + prefixes));

    // For each collection of destinations, we need to create an abstract network.
    // First we will figure out sets of nodes that can map together.
    Set<String> allDevices = g.getConfigurations().keySet();

    int min = -1;
    int max = 0;
    int totalAbstract = 0;
    int count = 0;
    double average = 0.0;

    for (Entry<Set<String>, List<Prefix>> entry : destMap.entrySet()) {
      Set<String> devices = entry.getKey();
      List<Prefix> prefixes = entry.getValue();
      UnionSplit<String> workset = new UnionSplit<>(allDevices);

      // System.out.println("Workset: " + workset);

      // Split by the singleton set for each origination point
      for (String device : devices) {
        Set<String> ds = new TreeSet<>();
        ds.add(device);
        workset.split(ds);
      }

      // Repeatedly split the abstraction to a fixed point
      Set<Set<String>> todo;
      do {
        todo = new HashSet<>();
        Set<Set<String>> ps = workset.partitions();

        // System.out.println("Todo set: " + todo);
        // System.out.println("Workset: " + workset);

        for (Set<String> partition : ps) {
          // Create the map from interface policy to neighboring group

          // Nothing to refine if already a concrete node
          if (partition.size() <= 1) {
            continue;
          }

          Map<String, Set<Pair<InterfacePolicy, Set<String>>>> groupMap = new HashMap<>();

          for (String router : partition) {

            // System.out.println("  Looking at router: " + router);

            Set<Pair<InterfacePolicy, Set<String>>> groups =
                groupMap.computeIfAbsent(router, r -> new HashSet<>());

            // TODO: translate the configurations into BDDs

            Configuration conf = g.getConfigurations().get(router);

            List<GraphEdge> edges = g.getEdgeMap().get(router);
            for (GraphEdge edge : edges) {
              if (!edge.isAbstract()) {
                String peer = edge.getPeer();
                InterfacePolicy pol = new InterfacePolicy(1);

                // For external neighbors, we don't split a partition
                Set<String> peerGroup;
                if (peer != null) {
                  Configuration peerConf = g.getConfigurations().get(peer);
                  peerGroup = workset.getPartition(peer);
                  // else {
                  // peerGroup = new TreeSet<>();
                  // peerGroup.add(EXTERNAL_NAME);

                  Pair<InterfacePolicy, Set<String>> pair = new Pair<>(pol, peerGroup);
                  groups.add(pair);

                  // System.out.println("    Group: " + pair.getKey() + "," + pair.getValue());
                }
              }
            }
          }

          Map<Set<Pair<InterfacePolicy, Set<String>>>, Set<String>> inversePolicyMap =
              new HashMap<>();
          groupMap.forEach(
              (router, groupPairs) -> {
                Set<String> routers =
                    inversePolicyMap.computeIfAbsent(groupPairs, grps -> new TreeSet<>());
                routers.add(router);
              });

          // Only add changed to the todo list
          for (Set<String> collection : inversePolicyMap.values()) {
            if (!ps.contains(collection)) {
              todo.add(collection);
            }
          }

          // System.out.println("Todo now: " + todo);
        }

        // Now divide the abstraction further
        for (Set<String> partition : todo) {
          workset.split(partition);
        }

      } while (!todo.isEmpty());


      // Update the metrics
      int abstractSize = workset.partitions().size();
      min = (min < 0 ? abstractSize : Math.min(min, abstractSize));
      max = Math.max(max, abstractSize);
      totalAbstract = totalAbstract + abstractSize;
      count = count + 1;
      average = (double) totalAbstract / (double) count;


      // System.out.println("EC: " + prefixes);
      // System.out.println("Final abstraction: " + workset.partitions());
      // System.out.println("Original Size: " + allDevices.size());
      // System.out.println("Compressed: " + workset.partitions().size());
    }

    System.out.println("===============================================");
    System.out.println("Number of ECs: " + count);
    System.out.println("Concrete size: " + allDevices.size());
    System.out.println("Smallest abstraction: " + min);
    System.out.println("Largest abstraction: " + max);
    System.out.println("Average abstraction: " + average);
    System.out.println("===============================================");

    AbstractionAnswerElement answer = new AbstractionAnswerElement();
    answer.setAverage(average);
    answer.setMax(max);
    answer.setMin(min);
    answer.setNumClasses(count);
    answer.setOriginalSize(allDevices.size());
    return answer;

  }
}

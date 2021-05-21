## Campion

Campion is implemented as an extension of Batfish, an open-source network configuration analysis tool. For more information on Batfish, visit https://www.batfish.org/.

### Running the Docker Image (Recommended)

1. Pull the Docker image

		docker pull modnetv/campion

2. Run the Docker image

		docker run --name campion  -p 9997:9997 -p 9996:9996 modnetv/campion

3. Install or update pybatfish 

		sudo python3 -m pip install --upgrade pip
		sudo python3 -m pip install --upgrade git+https://github.com/batfish/pybatfish.git

4. Run the script

		python3 run_diff.py
   or
   
		python3 run_time_diff.py

    There are two scripts included with the package. run_diff.py is for comparing two backup routers in a single snapshot. run_time_diff.py is for comparing a single router across multiple snapshots. By default, these are set to run on example networks. To run them on other networks, follow the instructions below:

    1. If comparing two backup routers, create a directory and place the configurations within a subdirectory in each called "configs". Then set the "curr_snapshot" variable in the script to the name of the directory. For example, if you place the configurations in "A/configs", then set "curr_snapshot = 'A'" in the script. Set the variable “router_regex" in the script to match the names of the routers that are to be tested. When comparing two backups, this should match exactly 2 routers. Set the variable “output_directory” to the name of the directory that the results should be written to.
    2. If comparing different versions of a router across time, create two directories, and place the configurations within a subdirectory in each called configs. Then update the "ref_snapshot" and "curr_snapshot" variables in the script with the names of those directories. For example, if you place the old versions of configurations in "A/configs" and place the new versions in "B/configs", then edit run_time_diff.py so with "ref_snapshot = 'A'" and "curr_snapshot = 'B'". Set the variable “router_regex" in the script to match the names of the routers that are to be tested. Set the variable “output_directory” to the name of the directory that the results should be written to.

5. Open the results in the directory "output/". Each is a CSV file that can be imported into a spreadsheet application.

### Building the docker image locally

To build the docker image locally, run:

        ./build_image.sh

### Result Files:

Each result file is a CSV file where each row represents a difference. If one router has a feature but the other does not, then "NONE" or empty spaces will appear in the table. If there are no differences found, then no file is produced. The contents of each file and their columns is explained below

* bgp_edge_diff: Checks BGP edge properties like whether extended communities are sent or whether an edge is to a route reflector client
  - Node1 and 2: the names of the two corresponding routers
  - Peer1 and 2: the IP address of their corresponding peers
  - LocalAs1 and 2: the AS number configured for that BGP session
  - RouteReflectorClient1 and 2: true if that BGP session is configured to a route reflector client
  - Capabilities1 and 2: the additional configurable capabilities for each session, including whether it sends communities or advertises inactive routes. The default capabilities differ between router vendors, though there exists commands to override those defaults.
* routes_diff: Finds prefixes that are known on one router but not another
  - Protocol: the protocol that the route is configured with
  - Prefix: the prefix of the route
  - Node1 and 2: the names of the two corresponding routers
  - Source1 and 2: an indication of where the route is configured. For OSPF and connected routes, it is an interface. For static routes it is a command.
* ospf_diff: Check OSPF edge properties like costs and areas
  - Node1 and 2: the names of the two corresponding routers
  - Intf1 and 2: the names of the interfaces that OSPF is configured on
  - IP1 and 2: the IP addresses corresponding to the interfaces
  - Peer1 and 2: the names of the peering router, if known
  - Area1 and 2: the OSPF area
  - AreaType1 and AreaType2: the OSPF area type
  - Cost1 and 2: the OSPF cost
  - IsPassive1 and 2: whether the interface is configured as passive
  - NetworkType1 and 2: the OSPF network types as defined in RFC 2238
* static_route_diff: Checks differences in static routes configured in each pair
  - Prefix: the prefix of the route
  - Node1 and 2: the names of the two corresponding routers
  - Destination1 and 2: the configured next-hops
  - Tag1 and 2: the tag for the static route or -1 if there is no tag
  - AdminDist 1 and 2: the administrative distance of the route
  - Text1 and 2: the text of the lines configuring the route
  - Lines1 and 2: the lines where the route is configured
* acl_diff: Checks different behavior in ACLs defined on corresponding edges
  - Included_Packets and Excluded_Packets: determines the set of packets affected. Only those in the included set but not the excluded set are affected
  - Node1 and 2: the names of the two corresponding routers
  - Filter1 and 2: the names of the corresponding data packet ACLs
  - Text1 and 2: the lines of the ACL relevant to the difference. Sometimes the difference depends on the default action which typically rejects the packet
  - Action1 and 2: the action performed in each corresponding router
* route_map_diff: Checks different behavior in route-maps for corresponding BGP connections
  - Neighbor: the BGP neighbor that both routers peer with, as well as the direction of the policy
  - Included_Prefixes and Excluded_Prefixes: determines the set of prefixes affected. Only those in the included set but not the excluded set are affected
  - Community: the communities attached to the route (at the moment, it finds single example rather than the full list of communities that are treated differently)
  - Protocol: the originating protocol. This is relevant for route redistribution from other protocols
  - Node1 and 2: the names of the two corresponding routers
  - Filter1 and 2: the names of the corresponding data packet route-maps
  - Text1 and 2: text from the configuration relevant to the difference
  - Action1 and 2: the action performed in each corresponding router

### Examples

Two example snapshots are provided in the networks/paper-ex and networks/paper-ex2 directories respectively. These are used in the run_diff.py and run_time_diff.py scripts. 

The two configurations in networks/paper-ex contain the route map difference mentioned in Section 2 of the Campion paper, and the corresponding results can be seen by running run_diff.py, and opening the route_map_diff.csv output file.

Configurations used in the Evaluation section are not provided.

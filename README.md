## Campion

Instructions for running: 

1. Build Batfish with the following commands:
 
> git clone --branch rm-localize https://github.com/atang42/batfish.git
> cd batfish
> . tools/batfish_functions.sh
> batfish_build_all
 
2. Install pybatfish if not installed:
 
> sudo python3 -m pip install --upgrade pip
> sudo python3 -m pip install --upgrade git+https://github.com/batfish/pybatfish.git
 
3a. I have created scripts that call my questions in Batfish. One is for comparing two backups and some are for comparing two versions of the same router across time.
 
If comparing two backup routers, create a directory and place the configurations within a subdirectory in each called "configs". Then set the "curr_snapshot" variable in the script to the name of the directory.
 
For example, if you place the configurations in "A/configs", then set "curr_snapshot = 'A'" in the script.
 
3b. If comparing different versions of a router across time, create two directories, and place the configurations within a subdirectory in each called configs. Then update the "ref_snapshot" and "curr_snapshot" variables in the script with the names of those directories.
 
For example, if you place the old versions of configurations in "A/configs" and place the new versions in "B/configs", then edit run_time_diff.py so with "ref_snapshot = 'A'" and "curr_snapshot = 'B'".
 
4. Set the variable “router_regex" in the script to match the names of the routers that are to be tested. When comparing two backups, this should match exactly 2 routers. 
 
5. Set the variable “output_directory” to the name of the directory that the results should be written to.
 
6. Start Batfish with:  "allinone -runclient false &"
 
7. Run "python3 <script>" where <script> is one of the scripts provided. This should write several .csv files into the output directory. These can be opened with a spreadsheet or other application.
 
Result Files:
 
bgp_edge_diff: Checks BGP edge properties like whether extended communities are sent or whether an edge is to a route reflector client
 
routes_diff: Finds prefixes that are known on one router but not another
 
ospf_diff: Check OSPF edge properties like costs and areas. Works better if you include configurations for adjacent routers in the configs/ directories (might be slower).
 
static_route_diff: Checks differences in static routes configured in each pair
 
acl_diff: Checks different behavior in ACLs defined on corresponding edges
 
route_map_diff: Checks different behavior in route-maps for corresponding BGP connections
 

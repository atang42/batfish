from pybatfish.client.commands import *
from pybatfish.question.question import load_questions, list_questions
from pybatfish.question import bfq

load_questions()

# Set network snapshot here
bf_init_snapshot('networks/example/live-with-bgp-announcements')

# Set regular expression for nodes that matches exactly 2 nodes
regex = "as2border.*"

# Set ignored prefix ranges here (format is PREFIX:RANGE)
ignored_list = [
#    "128.97.0.0/16:16-32",
#    "131.179.0.0/16:16-32",
#    "149.142.0.0/16:16-32",
#    "164.67.0.0/16:16-32",
#    "169.232.0.0/16:16-32",
#    "172.16.0.0/16:16-32",
#    "172.17.0.0/16:16-32",
#    "172.21.0.0/16:16-32",
#    "192.35.225.0/24:24-32",
#    "192.154.2.0/24:24-32",
]

# Set output file name
output_file = "result.html"


# Process
router_diff = bfq.routerdiff(nodes=regex, ignored=",".join(ignored_list)).answer()
fout = open(output_file, "w")
fout.write(router_diff.frame().to_html().replace("\\n", "<br>&nbsp;").replace("\\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"))
print (len(router_diff.rows), "differences found")

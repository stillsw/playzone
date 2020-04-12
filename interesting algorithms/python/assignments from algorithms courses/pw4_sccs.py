import sys, time, thread
from random import randint

"""
Directed graph, input is a list of nodes which each point to another node
the task is to find all the strongly connected components (SCCs) using Kosaraju's 2 pass algorithm

IMPORTANT NOTE: to be able to run the full assignment have to increase limits

    1) python's stacksize limit: (see main method at end of file)
	sys.setrecursionlimit(1000000)
    
    2) Linux's stack limit: (on command line - if don't do this, get segmentation fault)
	ulimit -s 10000000

"""
class Globals:
    debug = False
    debugAndWait = False

    # problem implementation vars
    ft = 0 				# finishing time, incremented each time a node is finished being processed in the first pass

def calcSccs(N):
    """
    N is a list of Node and links to the node it points plus the nodes that point to it
    they are already in order, so pop them off to process
    """
    pass2N = []
    numNodes = len(N)			# for sanity check
    
    if Globals.debug: 
	print('DEBUG list after loading')
	printNodeList(N)
	    
    if Globals.debug: print('BEGIN 1ST PASS: order by finishing times')
    
    while len(N) != 0:			# first pass to get finishing times, outer loop to make sure catch all nodes
	n = N.pop()
	if Globals.debug: printNode(n)
	
	if not n.visitedThisPass:
	    #dfsFirstPassRevNotRecursive(n, pass2N)
	    dfsFirstPassRev(n, N, pass2N)	# start the dfs for this particular node, N will lose other nodes in here, they're added in order to pass2N
    
    if len(pass2N) != numNodes:
	raise ValueError('node counts differ from after first pass, before', numNodes, 'after', len(pass2N))
    
    if Globals.debug: print('BEGIN 2ND PASS: make sccs')
    
    for n in pass2N: 			# reset visited flags
	n.visitedThisPass = False
    
    leaderSets = []
    while len(pass2N) != 0:		# second pass to get sets, outer loop to make sure catch all nodes
	n = pass2N.pop()
	if n.visitedThisPass:
	    continue
	
	if Globals.debug: printNode(n)
	leaderSet = [n]			# each starting node is a new leader and the first in its set
	leaderSets.append(leaderSet)
	dfsSecondPass(n, pass2N, leaderSet)
	#dfsSecondPassNotRecursive(n, pass2N, leaderSet)
    
    print('RESULTS: sccs')
    
    leaderSets.sort(cmp=bySize)		# sort the results by size
    
    results = ''
    for i in range (0, 5):
	if len(leaderSets) > i:
	    ls = leaderSets[i]
	    results = results + str(len(ls))
	    if Globals.debug: 
		otherNodes = ''
		for nn in ls[1:]: otherNodes = otherNodes + ', ' + str(nn.label)
		print('set leader node', ls[0].label, 'size', len(ls), 'scc', otherNodes)
	else:
	    results = results + '0'
	
	if i < 4:
	    results = results + ','
	    
    print(results)

def bySize(set1, set2):
    return len(set2) - len(set1)    

# version 1, works correctly, but needs stack size changes as mentioned at top of file
def dfsFirstPassRev(n, N, pass2N):
    n.visitedThisPass = True		# ensure no other node will re-open this one
    
    for prevN in n.fromNodes:		# recurse for all the previous nodes
	if not prevN.visitedThisPass:
	    #N.remove(prevN)
	    dfsFirstPassRev(prevN, N, pass2N)
    
    Globals.ft += 1			# increment finishing times
    n.label = Globals.ft		# set the label as this node is popped off 
    pass2N.append(n)			# add to next pass stack, effectively the finishing times are superfluous, but for the exercise...
    if Globals.debug: printNode(n, '     ')

# v1, again hit problem with recursion limit for large set
def dfsSecondPass(n, N, leaderSet):
    n.visitedThisPass = True		# ensure no other node will re-open this one
    
    for nextN in n.nextNodes:		# recurse
	if not nextN.visitedThisPass:
	    leaderSet.append(nextN)	# add it to the same leader set
	    if Globals.debug: printNode(n, '     ')
	    dfsSecondPass(nextN, N, leaderSet)

"""
# version 2, don't use recursion because python hits a limit, but there's some kind of a bug either in this or the 2nd pass method v2
# because the numbers come out wrong

Found this on the forum which might help... if it's to do with incorrect finishing times anyway, which it might not be!
"Turning DFS into an iterative algorithm turned out more difficult than I thought. 
In the end I had to keep a flag for each node, to see how often that node was visited. When popping a node from the stack, 
I check how often I have seen this node before. If the node is visited for the first time, I had to push it back onto the stack. 
If the node is visited for the second time, I increment the timer for that node. "

def dfsFirstPassRevNotRecursive(n, pass2N):

    fringe = [n]
    path = []
    
    def isFinished(node):
	for prevN in node.fromNodes:
	    if not prevN.visitedThisPass:
		return False
	return True
    
    while fringe:
	node = fringe.pop()
	if Globals.debug: printNode(node, '          popped off fringe')
	if node.visitedThisPass:
	    if Globals.debug: print('               already visited, ignoring')
	    continue
	
	node.visitedThisPass = True
	
	for prevN in node.fromNodes:	# add all the previous nodes to the stack
	    if not prevN.visitedThisPass:
		fringe.append(prevN)
		if Globals.debug: printNode(prevN, '               add to fringe')
		
	path.append(node)
	while path and isFinished(path[-1]):
	    fNode = path.pop()
	    Globals.ft += 1		# increment finishing times
	    fNode.label = Globals.ft
	    pass2N.append(fNode)	# add to next pass stack, effectively the finishing times are superfluous, but for the exercise...

# v2, non recursing
def dfsSecondPassNotRecursive(n, N, leaderSet):
        
    fringe = [n]

    while fringe:
	node = fringe.pop()
	if Globals.debug: printNode(node, '          popped off fringe')
	if node.visitedThisPass:
	    if Globals.debug: print('               already visited, ignoring')
	    continue
	
	node.visitedThisPass = True

	for nextN in node.nextNodes:
	    if not nextN.visitedThisPass:
		leaderSet.append(nextN)
		if Globals.debug: printNode(n, '     ')
		fringe.append(nextN)

"""

def printNodeList(N):
    for n in N: printNode(n)

def printNode(n, prefix=''):
    nextNodes = ''
    for nn in n.nextNodes: nextNodes = nextNodes + ', ' + nn.__str__()
    print(prefix + '     Node', n.__str__(), 'points to', nextNodes)
    
def reportTime(stmt, prevTime):
    now = time.clock()
    if now - prevTime > 0.0:
	print('           timing for', stmt, now - prevTime)
    return now
    
def readInputFile(fname):
    text_file = open(fname, "rU")
    lines = text_file.read().splitlines()
    return lines

def convertDataLines(dataLines):
    N = []				# will want these in reverse order, so add to a list as they come in 
					# and then pop them off that list to process
    nLabels = dict()			# temporary lookup to enable resolving of labels to nodes
    
    for line in dataLines:
	vs = map(int, line.split()) 
	if nLabels.has_key(vs[0]):	# > 1 lines of same node followed by another directed to node
	    n = nLabels.get(vs[0])
	    n.addDirectedToNode(vs[1])
	else:
	    n = Node(vs[0], vs[1])
	    N.append(n)			# both ids are the labels
	    nLabels[n.label] = n	# add to temp lookup for the next step to be easy
    
    for n in N:				# pre-process by resolving next nodes to the actual nodes instead of the labels
	for i in range(0, len(n.nextNodes)):
	    nn = nLabels.get(n.nextNodes[i])
	    if nn == None:		# no next node so only appeared as a to node for another, add it to the dict for subsequent nodes
		nn = Node(n.nextNodes[i])
		nLabels[n.nextNodes[i]] = nn
		N.append(nn)
	    n.nextNodes[i] = nn
	    nn.addDirectedFromNode(n)
	
    return N

class Node:
    def __init__(self, label, nextNodeLabel=None):
	if nextNodeLabel != None:
	    self.nextNodes = [nextNodeLabel]
	else:
	    self.nextNodes = []
	self.label = label
	self.oriLabel = label
	self.fromNodes = []
	self.visitedThisPass = False
    
    def __str__(self):
	return "%s (prev=%s)" % (self.label, self.oriLabel)
	
    def addDirectedFromNode(self, prevNode):
	self.fromNodes.append(prevNode)
    
    def addDirectedToNode(self, nextNode):
	self.nextNodes.append(nextNode)
    
def submitDataFile(hint, fname):

    dataLines = readInputFile(fname)
    # each line is a string of a vertex number followed by the vertex its edge goes to
    print(fname, 'contains #nodes', len(dataLines), 'hint', hint)
    N = convertDataLines(dataLines)
    
    Globals.ft = 0
    calcSccs(N)
    
if __name__ == '__main__':
    """
    min cut the matrix of input numbers provided in a file
    """
    if len(sys.argv) == 2:
	# this is the biggie, see if these tricks get over the max recursion depth errors
	sys.setrecursionlimit(1000000)
	submitDataFile('no hint', sys.argv[1])
    else:
	submitDataFile('expected result: 3,3,3,0,0', 'testcase1.txt')
	submitDataFile('expected result: 3,3,2,0,0', 'testcase2.txt')
	submitDataFile('expected result: 3,3,1,1,0', 'testcase3.txt')
	submitDataFile('expected result: 7,1,0,0,0', 'testcase4.txt')
	submitDataFile('expected result: 6,3,2,1,0', 'testcase5.txt')
	submitDataFile('expected result: 5,4,3,2,1', 'testcase6.txt')

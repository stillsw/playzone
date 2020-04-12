import sys, time, heapq
from random import randint

"""
Takes an adjacency list as input with a bunch of tuples on same line (vertex, distance)
Computes shortest paths from a start node (eg. index 1) to a list of destinations
"""
class Globals:
    debug = False
    debugAndWait = False
    timing = False

def computeShortestPaths(N, startNode, endNodes):
    """
    N is the set of all nodes, with links to all forward nodes
    """
    paths = dict() # the paths to each of the endNodes (only for debugging)
    dists = dict() # the distances

    # set up complete graph search 
    closed = dict()
    fringe = PriorityQueue()			# fringe is a queue of arrays in format [state, actions, accumDist], priority is the accumDist
    startAction = None
    if Globals.debug: startAction = []
    fringe.push([startNode, startAction, 0], 0)	# put the start node on the fringe with dist 0

    if Globals.debug: print('START SEARCH:')

    while not fringe.isEmpty():
	stateNode,path,accumDist = fringe.pop()
	n = N.get(stateNode)
	
	if len(dists) == len(endNodes):		# simple goal state test, don't have to keep going
	    break
	    
	if Globals.debug: print('  testing node '+str(stateNode)+' accumDist='+str(accumDist)+' path so far', path)
		
	# expand iff either not in closed, or in there with a higher dist than this one
	if shouldExpandNode(stateNode, accumDist, closed):

	    if stateNode in endNodes:		# after have established should expand it, then add to end nodes if applicable
		dists[stateNode] = accumDist
		if Globals.debug: paths[stateNode] = path
	    
	    if Globals.debug: print('    expanding node '+str(stateNode))
	    
	    closed[stateNode] = accumDist	# add/update it to closed set with dist
	    
	    for m in n.edges:			# get successors (following the ai course graph search, just add all to fringe, it's on pop they'll be expanded or not
		newDist = accumDist + m.dist	# add in the distance to the accum
		actions = None
		if Globals.debug: 
		    print('          add toNode to fringe '+str(m.toNode)+' accumDist='+str(newDist))
		    actions = path[0:]		# copy the path so far
		    actions.append(m.toNode)
		fringe.push([m.toNode, actions, newDist], newDist)

    printResults(startNode, endNodes, paths, dists)
    
def shouldExpandNode(state, dist, closed, costSensitive=True):
    if closed.has_key(state): 
      if costSensitive:
        # cscs means if the cost of this path is less than that on the path already, it should expand
        return dist < closed.get(state)
      else:
        return False
    else:
        return True

def printResults(startNode, endNodes, paths, dists):
    results = ''
    if Globals.debug: print('RESULTS: node '+str(startNode))
    for en in endNodes:
	results = results + str(dists.get(en))
	if Globals.debug: print('     to node', en, 'dist=', dists.get(en), 'path', paths.get(en))
	
	if en != endNodes[-1]:
	    results = results + ','
	    
    print(results)
    
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
    N = dict() # nodes, with edges as a list member

    for line in dataLines:
	tuples = line.split() # format is 'n1 n2,d n3,d n4,d'
	n = Node(int(tuples[0]))
	for pair in tuples[1:]:
	    m,d = map(int, pair.split(','))
	    n.addEdge(Edge(m, d))
	N[n.node] = n
	
    return N

class Node:
    def __init__(self, node):
	self.node = node
	self.edges = []
    
    def addEdge(self, edge):
	self.edges.append(edge)
	
class Edge:
    def __init__(self, toNode, dist):
	self.toNode = toNode
	self.dist = dist
        
class PriorityQueue:
    """
      Borrowed from AI course, uses a heap for fast retrieval

      Note does not allow change of priority. But can insert the same item multiple times with
      different priorities.
    """
    def  __init__(self):
        self.heap = []
        self.count = 0

    def push(self, item, priority):
        entry = (priority, self.count, item)
        heapq.heappush(self.heap, entry)
        self.count += 1

    def pop(self):
        (_, _, item) = heapq.heappop(self.heap)
        return item

    def isEmpty(self):
        return len(self.heap) == 0

def submitDataFile(hint, fname, start, dests):

    dataLines = readInputFile(fname)
    # each line is a string of numbers separated by spaces
    print(fname, 'contains #nodes', len(dataLines), 'hint', hint)
    N = convertDataLines(dataLines)
    
    if not N.has_key(start):
	raise ValueError('start '+str(start)+' not in the graph')
    
    for d in dests:
	if not N.has_key(d):
	    raise ValueError('dest '+str(d)+' not in the graph')
    
    if Globals.debug: 
	print('NODES:')
	for n in N.values():
	    print('     node '+str(n.node))
	    for m in n.edges:
		print('         to node '+str(m.toNode)+' dist='+str(m.dist))
    
    computeShortestPaths(N, start, dests)
    
if __name__ == '__main__':
    if len(sys.argv) == 2:
	if sys.argv[1] == 'real':
	    submitDataFile('no hint', 'dijkstraData.txt', 1, [7,37,59,82,99,115,133,165,188,197])
	else:
	    print('usage: wanna run the real assignment version? just type real as the arg')
    else:
	Globals.debug = True
	submitDataFile('expected result: {1 0 []}, {2 3 [2]}, {3 3 [3]}, {4 5 [2, 4]}', 'testcase1.txt', 1, [1, 2, 3, 4])
	submitDataFile('expected result: {1 0 []}, {2 3 [2]}, {3 4 [2, 3]}, {4 5 [2, 4]}', 'testcase2.txt', 1, [1, 2, 3, 4])
	submitDataFile('expected result: 2', 'testcase3.txt', 1, [4])
	submitDataFile('expected result: 5', 'testcase4.txt', 1, [7])
	submitDataFile('expected result: 26', 'testcase5.txt', 13, [5])
	submitDataFile('expected result: 9 with path [16, 6] ', 'testcase6.txt', 28, [6])



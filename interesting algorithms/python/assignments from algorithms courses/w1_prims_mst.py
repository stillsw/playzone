import sys, utils, heapUtils

"""
Prim's minimum spanning tree algorithm
Fast implementation using heap that allows delete and update of priority
"""
def computeMinSpanTree(V):

    T = []                          # for fun, keep the list of edges that form the min span tree (not needed for the hw)
    X = []
    
    totalCost = 0.0
    
    while not V.isEmpty():
        n = V.pop()                 # pop the next closest node from V
        
        e = n.closestEdge           # for all but the first there should be an edge corresponding to the closest node
        if e == None and len(X) != 0:
            print 'VALUE ERROR: strange to find a node that is closest but has no edge for that (node=%d)' % (n.node)
            raise ValueError()

        elif e != None:
            T.append(e)             # keep the edge
            totalCost += e.cost
        
        updateHeapKeys(V, n)        # all nodes still in V and connected to x need their heap keys (cost) reset

        X.append(n)                 # add the node to X
        
        if utils.Debug.debug:
            print 'added node %d to X' % (n.node)
            heapUtils.printHeapTree(V.heap, True)

    print '    total cost = %f, num edges = %d' % (totalCost, len(T))

def updateHeapKeys(V, n1):
    # for each edge where the other end is a node still in V
    # update its key in the heap to the cost only if it's better
    for e in n1.incidentEdges:
        if e.fromNode == n1:
            n2 = e.toNode
        else:
            n2 = e.fromNode
        
        if V.lookup(n2.node) != None:
            if V.updateBetter(n2.node, e.cost):
                n2.setClosestEdge(e)    # keep hold of the closest edge, it's not req'd for the hw, but seems good to be able to track the edges in the mst

class Node:
    def __init__(self, node):
        self.node = node
        self.incidentEdges = []
        self.closestEdge = None         # only set when updated in updateHeapKeys()
    
    def __str__(self):
        return str(self.node)
        
    def addIncidentEdge(self, edge):
        self.incidentEdges.append(edge)
    
    def setClosestEdge(self, edge):
        self.closestEdge = edge
        
class Edge:
    def __init__(self, fromNode, toNode, cost):
        self.fromNode = fromNode
        self.fromNode.addIncidentEdge(self)
        self.toNode = toNode
        self.toNode.addIncidentEdge(self)
        self.cost = cost
        
def submitDataFile(hint, fname):
    submitDataArray(hint, fname, utils.readInputFile(fname))

def submitDataArray(hint, source, lines):
    nums = []
    for l in lines:
        j = map(float, l.split())
        nums.append(j)
        
    # first line is number of nodes and number of edges (lines to follow), the rest are pairs of integers weight, length
    print '  source \'%s\', #nodes=%d, #edges=%d hint=%s' % (source, nums[0][0], nums[0][1], hint)

    V = heapUtils.Heap()        # set of nodes
    
    POS_INFINITY = 1e3000
    
    for v1, v2, cost in nums[1:]: # ignore the first, it's just a count
    
        n1 = V.lookup(v1)       # test for already in the set
        if n1 == None: 
            n1 = Node(v1)
            V.push(n1, POS_INFINITY, v1)
                
        n2 = V.lookup(v2)
        if n2 == None: 
            n2 = Node(v2)
            V.push(n2, POS_INFINITY, v2)
        
        # create an edge, adds itself to both nodes for easy lookup
        Edge(n1, n2, cost)

    computeMinSpanTree(V)
    
if __name__ == '__main__':
    if len(sys.argv) == 2:
        if sys.argv[1] == 'debug':
            utils.Debug.debug = True
        elif sys.argv[1] != 'real':
            print('usage: real')
            sys.exit()    
        submitDataFile('no hint', 'w1_asgn2_edges.txt')
    else:
        submitDataArray('expected result: tree 1,2,3 sum=6', 'test0', ['4 4','1 2 1','2 3 2','3 4 3','4 1 4'])
        submitDataArray('expected result: Tree: [1, 1, 2] Sum: 4', 'test1', ['4 5','1 2 1','1 4 4','2 3 2','2 4 1','3 4 3'])
        submitDataArray('expected result: -16', 'test2', ['6 11','1 2 10','1 5 -3','1 4 5','1 3 4','2 6 6','2 3 7','3 6 -10','3 4 -1','4 6 2','4 5 -8','5 6 1'])
        utils.Debug.debug = True
        submitDataArray('expected result: 1.81', 'test3', ['8 16','4 5 0.35','4 7 0.37','5 7 0.28','0 7 0.16','1 5 0.32','0 4 0.38','2 3 0.17','1 7 0.19','0 2 0.26','1 2 0.36','1 3 0.29','2 7 0.34','6 2 0.40','3 6 0.52','6 0 0.58','6 4 0.93'])
        utils.Debug.debug = False
        submitDataFile('expected result:10.46351', 'w1_asgn3_mediumEWG.txt')

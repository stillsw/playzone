import sys, utils, unionFindUtils

"""
Kruskal's minimum spanning tree algorithm
Fast implementation using union find data structure
"""
def computeMinSpanTree(M):

    M.sort(cmp=byCost)
    T = []
    uf = unionFindUtils.UnionFind()
    
    totalCost = 0.0
    
    for e in M:
        # this is the graph search step to ensure no cycles, seems can do it purely with the union find here
        lead1 = uf.find(e.fromNode, False)          # don't fail if not there
        lead2 = uf.find(e.toNode, False)
        
        if lead1 != None and lead1 == lead2:        # same set, don't include as will be a cycle
            continue

        if utils.Debug.debug: 
            print 'debug: v1=%s v2=%s' % (e.fromNode, e.toNode)
            unionFindUtils.printUnionFind(uf)
        
        uf.union(e.fromNode, e.toNode)

        if utils.Debug.debug: 
            print 'debug: after call to union'
            unionFindUtils.printUnionFind(uf)

        T.append(e)
        totalCost += e.cost

    print '    total cost = %f, num edges = %d, num closed sets = %s' % (totalCost, len(T), uf.getNumSets())

def byCost(e1, e2):
    if e1.cost < e2.cost: return -1
    else: return 1
    
class Edge:
    def __init__(self, fromNode, toNode, cost):
        self.fromNode = fromNode
        self.toNode = toNode
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

    M = []
    for v1, v2, cost in nums[1:]: # ignore the first, it's just a count
        M.append(Edge(v1, v2, cost))

    computeMinSpanTree(M)
    
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
        utils.Debug.debug = True
        submitDataArray('expected result: Tree: [1, 1, 2] Sum: 4', 'test1', ['4 5','1 2 1','1 4 4','2 3 2','2 4 1','3 4 3'])
        submitDataArray('expected result: -16', 'test2', ['6 11','1 2 10','1 5 -3','1 4 5','1 3 4','2 6 6','2 3 7','3 6 -10','3 4 -1','4 6 2','4 5 -8','5 6 1'])
        utils.Debug.debug = False
        submitDataArray('expected result: 1.81', 'test3', ['8 16','4 5 0.35','4 7 0.37','5 7 0.28','0 7 0.16','1 5 0.32','0 4 0.38','2 3 0.17','1 7 0.19','0 2 0.26','1 2 0.36','1 3 0.29','2 7 0.34','6 2 0.40','3 6 0.52','6 0 0.58','6 4 0.93'])
        submitDataFile('expected result:10.46351', 'w1_asgn3_mediumEWG.txt')

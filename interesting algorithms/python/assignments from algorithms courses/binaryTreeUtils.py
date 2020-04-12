import utils

"""
Home grown binary tree data structure implementations
NOTE: this is a work in progress, if I come back to it or not is not known right now :D
   
    API:
    
"""
class BinaryTree:
    
    def __init__(self, nodeEvaluator=None):
        self.root = None
        self.count = 0 # the number of leaves not branches
        if nodeEvaluator == None:
            self.nodeEvaluator = DefaultNodeEvaluator()
        else:
            self.nodeEvaluator = nodeEvaluator
        
    def __str__(self):
        if self.root == None:
			return 'binary tree is empty'
		else:
			return 'binary tree #items='+str(self.count)
    
    def search(self, value, node=None):
        if self.root == None:
            return None
            
        if node == None:
            node = self.root
            
        comp = nodeEvaluator.compareNodes(node, value)
        
        if comp == 0:
            return node
            
        elif isinstance(node, BranchNode):
            if comp == -1:
                return self.search(value, node.leftNode)
            else:
                return self.search(value, node.rightNode)
        else:
            return None
    
    def insert(self, item, node=None, parentNode=None, isLeft=False, isRight=False):
        self.count += 1
        
        if self.root == None:       # first insert, make it the root
            self.root = item
            return
            
        if node == None:            # first call, start at the root
            node = self.root
            
        if isinstance(node, BranchNode):    # at a branch
                                            # comparison means either going to be inserted in the left tree or the right
            if nodeEvaluator.compareNodes(node, item) == -1:
                if node.leftNode == None:   # it's less than the node, and there's nothing there already, done
                    node.leftNode = item
                else:
                    self.insert(item, node.leftNode, node, isLeft=True) # there's already a left item, recurse down a level
            else:
                if node.rightNode == None:  # same for right side
                    node.rightNode = item
                else:
                    self.insert(item, node.rightNode, node, isRight=True)
        
        else:   # node isn't already a branch, need to make one
            
            b = self._makeBranch(node, item)
            
            # where to insert it
            
            if node == self.root:   # at the root, there's no parent, so the branch goes here
                self.root = b
                
            else:                   # otherwise, there better be a parent
                assert parentNode != None, 'insert no parentNode it should only happen at the root node'
                
                if isLeft:
                    parentNode.leftNode = b
                else:
                    parentNode.rightNode = b                
        
    def _makeBranch(self, node1, node2):
        b = BranchNode()
        
        comp = nodeEvaluator.compareNodes(node1, node2)
            
        if comp == -1:
            b.leftNode = node1
            b.rightNode = node2
        else:
            b.leftNode = node2
            b.right_node = node1
    
        nodeEvaluator.setValue(b)
        
        return b
    
    def len(self):
        return self.count

    def isEmpty(self):
        return self.count == 0

class BranchNode:
    
    def __init__(self, value=None):
        self.value = value
        self.rightNode = None
        self.leftNode = None

class DefaultNodeEvaluator:
    # expects nodes to be either instances of branch nodes or it will attempt a direct comparison of the value
    # to do anything else, create an Evaluator class with the required types
    
    def compareNodes(self, node1, node2):
        assert node1 != None and node2 != None, 'compareNodes can only compare non-null nodes'
        
        if isinstance(node1, BranchNode):
            val1 = node1.value
        else:
            val1 = node1
            
        if isinstance(node2, BranchNode:
            val2 = node2.value
        else:
            val2 = node2
        
        if val1 < val2: return -1
        elif val1 == val2: return 0
        else: return 1
    
    def setValue(self, branch): # called when a branch has been created or changed to set its value
        
    
def printBinaryTree(bt):
    if bt.len() == 0:
        print 'union find is empty'
    else:
        print 'union find sets:'
        for k,lv in uf.sets.items():
            print '    leader = %s' % str(k)
            for it in lv:
                print '        %s' % str(it)
    
def runTests():
    test1 = ['one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten']
    
    print 'add a bunch of strings all in own sets'
    bt = BinaryTree()
    for s in test1:
        uf.union(s, None)

    printUnionFind(uf)

    uf = UnionFind()    
    print 'add a bunch of strings in set pairs'
    i = 0
    while i < len(test1) -1:
        uf.union(test1[i], test1[i+1])
        i += 2
        
    printUnionFind(uf)
    
    print 'merge a couple of the sets together from non-leaders' # leaders are all odd numbers
    uf.union(test1[1], test1[3])
    printUnionFind(uf)
    
    print 'merge one of those with a leader' # leaders are all odd numbers
    uf.union(test1[1], test1[6])
    printUnionFind(uf)
    
    print 'merge all the remaining together'
    for s in test1:
        uf.union(s, test1[0])
    printUnionFind(uf)
    
    
    #utils.waitForInput()

        
if __name__ == '__main__':
    
    utils.Debug.debug = True
    print 'running binary tree tests'
    runTests()

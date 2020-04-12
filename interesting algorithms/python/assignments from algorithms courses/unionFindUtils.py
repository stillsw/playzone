import utils

"""
Home grown union-find data structure implementation 
Call with a leaderManager which can be one of the classes below, or any ad-hoc implementation (see methods in following classes)
or just allow default, which will create Nodes and manage sets. Depending on need for which data to hold onto, and speed/volume etc might use another impl,
for instance, w2 assignment 2 has massive data, but only needs numbers in a set, not the actual data, at the end. Hence use the Array based impl in following class
   
    API:
        init()               : leaderManager, optional class see above comments
                               lazy, optional implement as lazy leader algorithm
                               compressPaths, optional when use lazy only, to update paths during find when traversed
                      
        isItemPartOfAnySet() : as it says
        
        makeNewSet()         : as it says
        
        find()               : gets the leader for an item, which may include path traversal if using lazy impl, and path compression on that too
        
        union()              : the merge method
        
        unionWithLeaders()   : the body of the merge method, called when already have found the leaders so avoid additional lookups again
        
        The following methods are implemented by the leaderManager class, depending on the impl some may not be supported
            getNumSets()
            getSets()
            len()
"""
class UnionFind:
    
    # manageOwnLeaders when the caller will manage the get/setLeader() management (see Node class below which can be a wrapper, and the default get/setLeader functions below too)
    # lazy prevents management of lists per set, they are not updated when the leader changes, only the leader
    def __init__(self, leaderManager=None, lazy=False, compressPaths=False): # compressPaths only applies with lazy is set 
    
        assert (lazy or not compressPaths), 'UnionFind, compress paths can only be set for lazy impl'
        
        if leaderManager == None:
            self.leaderManager = DefaultSetDataManager()
        else:
            self.leaderManager = leaderManager
            
        self.lazy = lazy
        self.compressPaths = compressPaths
        
    def __str__(self):
        return str(self.leaderManager)
    
    def isItemPartOfAnySet(self, item):
        return self.leaderManager.getLeader(item) != None
    
    def makeNewSet(self, item):
        if self.isItemPartOfAnySet(item):
            if utils.Debug.debug: print 'ERROR: attempt to make a new set with item already in a set %s' % item
            raise ValueError('UnionFind.makeNewSet: called for already cataloged item')
        
        return self.leaderManager.newSet(item)      # return leader of the new set, which is the item (but the set manager may create a wrapper for it)

    def find(self, item, failOnError=True):
        
        leader = self.leaderManager.getLeader(item)
        if type(leader) != type(item) and self.lazy:
            print 'warning: calling find(item) with a raw item, have to do something to map it to same type as leader for lazy impl'
            
        if leader == None and failOnError:
            if utils.Debug.debug: print 'ERROR: attempt to find leader for unknown item %s' % item
            raise ValueError('UnionFind.find: called for uncataloged item')

        if self.lazy and item != leader:            # may have to walk up the tree of leaders to find the latest leader 
            leader = self.find(leader, failOnError)
            if self.compressPaths:
                self.leaderManager.setLeader(item, leader)

        #assert self.sets.has_key(leader), 'unionFind.find: leader (%d) not in sets (item=%d, self leader=%s, sets lookup=%s)' % (leader.n, item.n, str(item.getLeader().n), self.sets.get(leader))
        
        return leader

    def union(self, item1, item2):                  # get both sets and if they're different, merge them directly
        lead1 = self.find(item1, False)             # don't fail if not found
        lead2 = self.find(item2, False)
        return self.unionWithLeaders(item1, item2, lead1, lead2)
            
    def unionWithLeaders(self, item1, item2, lead1, lead2): # when already queried leaders, no need to redo that

        if lead1 == None and lead2 == None:         # neither item is already in a set
            lead1 = self.makeNewSet(item1)          # make a set with first item, and let it drop through to the next if...
            
            if item2 == None:                       # called with only one arg, no need to go further
                return lead1
            
        if (lead1 == None and lead2 != None) or (lead1 != None and lead2 == None):
                                                    # one exists one is new, add the new to the exists and return the correct leader
            if lead1 != None: 
                self.leaderManager.addToSet(item2, lead1)
                return lead1
            else:
                self.leaderManager.addToSet(item1, lead2)
                return lead2
        
        elif lead1 != lead2:                        # both are already in a set, have to merge them
            
            return self.leaderManager.mergeSets(lead1, lead2, self.lazy)
    
    def getNumSets(self):
        return self.leaderManager.getNumSets()
    
    def getSets(self):
        return self.leaderManager.getSets()
        
    def len(self):
        return self.leaderManager.len()
        
# example wrapper class that could be used to encapsulate a node, then just use the DefaultSetDataManager below
class Node:
    def __init__(self, n):
        self.n = n
        self.leader = None
    
    def __str__(self):
        if self.leader == None:
            ld = 'None'
        elif self.leader.n == self.n:
            ld = 'Self'
        else:
            ld = str(self.leader.n)
        return str(self.n) + ' (ldr='+ld+')'

class DefaultSetDataManager:
    
    def __init__(self):
        self.sets = dict()
        self.dataMap = dict()
    
    def __str__(self):
        return str(self.sets)
        
    def newSet(self, item):
        node = Node(item)               # create a Node to wrap the item
        self.dataMap[item] = node       # keep its reference
        
        newSet = [node]                 # start a new list for the set
        self.sets[node] = newSet
        self.setLeader(node, node)
        
        return node                     # return the leader (which is itself)
    
    def _getNode(self, item, createIfMissing=False):
        if isinstance(item, Node):
            return item
        else:
            ret = self.dataMap.get(item)
            if ret == None and createIfMissing:
                ret = Node(item)
                self.dataMap[item] = ret
            return ret
            
    def addToSet(self, item, leader):
        item = self._getNode(item, True)    # make it if it's not there        
        leader = self._getNode(leader)
        
        self.sets.get(leader).append(item)
        self.setLeader(item, leader)
    
    def mergeSets(self, lead1, lead2, lazy):
        keepSet = self.sets.get(lead1)
        updateSet = self.sets.get(lead2)
        
        if len(keepSet) > len(updateSet):
            self.sets.pop(lead2)            # remove the set that is smaller from the dict
            keepLead = lead1
            self.setLeader(lead2, keepLead)
        else:
            swap = updateSet
            updateSet = keepSet
            self.sets.pop(lead1)
            keepSet = swap
            keepLead = lead2
            self.setLeader(lead1, keepLead)
    
        if not lazy:
            assert keepSet != updateSet, 'ERROR, keepset and updateset are the same, inf loop now...'
            
            while not len(updateSet) == 0:      # update each item in the update set
                item = updateSet.pop()
                keepSet.append(item)
                self.setLeader(item, keepLead)  # update the leader
            
        return keepLead
    
    def setLeader(self, item, leader):
        item = self._getNode(item)
        leader = self._getNode(leader)

        item.leader = leader

    def getLeader(self, item):
        item = self._getNode(item)
        
        if item == None:
            return None
        else:
            return item.leader

    def getNumSets(self):
        return len(self.sets)
        
    def getSets(self):
        # big warning here! if unions have been lazy, the sets are probably inconsistent, it would be necessary to walk all the leaders up and update them properly first
        return self.sets

    def len(self):
        return len(self.dataMap)
        
class ArrayMappedSetDataManager:
    # see example of using this way in w2_asgn2_find_max_clustering.py
    
    def __init__(self, loaded):
        self.loaded = loaded                # the raw numbers, indexed by their own values
        self.leaders = [None]*len(loaded)   # the corresponding leader settings, indexed by the loaded array numbers (indexes)
        self.setCounts = [None]*len(loaded) # another correspoding array, whichever is the leader has the number of items as the data
        self.numSets = 0
        
    def __str__(self):
        return 'array len='+str(len(self.loaded))
        
    def newSet(self, item):

        self.leaders[item] = item
        self.setCounts[item] = 1
        #if item == 6013968: print 'new set/leader=%d setCount=%s' % (item, str(self.setCounts[item]))
        self.numSets += 1
        return item                         # return the leader (which is itself)

    def addToSet(self, item, leader):
        self.setLeader(item, leader)
        #if item == 6013968: print 'got type error in add to set leader=%d setCount=%s' % (leader, str(self.setCounts[item]))
        self.setCounts[leader] += 1
        
    def mergeSets(self, lead1, lead2, lazy):
        # for now, only doing lazy 
        if self.setCounts[lead1] > self.setCounts[lead2]:
            keepSet = lead1
            self.setLeader(lead2, lead1)
            self.setCounts[lead1] += self.setCounts[lead2]
            #if lead2 == 6013968: print 'merging lead2=%d set to leader=%d setCount=%s' % (lead2, lead1, str(self.setCounts[lead2]))
            self.setCounts[lead2] = None
        else:
            keepSet = lead2
            self.setLeader(lead1, lead2)
            self.setCounts[lead2] += self.setCounts[lead1]
            #if lead1 == 6013968: print 'merging lead1=%d set to leader=%d setCount=%s' % (lead1, lead2, str(self.setCounts[lead1]))
            self.setCounts[lead1] = None

        self.numSets -= 1
            
        return keepSet
        
    def setLeader(self, node, leader):
        self.leaders[node] = leader

    def getLeader(self, item):
        return self.leaders[item]

    def getNumSets(self):
        return self.numSets
        
    def getSets(self):
        raise ValueError, 'getSets not supported by ArrayMappedSetDataManager'

    def len(self):
        raise ValueError, 'len not supported by ArrayMappedSetDataManager'
        
def printUnionFind(uf):
    if uf.len() == 0:
        print 'union find is empty'
    else:
        print 'union find sets:'
        for k,lv in uf.getSets().items():
            print '    leader = %s' % str(k)
            for it in lv:
                print '        %s' % str(it)
    
def runTests():
    test1 = ['one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten']
    
    print 'add a bunch of strings all in own sets'
    uf = UnionFind()
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
    print 'running union find tests'
    runTests()

import math, utils

"""
Home grown heap implementation, supports extractMin/Max, delete, key/priority update and fast lookup of an item (also by added optional lookup key)
When calling pop() pass a function if need to do tie breaking... see pop()
    
    Note on structure: internally each entry is an array[3] comprising the key in the heap, the object being stored and an optional lookup.
    To facilitate fast deletes and lookups, the item's index into the heap is stored in a dictionary using the lookup passed in push(), if no lookup is passed, the object itself
    is used. 
    There is no dependence on the value of an object, it can be changed outside of the heap and the heap *should* not be affected, passing a lookup is optional, 
    but recommended if the item itself might be changed in a way that affects its hashing contract for dictionary lookup.
    The key/priority is set by the caller and can be changed without problems.
    
    API:
    
    Heap()          : optional param <extractMax=True> if want to reverse the order to track and extract the max instead of min
    
    push()          : optional param <lookup> if want to be able to be able to reference the item by something other than itself. see lookup()
    
    lookup()        : returns the item associated with the lookup when stored, see push()
    
    pop()           : optional param <tieBreaker> if there can be duplicate key items, pass the name of a function to break ties, see end of file for example
    
    delete()        : deletes the item associated with the lookup param, or if not associated with a lookup pass the item itself in the param, see push()
    
    updateKey()     : changes the key/priority of the item associated with the lookup param, or if not associated with a lookup, the item itself
    
    peek()          : peek the top entry, no tie breaking, so if pop() with tie breaking could return a different item than without tie breaking, don't use peek() as it will be unreliable
    
    len()           : the number of entries in the heap
    
    isEmpty()       : as it says
    
    updateBetter()  : conditionally updates an item's key/priority only if the value passed in is better than what it has already
                      returns True if the key was updated as a result
    
    Additional functions for testing/debugging:
    
    __main__()      : runs tests when run script directly from command line
    
    printHeapTree() : prints out the heap as a tree
    
    runTests()      : runs all the tests (lots of output, pipe to a file for easier browsing of results)
    
    testTieBreakDupes() : function used to test tie breaking when pop() dupes
"""
class Heap:
    KEY_IDX = 0
    ITEM_IDX = 1
    LOOKUP_IDX = 2
    
    def __init__(self, extractMax=False):
        self.heap = []
        self.indexLookup = dict() # independent of the heap, its purpose it to track the index to the array for any item, so it can be grabbed fast
        self.extractMax = extractMax

    def __str__(self):
        return str(self.heap)
        
    def push(self, item, key, lookup=None):
        
        if lookup != None and self.indexLookup.has_key(lookup):
            raise ValueError('Heap.push: duplicate lookup not supported')
        
        if lookup == None:
            if self.indexLookup.has_key(item):
                raise ValueError('Heap.push: duplicate entry, use a separate lookup')
            else:
                lookup = item
        
        if self.extractMax:                         # reverse sign if extract max
            key *= -1
        
        # doing an insert, add to end and bubble up till heap property is restored
        entry = [key, item, lookup]
        self.heap.append(entry)
        self._bubbleUp(entry, len(self.heap) -1) # idx -1 = end
        
    def _bubbleUp(self, entry, i):
        if i != 0:                                  # unless already at the root
            
            parent = ((i + 1) / 2) -1               # trunc the index of the parent, finally also -1 because index from 0
            
            while self.heap[parent][Heap.KEY_IDX] > self.heap[i][Heap.KEY_IDX]:
                
                parentEntry = self.heap[parent]     # grap the parent
                self.heap[parent] = self.heap[i]    # put the entry in parent's place
                self.heap[i] = parentEntry          # swap the parent down
                self._updateLookup(parentEntry, i)  # update the parent's lookup index if there was one

                i = parent                          # update index to new position
                
                if i == 0:                          # break out of the loop if at the root
                    break;
                
                parent = ((i + 1) / 2) -1           # find next parent

        self._updateLookup(entry, i)                # add/update lookup entry

    def _bubbleDown(self, entry, idx):
        parent = idx + 1                            # the multiply to get children only works if start at 1

        minChild = None                             # find the child with the least value        
        for i in range(parent * 2 -1, parent * 2 +1):
            if i >= len(self.heap): break;
            
            if minChild == None or minChild[Heap.KEY_IDX] > self.heap[i][Heap.KEY_IDX]:
                minChild = self.heap[i]
        
        # swap the entry with the least key child if that child's key is < the entry's
        if minChild != None and minChild[Heap.KEY_IDX] < self.heap[idx][Heap.KEY_IDX]:
            childIdx = self.indexLookup.get(minChild[Heap.LOOKUP_IDX])
            self.heap[childIdx] = entry             # swap the entry into the child's pos
            self._updateLookup(entry, childIdx)        # update that lookup
            self.heap[idx] = minChild               # put the child where this entry was
            self._updateLookup(minChild, idx)          # and update that lookup too
            
            self._bubbleDown(entry, childIdx)       # recurse till drops out            
    
    def _updateLookup(self, entry, i):
        self.indexLookup[self.heap[i][Heap.LOOKUP_IDX]] = i
        
    def lookup(self, lookup):
        i = self.indexLookup.get(lookup)
        if i != None:
            try:
                return self.heap[i][Heap.ITEM_IDX]
            except IndexError:
                print 'heap.lookup: ERROR with lookup indexing, lookups and indices are inconsistent lookup=\'%s\', index=%d' % (lookup, i)
                return None
        else:
            return None
        
    def pop(self, tieBreaker=None):
        entry = self._delete(0)
        
        # break ties by popping them all off into a list and choosing the one to go, then push the remaining ones back onto the heap
        if tieBreaker != None and len(self.heap) != 0:
            key = entry[Heap.KEY_IDX]
        
            if self.heap[0][Heap.KEY_IDX] == key:
                dupes = []
                while len(self.heap) != 0 and self.heap[0][Heap.KEY_IDX] == key:
                    dupes.append(self._delete(0))
                    
                if utils.Debug.debug: print 'FOUND %d duplicate key entries, using tieBreaker to return the correct one' % len(dupes)
                
                while len(dupes) != 0:
                    dupe = dupes.pop()
                    winner = tieBreaker(entry, dupe)
                    if winner == entry:
                        self.push(dupe[Heap.ITEM_IDX], dupe[Heap.KEY_IDX], dupe[Heap.LOOKUP_IDX])
                    else:
                        self.push(entry[Heap.ITEM_IDX], entry[Heap.KEY_IDX], entry[Heap.LOOKUP_IDX])
                        entry = winner
        
        return entry[Heap.ITEM_IDX]

    def delete(self, lookup):                          # delete works with lookup, or if none was supplied, then the item itself
        i = self.indexLookup.get(lookup)
        if i == None:
            raise ValueError('Heap.delete: no matching entry found, locates by lookup or if none, then the item is the lookup')

        return self._delete(i)[Heap.ITEM_IDX]

    def _delete(self, i):
        if len(self.heap) == 0:
            return None
	    
        entry = self.heap[i]
        
        self.indexLookup.pop(entry[Heap.LOOKUP_IDX])# delete from lookups
        
        if i == len(self.heap) -1:                  # last entry, not much to do
            self.heap.remove(entry)
        else:
            lastEntry = self.heap[-1]               # overwrite the first entry with the last
            self.heap.remove(lastEntry)             # remove the last entry first
            self.heap[i] = lastEntry                # swap it into the first position
            self._updateLookup(lastEntry, i)        # update the lookup as there might not be any changes from bubbling down
            self._bubbleDown(lastEntry, i)          # bubble it down to where the heap property is restored
            
        return entry

    def updateKey(self, lookup, key):
        i = self.indexLookup.get(lookup)
        if i == None:
            raise ValueError('Heap.updateKey: no matching entry found, locates by lookup or if none, then the item is the lookup')

        self._updateKey(i, key)

    def _updateKey(self, i, key):            
        if self.extractMax:                         # extract max need to reverse sign of key
            key *= -1

        entry = self.heap[i]
        oldP = entry[Heap.KEY_IDX]
        entry[Heap.KEY_IDX] = key
        
        if key > oldP:
            self._bubbleDown(entry, i)              # bubble it down to where the heap property is restored
        elif key < oldP and i > 0:                  # might need to bubble up
            self._bubbleUp(entry, i)
    
    def updateBetter(self, lookup, key):
        # conditionally does an update of the key, only if the value is improved
        i = self.indexLookup.get(lookup)
        if i == None:
            raise ValueError('Heap.promoteBetter: no matching entry found, locates by lookup or if none, then the item is the lookup')

        testKey = key                               # the update method also reverses the sign, so use a temp var
        if self.extractMax:                         # extract max need to reverse sign of key
            testKey *= -1
            
        if self.heap[i][Heap.KEY_IDX] > testKey:
            self._updateKey(i, key)
            return True
        else:
            return False
        
    def peek(self):
        if len(self.heap) == 0:
            return None
            
        _,item,_ = self.heap[0]
        
        return item
	
    def len(self):
        return len(self.heap)

    def isEmpty(self):
        return len(self.heap) == 0

def _printChildren(heap, parent, showDetails):
    parent = parent + 1 # the multiply to get children only works if start at 1

    for i in range(parent * 2 -1, parent * 2 +1):
        if i >= len(heap): break;
        
        line = '     ' * int(math.log(i+1,2))
        if showDetails:
            print '%s|--- %s [%s lookup=\'%s\']' % (line, str(heap[i][0]), str(heap[i][1]), str(heap[i][2]))
        else:
            print '%s|--- %s' % (line, str(heap[i][0]))
        _printChildren(heap, i, showDetails)
    
def printHeapTree(heap, showDetails=False):
    if len(heap) == 0:
        print 'heap is empty'
    else:
        print 'heap tree:'
        if showDetails:
            print '     %s [%s lookup=\'%s\']' % (str(heap[0][0]), str(heap[0][1]), str(heap[0][2]))
        else:
            print '     %s' % str(heap[0][0])
        _printChildren(heap, 0, showDetails)
    
def runTests(heap):
    print 'add a bunch of strings with numbers and lookups'
    test1 = [[1, 'one', 'one'],[6, 'six', 'six'],[4, 'four', 'four'],[9, 'nine', 'nine'],[0, 'zero', 'zero'],[12, 'twelve', None],[2, 'two', None],[3, 'three', None],[8, 'eight', None],[7, 'seven', None],[-2, 'minus two', None],[-2, 'minus two 2', 'dupe2'],[-2, 'minus 22', 'tutu']]
    for p,s,k in test1:
        heap.push(s,p,k)
        print 'heap after adding %s (key=%s)' % (s,str(p))
        #print '        %s' % (str(heap))
        printHeapTree(heap.heap)
        #utils.waitForInput()

    print 'test popping the items'
    while not heap.isEmpty():
        s = heap.pop()
        print 'heap after popping %s' % (s)
        printHeapTree(heap.heap)
        #utils.waitForInput()

    print 'test re-adding all the items again'
    for p,s,k in test1:
        heap.push(s,p,k)
    printHeapTree(heap.heap)

    print 'test deleting a few of them'
    print 'deleting first entry (same as pop)'
    heap.delete('minus two')
    printHeapTree(heap.heap)

    print 'delete three'
    heap.delete('three')
    printHeapTree(heap.heap)
    
    print 'attempt deleting three (same again)'
    try:
        heap.delete('three')
        print 'ERROR: didn\'t raise value error....!'
    except ValueError: 
        print 'got expected value error'        
    print 'heap now:        %s' % (str(heap))
    
    print 'delete 2nd to last entry (nine)'
    heap.delete('nine')
    printHeapTree(heap.heap)
    print 'heap now:        %s' % (str(heap))
    
    print 'delete last entry (eight)'
    heap.delete('eight')
    printHeapTree(heap.heap)

    print 'test updateBetter'
    for i in range(1, 5):
        if heap.extractMax:
            lookup = 'zero'
            newKey = i * 3
        else:
            lookup = 'twelve'
            newKey = 12 - i * 4
        
        print '     change key for \'%s\' to %d' % (lookup, newKey)
        heap.updateBetter(lookup, newKey)
        printHeapTree(heap.heap, True)
        #utils.waitForInput()
                        
    print 'test updating just the lower value priorities to their minus values'
    for p,s,k in test1:
        if p > 3:
            newP = p * -1
            lookup = None
            if k != None and heap.lookup(k) != None:
                lookup = k
            elif k == None and heap.lookup(s) != None:
                lookup = s

            if lookup != None:
                print 'updating key for \'%s\' to %d' % (s, newP)
                heap.updateKey(lookup, newP)
                printHeapTree(heap.heap, True)
                #utils.waitForInput()
    
    
    print 'test lookups return the items'
    for p,s,k in test1:
        if k != None:
            print '    heap get value for lookup %s = %s' % (k, heap.lookup(k))
        else:
            print '    heap get value for itself %s = %s' % (s, heap.lookup(s))
    
    if not heap.extractMax:
        while not heap.isEmpty():
            heap.pop()
        print 'test pop duplicate entries w/o tiebreaking'
        for p,s,k in test1: 
            if p == -2:
                heap.push(s,p,k)
        print '    heap before pop anything, pop dupes in arbitrary order'
        printHeapTree(heap.heap, True)
        while not heap.isEmpty():
            print '        popped %s' % heap.pop()

        print 'test pop duplicate entries w tiebreaking'
        for p,s,k in test1: 
            if p == -2:
                heap.push(s,p,k)
        print '    heap before pop anything, pop dupes in tie break order (alphabetical)'
        printHeapTree(heap.heap, True)
        while not heap.isEmpty():
            print '        popped %s' % heap.pop(testTieBreakDupes)

def testTieBreakDupes(entry1, entry2):
    if entry1[Heap.ITEM_IDX] > entry2[Heap.ITEM_IDX]:
        winner = entry2
    else:
        winner = entry1
    print 'test tie break dupe %s v %s, winner is %s' % (str(entry1), str(entry2), str(winner))
    return winner
        
if __name__ == '__main__':
    
    utils.Debug.debug = True
    print 'running heap tests using extract max'
    heap = Heap(True)
    runTests(heap)
    print 'running heap tests'
    heap = Heap()
    runTests(heap)

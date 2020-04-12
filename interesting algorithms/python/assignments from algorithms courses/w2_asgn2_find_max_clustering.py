import sys, time, utils, unionFindUtils

"""
Kruskal's max spacing clustering algorithm 
This is for the w2 2nd programming assignment where the task is to find the max number of clusters
where the max spacing is 3. In other words, cluster anything of length < 3 and the number of clusters
remaining is the answer
"""

def makeHammingPermutations():
    hPerms = [] 
    # create the 300 possible numbers which are results of XORing 2 24-bit numbers which produce either 1 or 2 hamming distance
    # could take the input param and use it to construct the appropriate bit length numbers, but not sure how far can go before the number of lookups becomes too many
    for exp1 in xrange(24): # exponent of the first number
        num1 = 2**exp1
        hPerms.append(num1)
        
        if utils.Debug.debug: print 'appended %s' % str(bin(num1))
        
        for exp2 in range(0, exp1):
            num2 = num1 + 2**exp2
            hPerms.append(num2)

            if utils.Debug.debug: print 'appended %s' % str(bin(num2))
    
    hPerms.sort()
    
    if utils.Debug.debug: print 'num permutations = %d' % len(hPerms)
    
    """
    hMaxos = [None]*len(hPerms)
    for i in xrange(len(hPerms)):
        z = '0'*(hPerms[i].bit_length()-1)
        hMaxos[i] = int('0b1' + z, 2)
        if utils.Debug.debug: print 'hmaxo for %s is %s XOR is greater=%s' % (str(bin(hPerms[i])), str(bin(hMaxos[i])), str(hPerms[i] < hMaxos[i] ^ hPerms[i]))
    
    return (hPerms, hMaxos)
    """
    return hPerms
    
def submitDataFile(hint, fname, maxSpacing):
    submitDataArray(hint, fname, utils.readInputFile(fname), maxSpacing)

def submitDataArray(hint, source, lines, maxSpacing):
    
    prevTime = time.clock()
    
    #hamPerms, hMaxos = makeHammingPermutations()
    hamPerms = makeHammingPermutations()
    
    firstLine = None
    keys = None
    keysIdx = 0
    for i in xrange(len(lines)):              # pre-process 1, store all the keys
        l = lines[i]
        if firstLine == None:
            firstLine = map(int, l.split())
            assert firstLine[1] == 24, 'only length 24 bits labels supported, file bits=%d' % (firstLine[1])
            numNodes = firstLine[0]
            keys = [None]*numNodes
        else:
            j = l.replace(' ', '')
            assert len(j) == firstLine[1], 'label for j=%s is not length bits=%d' % (j, firstLine[1])
            n = int('0b'+j, 2)                  # slap a '0b' on the front of it to make a binary string and convert it to an int
            lines[i] = n                        # make that the array value to save doing it again next time
            keys[keysIdx] = n
            keysIdx += 1

    prevTime = utils.reportTime('store keys', prevTime)
    keys.sort()
    prevTime = utils.reportTime('sort keys', prevTime)
    
    loaded = [None]*(keys[-1]+1)                # instead of dict lookup, have an array big enough so any key is its index 
    uf = unionFindUtils.UnionFind(leaderManager=unionFindUtils.ArrayMappedSetDataManager(loaded), lazy=True, compressPaths=False) # w compress paths it's a bit slower for this data... could try to have sets managed outside the class too for another approach

    for n in lines[1:]:
        loaded[n] = n

    prevTime = utils.reportTime('init load completed', prevTime)
    
    """
    breakouts = dict() whole idea of breaking out of the loop is nice, but the cost of checking adds more than the reduction in loop times 
                idea is: there's a max h that when xor with n will produce a number that's too large to satisfy o < n
    """
    # was nice to do it all in one read through, but actually, sorting it shaves another 3 secs off the timing
    lastI = None
    for n in keys:
        if n == lastI: continue                 # ignore dupes
        lastI = n
        lead1 = None                            # postpone creating a cluster until know it didn't get included in some other one, this shaved another 1.5 secs off the timing (11.8 secs)

        #if utils.Debug.debug: print 'added %s as int=%d' % (bin(n)[2:], n)

        #j = -1 see breakouts idea
        for h in hamPerms:        # loop through all the hamming results to find complimentary numbers that make them
            #j += 1
            o = n ^ h
            
            if (o < n):                         # benefit of sorting, now do fewer checks - only for when it's earlier in the list
                od = loaded[o]
                
                if od != None:                  # found one previously loaded
                    lead2 = uf.find(od, False) 
                    
                    if lead2 != lead1:          # and it isn't already in the same cluster (first time it finds one it can't be in same cluster)
                        #if utils.Debug.debug: print '   found other node within hamming distance (%d) %s as int=%d' % (bin(o^n).count('1'), bin(o), o)
                        lead1 = uf.unionWithLeaders(n, od, lead1, lead2)
                """ 
            else:
                if hMaxos[hi] > n: 
                    prev = breakouts.get(j)
                    if prev != None: prev += 1
                    else: prev = 1
                    breakouts[j] = prev
                    break             # only need numbers that can produce results < n
                """ 

        if lead1 == None:                       # didn't find anything to cluster with, create a cluster of its own 
            uf.makeNewSet(n)


    """
    for j,c in breakouts.items():
        print 'broke out of h loop with j=%d count=%d' % (j, c)
    """

    prevTime = utils.reportTime('processed data', prevTime)
    
    # first line is number of nodes and number of edges (lines to follow), the rest are pairs of integers weight, length
    print 'num clusters = %s, source \'%s\', #nodes=%d, #bits for label=%d, hint=%s' % (uf.getNumSets(), source, firstLine[0], firstLine[1], hint)

if __name__ == '__main__':
    if len(sys.argv) == 2:
        if sys.argv[1] == 'debug':
            utils.Debug.debug = True
        elif sys.argv[1] != 'real':
            print('usage: real')
            sys.exit()    

        submitDataFile('expected answer is 6118', 'w2_clustering_big.txt', 3)
    else:
        utils.Debug.debug = True
        submitDataArray('expected result: 2 clusters', 'test1', ['2 24','0 1 1 0 0 1 1 0 0 1 0 1 1 1 1 1 1 0 1 0 1 1 0 1','0 1 0 0 0 1 0 0 0 1 0 1 1 1 1 1 1 0 1 0 0 1 0 1'], 3)
        utils.Debug.debug = False
        submitDataArray('expected result: 2 clusters', 'test2', ['2 24','1 1 1 0 0 1 1 0 0 1 0 1 1 1 1 1 1 0 1 0 1 1 0 1','0 1 0 0 0 1 0 0 0 1 0 1 1 1 1 1 1 0 1 0 0 1 0 1'], 3)
        submitDataArray('expected result: 1 clusters', 'test3', ['3 24','0 1 1 0 0 1 1 0 0 1 0 1 1 1 1 1 1 0 1 0 1 1 0 1','0 1 1 0 0 1 1 0 0 1 0 1 1 1 1 1 1 0 1 0 1 1 0 0','1 0 1 0 0 1 1 0 0 1 0 1 1 1 1 1 1 0 1 0 1 1 0 0'], 3)
        submitDataFile('expected result: 989 clusters', 'w2_clustering_big_1000.txt', 3)
        submitDataFile('expected result: 9116 clusters', 'w2_clustering_big_10000.txt', 3)

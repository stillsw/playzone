import sys, utils

"""
Knapsack dynamic prog algorithm 
Approach for this try is to process the values in ranges (start weight, value) instead of one weight at a time, 
pros/cons:
it's much more data effiecient
it's quicker on large data sets (assignment data in 8mins)
it's slower on small data sets
it's tricky to understand and code
"""
    
def submitDataFile(hint, fname):
    submitDataArray(hint, fname, utils.readInputFile(fname))

def submitDataArray(hint, source, lines):
    
    W = int(lines[:1][0].split()[0])
    
    if utils.Debug.debug: 
        oldA = []
        oldA.append([0]*(W+1))
        ai = 1
        
    # mimic original logic, walk through the weights forwards and just use the ranges instead of individual
    # values, just keep 2 rows
    A = [[],[(0, 0)]] # 2 rows of tuples = position value is effective from and value, the first will be popped in the first loop iteration
    
    print 'knapsack capacity = %d' % (W)
    
    VW = []
    
    for i in xrange(1, len(lines)):
        l = lines[i]
        vw = map(int, l.split())
        VW.append([vw[1], vw[0]])
    
    VW.sort()

    for vw in VW:
        v = vw[1]
        w = vw[0]
        
        if w > W:       # if it blows the capacity on its own, nothing to do
            if utils.Debug.debug: print 'reject w > W'
            continue
        elif utils.Debug.debugAndWait: 
            print 'v = %d, w = %d' % (v, w)

        A.pop(0)        # remove the first row 
        A.append([])    # append a new row to receive the values
        
        xi = 0          # index into the current tuple in A
        
        while xi < len(A[0]):
            
            rangeStart, accumVal = A[0][xi]         # read the next tuple
            
            #if (v,w) == (35239,95) and (rangeStart, accumVal) == (297, 315998):
            #    import pdb; pdb.set_trace()

        
            # determine the end of the range, it is inclusive, either 1 less than the next entry, or the max W
            if xi == len(A[0]) - 1:
                rangeEnd = W
            else:
                rangeEnd = A[0][xi + 1][0] - 1
            
            if w > rangeEnd:                        # range is before this item features
                A[1].append((rangeStart, accumVal)) # so just copy the values to the new row
                xi += 1
                continue
        
            # in range
            
            # compile a lifo list of tuples where they reach from this one's range when w is added
            affectedList = None
            
            writeThisFirst = True                   # by default will write out the current row first, but could be unset when visit prev range below
                
            pXi = xi - 1
            while pXi >= 0:
                pa = A[0][pXi]
                pVal = pa[1]
                
                if pVal + v <= accumVal:            # stop when get to a val that won't insert anyway, the values only get smaller previous to that
                    break
                
                pStart = pa[0]
                pEnd = A[0][pXi + 1][0] - 1         # end is the corresponding next tuple's start - 1
                
                if pEnd >= rangeStart - w:          # add any within reach of start - w
                    affectiveStart = max(pStart, max(rangeStart, w) - w)
                    
                    if affectiveStart + w <= rangeEnd:  # provided don't go past the end of the current range
                        if affectedList == None:        # lazy init to prevent doing it every time unnecessarily
                            affectedList = [(pStart, pEnd, pVal)]
                        else:
                            affectedList.append((pStart, pEnd, pVal))

                        # rollback to get the earliest point to figure out if need to write the current range out first
                        # if the range start is exactly the same the current range is not needed
                        if writeThisFirst and pVal + v > accumVal:  # but only if the value would be higher (ie. would overwrite)
                            writeThisFirst = affectiveStart + w != rangeStart
            
                pXi -= 1
                
            if writeThisFirst:
                A[1].append((rangeStart, accumVal))
            
            # process the affected list
            while affectedList != None and len(affectedList) != 0:
                aStart, aEnd, aVal = affectedList.pop()

                # only care about anywhere the new value is greater than the current one
                if aVal + v > accumVal:
                    # rollback to get the earliest point
                    affectiveStart = max(aStart, max(rangeStart, w) - w)

                    # roll forwards to find where to create a new range
                    A[1].append((affectiveStart + w, aVal + v))
        
            # finally, may also need to create a new range which splits the current one, if it's at least as wide as w
            if rangeEnd - rangeStart >= w:
                A[1].append((rangeStart + w, accumVal + v))
            
            xi += 1
        

        # parallel run the old way to check correct
        if utils.Debug.debug and False:
            # first do integrity check, no dupe range starts and all must be in sequence
            rs = [-1]
            for dr,dv in A[1]:
                if dr < rs[-1]:
                    print 'ERROR: v=%d,w=%d start range out of sequence rangeStart=%d, previous %d, A=%s' % (v,w,dr,rs[-1],str(A))
                    sys.exit()
                if dr == rs[-1]:
                    print 'ERROR: v=%d,w=%d start range dupe rangeStart=%d, previous %d, A=%s' % (v,w,dr,rs[-1],str(A))
                    sys.exit()
                    
                rs.append(dr)

            oldA.append([0]*(W+1))
            for x in xrange(0, W+1):
                if x < w:
                    oldA[ai][x] = oldA[ai-1][x]
                else:
                    oldA[ai][x] = max( oldA[ai-1][x] , oldA[ai-1][x-w] + v )

            ai += 1
        
            # data check old way with new
            for dr,dv in A[1]:
                if dr >= len(oldA[-1]):
                    print 'ERROR: v=%d,w=%d mismatch not even enough values for that index=%d new says %d, A=%s' % (v,w,dr,dv,str(A))
                    sys.exit()
                elif oldA[-1][dr] != dv:
                    print 'ERROR: v=%d,w=%d mismatch oldA[%d]=%d new says %d, A=%s' % (v,w,dr,oldA[-1][dr],dv,str(A))
                    if dr > 50: print '    too many values to easily see'
                    else:
                        print '    oldA[upto %d] = %s' % (dr, oldA[-1][:dr])
                    sys.exit()
            
        
            if utils.Debug.debugAndWait: 
            
                print 'v=%d, w=%.2f: all the rows of oldA' % (v, w)
                for row in oldA[-1:]:
                    print row[:25]
                    
                print 'the new look for A'
                print A[-1]
                
                #utils.waitForInput()

    # results
    print 'result = %s, source \'%s\', hint=%s' % (A[1][-1][1], source, hint)

if __name__ == '__main__':
    if len(sys.argv) == 3:
        if sys.argv[1] == 'debug':
            utils.Debug.debug = True
        elif sys.argv[1] != 'real':
            print('usage: real')
            sys.exit()    

        if sys.argv[2] == 'q1':
            submitDataFile('answer is 2493893', 'w3_knapsack1.txt') # question 1
        else:
            submitDataFile('answer is 4243395', 'w3_knapsack_big.txt') # question 2
    else:
        utils.Debug.debugAndWait = True
        utils.Debug.debug = True
        utils.Debug.debugAndWait = False
        submitDataArray('expected result: 8', 'test1', ['6 4','3 4','2 3','4 2','4 3'])
        submitDataArray('expected result: 12', 'test2', ['15 5','1 6','2 8','3 12','4 2','5 1'])
        submitDataArray('expected result: 11', 'test3', ['14 5','1 6','2 8','3 12','4 2','5 1'])
        submitDataFile('answer is 1398904', 'w3_knapsack1-capacity3000.txt') # question reduced capacity of the knapsack
        utils.Debug.debug = False


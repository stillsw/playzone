import sys, utils

"""
Knapsack dynamic prog algorithm 
Approach for this try is to process the backwards so only have one array
pros/cons:
it's more data effiecient
it's quicker since memory could be close/contiguous and less of it, (assignment data takes 20mins)
there's no reconstruction possible this way

NOTE: same as non-optimized file, since someone noticed gcd=367 on the data, this now runs in 2.9 seconds
"""
    
def submitDataFile(hint, fname, gcd=None):
    submitDataArray(hint, fname, utils.readInputFile(fname), gcd)

def submitDataArray(hint, source, lines, gcd=None):
    
    W = int(lines[:1][0].split()[0])
    gcdInfo = 'None'    
    if gcd != None:
        W /= gcd
        gcdInfo = str(gcd) + ' so real capacity=' + str(gcd * W)

    print 'knapsack capacity = %d, (gcd=%s) ' % (W, gcdInfo)
    
    # make a 2D array big enough for all values and all weights, init all zero on first line
    A = [0]*(W+1)                   # NOTE : ignore first entry and go to W+1, I think because weights are >1, not an indexed number from 0
                                    # which has repercussions for the compute loop
    
    for i in xrange(1, len(lines)):
        l = lines[i]
        vw = map(int, l.split())
        v = vw[0]
        w = vw[1]
        if gcd != None: w /= gcd
        
        if w > W:       # if it blows the capacity on its own, nothing to do
            if utils.Debug.debug: print 'reject w > W'
            continue
        elif utils.Debug.debug: 
            print 'v = %d, w = %d' % (v, w)

        for fx in xrange(W+1):      # NOTE: compute loop, see note above, why this line is W+1 and the next one doesn't do -1 as would usually to index backwards
            x = W - fx              # go backwards so can avoid needing 2x array
            if x >= w:
                if utils.Debug.debug: print '   line=%d, x=%d, comparing Ax=%s with Ax-w+v=%s' % (i, x, A[x], A[x-w]+v )
                A[x] = max( A[x], A[x-w] + v )

    # results
    print 'result = %s, source \'%s\', hint=%s' % (A[-1], source, hint)

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
            submitDataFile('answer is 4243395', 'w3_knapsack_big.txt', gcd=367) # question 2
    else:
        submitDataArray('expected result: 8', 'test1', ['6 4','3 4','2 3','4 2','4 3'])
        #submitDataArray('expected result: 8', 'test1, optimized', ['6 4','3 4','2 3','4 2','4 3']) # try optimized
        #submitDataArray('expected result: 12', 'test2', ['15 5','1 6','2 8','3 12','4 2','5 1'])
        utils.Debug.debug = True
        utils.Debug.debug = False
        #submitDataArray('expected result: 11', 'test3', ['14 5','1 6','2 8','3 12','4 2','5 1'])
        submitDataFile('answer is 1398904', 'w3_knapsack1-capacity3000.txt') # question reduced capacity of the knapsack


import sys, utils

"""
Knapsack dynamic prog algorithm 
Approach for this try is to process the values exactly as described in the lectures (bottom-up and using memoization)
pros/cons:
it's slow on large data sets (assignment data in 25mins, depending if arrays are lines x weights or weights x lines, which I presume is memory caching efficiency)
optionally optimized param will only use 2x array, but at the expense of being able to reconstruct the data

NOTE: someone mentioned on forum that gcd of weights was 367, so by dividing all weights by that, it now runs in 5.3secs
"""
    
def submitDataFile(hint, fname, optimize=False, gcd=None):
    submitDataArray(hint, fname, utils.readInputFile(fname), optimize, False, gcd)

def submitDataArray(hint, source, lines, optimize=False, reconstruct=False, gcd=None):  # use the fact that there is a gcd over the data (discovered in the forums)

    W = int(lines[:1][0].split()[0])
    gcdInfo = 'None'    
    if gcd != None:
        W /= gcd
        gcdInfo = str(gcd) + ' so real capacity=' + str(gcd * W)

    print 'knapsack capacity = %d, (gcd=%s) ' % (W, gcdInfo)
    
    VW = []

    for l in lines[1:]:
        vw = map(int, l.split())
        if gcd == None:
            VW.append([vw[1], vw[0]])
        else:
            w = vw[1] / gcd
            if w * gcd != vw[1]: print 'divide by gcd does not mult up again'
            VW.append([w, vw[0]])
    
    # make a 2D array big enough for all values and all weights, init all zero on first line
    A = []
    for i in xrange(0, W+1):
        if optimize:
            A.append([0, 0])
        else:
            A.append([0])
    ai = 1              # optimized index always stays at one
        
    for i in xrange(len(VW)):
        v = VW[i][1]
        w = VW[i][0]
        
        if w > W:       # if it blows the capacity on its own, nothing to do
            if utils.Debug.debug: print 'reject w > W'
            continue
        elif utils.Debug.debug: print 'v = %d, w = %d' % (v, w)

        if optimize:    # copy back the values from the 2nd row to the 1st and re-init the 2nd
            for b in range(0, W+1):
                A[b][0] = A[b][1]
                A[b][1] = 0
        else:
            ai = i + 1
            for b in xrange(0, W+1):
                A[b].append(0)


        for x in xrange(0, W+1):
            if x < w:
                A[x][ai] = A[x][ai-1]
            else:
                if utils.Debug.debug: print '   line=%d, x=%d, comparing Ax=%s with Ax-w+v=%s' % (i, x, A[x][ai-1], (A[x-w][ai-1])+v )
                A[x][ai] = max( A[x][ai-1] , A[x-w][ai-1] + v )


    # results
    print 'result = %s, source \'%s\', hint=%s' % (A[-1][-1], source, hint)

    if reconstruct:
        if optimize: print 'can\'t reconstruct optimzed solution as-is'
        else:
            testX = W
            i = len(A[testX]) -1
            while i > 0 and testX > 0:
                vw = map(int, lines[i].split())     # if not debugging, wouldn't need this here, only if the inner test succeeds
                
                if utils.Debug.debug: print '       comparing at pos testX=%d (v=%d,w=%d) with prev line (v=%d)' % (testX, A[testX][i], vw[1], A[testX][i -1])
                if A[testX][i] > A[testX][i -1]:
                    testX -= vw[1]
                    print '    solution included line %d, value = %d' % (i, vw[0])
                i -= 1

if __name__ == '__main__':
    if len(sys.argv) == 3:
        if sys.argv[1] == 'debug':
            utils.Debug.debug = True
        elif sys.argv[1] != 'real':
            print('usage: real')
            sys.exit()    

        if sys.argv[2] == 'q1':
            submitDataFile('answer is 2493893', 'w3_knapsack1.txt', optimize=True) # question 1
        else:
            submitDataFile('answer is 4243395', 'w3_knapsack_big.txt', optimize=True, gcd=367) # question 2
    else:
        utils.Debug.debug = True
        submitDataArray('expected result: 8', 'test1', ['6 4','3 4','2 3','4 2','4 3'])
        utils.Debug.debug = False
        #submitDataArray('expected result: 8', 'test1, optimized', ['6 4','3 4','2 3','4 2','4 3'], True) # try optimized
        #submitDataArray('expected result: 12', 'test2', ['15 5','1 6','2 8','3 12','4 2','5 1'], reconstruct=True)
        #submitDataArray('expected result: 11', 'test3', ['14 5','1 6','2 8','3 12','4 2','5 1'], reconstruct=True)
        #submitDataFile('answer is 1398904', 'w3_knapsack1-capacity3000.txt', gcd=367) # question reduced capacity of the knapsack
        #submitDataFile('answer is 1398904', 'w3_knapsack1-capacity3000.txt', True) # question reduced capacity of the knapsack


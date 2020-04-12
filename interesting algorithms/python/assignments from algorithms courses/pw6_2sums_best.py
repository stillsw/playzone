import sys, time, heapq
import indexForValue
from random import randint

"""
Takes an array of ints and finds the total distinct results of adding an them together that range between min and max
"""
class Globals:
    debug = False
    debugAndWait = False
    timing = False

    T_MIN = -10000
    T_MAX = 10000

"""
On the very large input, this way churns for ages... didn't expect that, use this as a figure out why method
Correction, it's fine... just I had a bug (infinite loop when deduping y)
"""
def findSums(nums):
    """
    nums is the ordered set of distinct integers from input
    """
    res = set()

    yLowerBound = Globals.T_MIN - nums[0]        # prime the y lower and upper bounds before looping
    yUpperBound = Globals.T_MAX - nums[0]

    yUpperIdx = len(nums) -1
    while nums[yUpperIdx] > yUpperBound:         # set upper bound index to point to the appropriate elements
        yUpperIdx -= 1

    if Globals.debug: print 'First num=%d bounds y=%d to y=%d, highest data value in range=%d' % (nums[0], yLowerBound, yUpperBound, nums[yUpperIdx])

    lastX = None
    for x in nums:                               # main loop, start counting up from the lowest number
        if x == lastX: continue                  # bypass dupe
        lastX = x

        yLowerBound = Globals.T_MIN - x
        yUpperBound = Globals.T_MAX - x

        if Globals.debug: print 'x = %d range y = %d ... %d' % (x, yLowerBound, yUpperBound)

        if yUpperBound < x:                      # covered all the numbers
            if Globals.debug: print 'FINISHED SUMMING - check it is correct to stop here, continuing should only do dupes, not skew results'
            return res

        while nums[yUpperIdx] > yUpperBound:     # bring the upper index down to the highest element <= to it
            yUpperIdx -= 1
            if yUpperIdx < 0:
                if Globals.debug: print 'upper bound dropped out, no more rows to find'
                return res

        if nums[yUpperIdx] < yLowerBound:
            if Globals.debug: print '     no values in range'
            continue

        if Globals.debug: print '     highest data value in range=%d' % nums[yUpperIdx]

        yIdx = yUpperIdx
        lastY = None                             # deduping y values are per value of x (eg. x = 1 try y = 2, then x = 2, try y = 2 is also good)

        # NOTE; this prevents sum of x=x which was an ambiguous spec
        while nums[yIdx] >= yLowerBound and yIdx >= 0 and nums[yIdx] > x:

            y = nums[yIdx]
            if y == lastY: 
		yIdx -= 1
                if Globals.debug: print '     ignoring dupe y=%d' % y
                continue                         # ignore dupes

            if Globals.debug: print '     trying with y=%d' % y

            lastY = y

            t = x + nums[yIdx]                   # try the sum
            if t >= Globals.T_MIN and t <= Globals.T_MAX:
                res.add(t)
            if Globals.debug: print '     sum x+y=('+str(x)+'+'+str(nums[yIdx  ])+') t='+str(t)+', in range = '+str(t >= Globals.T_MIN and t <= Globals.T_MAX)

            yIdx -= 1                            # dec the index to go around again

    return res

def reportTime(stmt, prevTime):
    now = time.clock()
    if now - prevTime > 0.0:
        print('           timing for', stmt, now - prevTime)
    return now

def readInputFile(fname):
    text_file = open(fname, "rU")
    lines = map(int, text_file.read().splitlines())
    return lines

def submitDataFile(hint, fname):
    submitDataArray(hint, fname, readInputFile(fname))

def submitDataArray(hint, source, nums):
    # each line is an integer
    print(source, 'data len', len(nums), 'hint', hint)

    nums = sorted(nums)

    print 'result %d' % len(findSums(nums))
    
if __name__ == '__main__':
    if len(sys.argv) == 2:
        if sys.argv[1] == 'debug':
            Globals.debug = True
        elif sys.argv[1] != 'real':
	    print('usage: real')
	    sys.exit()    
	submitDataFile('no hint', 'prob12sum.txt')
    else:
        #Globals.debug = True
        submitDataArray('expected result: 3', 'test1', [-10001,1,2,-10001])
        submitDataArray('expected result: 5', 'test2', [-10001,1,2,-10001,9999])
        submitDataArray('expected result: 11', 'test3', [1,1,2,3,4,6,8])
        submitDataFile('expected result:6', 'testcase1.txt')

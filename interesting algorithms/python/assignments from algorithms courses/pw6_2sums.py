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
bucket all the numbers, from post by Valentin Lavrinenko (https://class.coursera.org/algo-007/forum/thread?thread_id=573) (see notes in this folder for description):
runs in 4+ seconds
basic idea is that a number goes into one bucket (one of many, each size of target range) and so it's complementary numbers must be in the bucket that corresponds to
T min - itself or the bucket after that (since the range could span 2 buckets)
"""
def findSums(nums):
    res = set()
    w = Globals.T_MAX - Globals.T_MIN
    print 'Array min, max (%d, %d), w=%d' % (nums[0], nums[-1], w)
    
    buckets = dict()
    for x in nums:
	hi = x / w                               # hash from division
	b = buckets.get(hi)
	if b == None:                            # create bucket on the fly
	    b = []
	    buckets[hi] = b
	if b.count(x) == 0:                      # ignore dupes
            b.append(x)
    
    print 'Numbers gone into #buckets = %d, ave per bucket=%d' % (len(buckets), len(nums) / len(buckets))
    
    # again loop through nums to find sums
    for x in nums:
	if Globals.debug: print '     x = %d' % x
	
	hi = (Globals.T_MIN - x) / w             # hash from division
	for bi in range(hi, hi + 2):             # +2 so goes to +1 bucket
	    b = buckets.get(bi)
	    if b == None:                        # no bucket, no interesting values
		continue
	    
	    for y in b:
		if y == x: continue
		
		t = x + y
		if Globals.debug: print '          x=%d y=%d t=%d, in range = %s' % (x, y, t, str(t >= Globals.T_MIN and t <= Globals.T_MAX))
		if t >= Globals.T_MIN and t <= Globals.T_MAX:
		    res.add(t)    
    
    return res

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

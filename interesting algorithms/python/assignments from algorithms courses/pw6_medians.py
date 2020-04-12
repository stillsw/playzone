import sys, time, heapq
import indexForValue
from random import randint

"""
Takes an array of ints and finds the total of the medians as they are streamed
"""
class Globals:
    debug = False
    debugAndWait = False
    timing = False

def findMedians(nums):
    hLow = Heap(True)
    hHigh = Heap()
    """
    to test the heaps work as expected, just run the following method:
    testHeaps(nums, hLow, hHigh)
    """

    sumMedians = 0
    
    for n in nums:
	if hLow.peek() == None:		# on first entry so will hHigh be, in both cases put into hLow
	    hLow.push(n)
	    sumMedians += n
	
	elif hHigh.peek() == None:	# on second entry
	    if n > hLow.peek():
		hHigh.push(n)
	    else:
		hLow.push(n)
		nMax = hLow.pop()
		hHigh.push(nMax)
		
	    sumMedians += hLow.peek()

	else:				# usually
	    if n < hLow.peek():
		hLow.push(n)
	    
	    elif n > hHigh.peek():
		hHigh.push(n)
		
	    elif hHigh.len() < hLow.len():	# in between them
		hHigh.push(n)
	    else:
		hLow.push(n)
	    
	    # now rebalance if needed
	    if hHigh.len() + 1 < hLow.len():
		nMax = hLow.pop()
		hHigh.push(nMax)

	    elif hLow.len() + 1 < hHigh.len():
		nMin = hHigh.pop()
		hLow.push(nMin)
	    
	    # median is hHigh if more there, otherwise hLow
	    if hHigh.len() > hLow.len():
		sumMedians += hHigh.peek()
	    else:
		sumMedians += hLow.peek()
		

    print 'result %d' % (sumMedians % 10000)
    

def testHeaps(nums, hLow, hHigh):    
    if Globals.debug: print 'TEST INSERTS'
    for n in nums:
	# insert all into both heaps
	hLow.push(n)
	hHigh.push(n)
	
	if Globals.debug: print '     inserted %d, peek hLow=%d, hHigh=%d' % (n, hLow.peek(), hHigh.peek())

    if Globals.debug: print 'TEST POPS FROM HLOW (should always show and pop the max value)'
    while hLow.len() != 0:
	if Globals.debug: print '     popped %d, peek=%s' % (hLow.pop(), str(hLow.peek()))
	
    if Globals.debug: print 'TEST POPS FROM HHIGH (should always show and pop the min value)'
    while hHigh.len() != 0:
	if Globals.debug: print '     popped %d, peek=%s' % (hHigh.pop(), str(hHigh.peek()))
	

class Heap:
    def  __init__(self, extractMax=False):
        self.heap = []
	self.extractMax = extractMax

    def push(self, entry):
        if self.extractMax:
	    entry *= -1
        heapq.heappush(self.heap, entry)

    def pop(self):
	if len(self.heap) == 0:
	    return None
	    
        entry = heapq.heappop(self.heap)
        if self.extractMax:
	    entry *= -1
        return entry

    def peek(self):
	if len(self.heap) == 0:
	    return None
	    
	entry = self.heap[0]
        if self.extractMax:
	    entry *= -1
        return entry
	
    def len(self):
	return len(self.heap)
	
def readInputFile(fname):
    text_file = open(fname, "rU")
    lines = map(int, text_file.read().splitlines())
    return lines

def submitDataFile(hint, fname):
    submitDataArray(hint, fname, readInputFile(fname))

def submitDataArray(hint, source, nums):
    # each line is an integer
    print(source, 'data len', len(nums), 'hint', hint)
    findMedians(nums)

if __name__ == '__main__':
    if len(sys.argv) == 2:
        if sys.argv[1] == 'debug':
            Globals.debug = True
        elif sys.argv[1] != 'real':
	    print('usage: real')
	    sys.exit()    
	submitDataFile('no hint', 'Median.txt')
    else:
        Globals.debug = True
        submitDataArray('expected result: 54', 'test1', [4,5,6,7,8,9,10,1,2,3])
        submitDataArray('expected result: 23', 'test2', [3,7,4,1,2,6,5])
        submitDataArray('expected result: 55', 'test3', [10,1,9,2,8,3,7,4,6,5])
        submitDataArray('expected result: 148', 'test4', [4,14,5,13,17,6,1,7,19,8,9,10,2,3,11,20,15,16,18,12])
	
        #submitDataFile('expected result:6', 'testcase1.txt')

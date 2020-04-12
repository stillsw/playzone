import sys

class Globals:
    numCompararisons = 0
    debug = False
    debugAndWait = False

def doSort(data, pivotPoint):
    Globals.numCompararisons = 0
    partition(data, pivotPoint, 0, len(data))
    return data
    
def partition(data, pivotPoint, left, right):
    if Globals.debug: print('data now/pivot/left/right', data, pivotPoint,left, right)
    
    if right - left <= 1: # base case, nothing left to process
	return
    
    Globals.numCompararisons += right-left-1
    
    pivotIdx = choosePivotIndex(data, pivotPoint, left, right)
    
    if pivotIdx != left: # need to swap the pivot into the first pos
	swapVal = data[pivotIdx]
	data[pivotIdx] = data[left]
	data[left] = swapVal

    p = data[left] # pivot value    
    i = left + 1 # start indices to the right of pivot

    if Globals.debug: print('     pivot val', p, 'i and j begin at', i)
    
    # loop from j to the right marker, whenever find a value < pivot swap into the place i points to and inc i
    for j in range(left +1, right):
	if data[j] < p:
	    swapVal = data[j]
	    data[j] = data[i]
	    data[i] = swapVal
	    i += 1
	    if Globals.debug: print('          swapped i and j', data[j], swapVal, 'array=', debugArr(data, left, right, i, j+1), 'indexes now', i, j+1) # j+1 because printing after the swaps, wanna show what end of loop looks like
	else:
	    if Globals.debug: print('          advanced j, no swaps ', debugArr(data, left, right, i, j+1), 'indexes now', i, j+1)
	    
	if Globals.debugAndWait: raw_input("          Press enter to continue...")	    
	
    # swap the pivot into its rightful position
    if i - 1 > left:
	swapVal = data[i -1]
	data[i -1] = p
	data[left] = swapVal
	if Globals.debug: print('     swapped pivot into right place with ', swapVal, 'array=', debugArr(data, left, right, i, right))
    else:
	if Globals.debug: print('     no need to swap pivot in because no values were less than it')
    if Globals.debugAndWait: raw_input("     Press enter to continue...")	    

    # recurse the left side (everything between left and pivot)
    partition(data, pivotPoint, left, i -1)
    
    # recurse the right side (everything between pivot and right)
    partition(data, pivotPoint, i, right)

def debugArr(data, left, right, i, j):
    string = ''
    for x in range(left, right):
	if x == i: string += ' (i)'
	if x == j: string += ' (j)'
	string += ' '
	string += str(data[x])
	string += ',' 
    return string
    
def choosePivotIndex(data, pivotPoint, left, right):
    end = right -1
    if pivotPoint == 'start':
	return left
    elif pivotPoint == 'end':
	return end
    else:
	#print('median pivot point, left/right=', left, right, '#entries', right-left, 'point', (left + (right-left) / 2) -1, 'value', data[(left + (right-left) / 2) -1])
	half = (right-left) / 2
	midPoint = left + half
	if half*2 == right-left:
	    midPoint -= 1
	    
	# return the value that is in the middle of the other 2
	firstVal = min(data[left], data[end], data[midPoint])
	lastVal = max(data[left], data[end], data[midPoint])
	
	if data[left] != firstVal and data[left] != lastVal: return left
	elif data[midPoint] != firstVal and data[midPoint] != lastVal: return midPoint
	else: return end
    
def readInputFile(fname):
    text_file = open(fname, "rU")
    lines = text_file.read().splitlines()
    return map(int, lines)

def submitTestCase(fname):
    #get data to print for pivot when median case
    data = readInputFile(fname)
    medIdx = choosePivotIndex(data, 'median', 0, len(data))
    medPivotVal = data[medIdx]
    
    sData = doSort(readInputFile(fname), 'start')
    start = Globals.numCompararisons
    eData = doSort(readInputFile(fname), 'end')
    end = Globals.numCompararisons
    mData = doSort(readInputFile(fname), 'median')
    median = Globals.numCompararisons
    print(fname, 'start', start, 'end', end, 'median', median, 'pivot for median', medIdx, 'value', medPivotVal)
    if len(sData) < 100:
	print('     original data', data)
	if Globals.debug: print('     sorted data pivot=start', sData)
	if Globals.debug: print('     sorted data pivot=end', eData)
	if Globals.debug: print('     sorted data pivot=median', mData)
    
if __name__ == '__main__':
    """
    quick sort an array of input numbers provided in a file
    also counts comparisons
    """
    if len(sys.argv) == 3:
	if sys.argv[2] == 'start' or sys.argv[2] == 'end' or sys.argv[2] == 'median':
	    doSort( readInputFile(sys.argv[1]), sys.argv[2] )
	    print('num comparisons', Globals.numCompararisons)
	else:
	    print("usage1: pw2_quick_sort_array.py <file name> <pivot: start|end|median>")
	    print("usage2: pw2_quick_sort_array.py <no params>: for test cases")
    else:
	submitTestCase('testcases10.txt')
	submitTestCase('testcases13.txt')
	submitTestCase('testcases100.txt')
	submitTestCase('testcases1000.txt')
    

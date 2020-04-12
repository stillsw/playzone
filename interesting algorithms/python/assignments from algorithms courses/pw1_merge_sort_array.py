import sys

class Globals:
    debug = False
    numInversions = 0

def doSort(input):
    Globals.numInversions = 0
    print('doSort: input array len = ', len(input))
    recurseSort = splitToSort(input)
    #for line in recurseSort: print(line)
    print('inversions = ',Globals.numInversions)
    
def splitToSort(input):
    if len(input) <= 1:
	return input
	
    h1 = input[0:len(input) /2]
    h2 = input[len(input) /2:]
    
    arr1 = splitToSort(input[0:len(input) /2])
    arr2 = splitToSort(input[len(input) /2:])

    return mergeResults([arr1, arr2])

def mergeResults(arrs):	
    arr1, arr2 = arrs
    i = 0
    j = 0
    n = len(arr1) + len(arr2)
    res = []
    
    if Globals.debug: print('merge step', arr1, arr2)
    for k in range(0, n):
	#print('going into main loop, k=', k, 'i=', i, 'j=', j)
	
	if i < len(arr1) and j < len(arr2):
	    if arr1[i] <= arr2[j]:
		res.append(arr1[i])
		i += 1
	    else:
		res.append(arr2[j])
		j += 1
		Globals.numInversions += len(arr1) - i
	else:
	    if j < len(arr2):
		res.append(arr2[j])
		j += 1
	    else:
		res.append(arr1[i])
		i += 1
    return res

def readInputFile(fname):
    text_file = open(fname, "rU")
    lines = text_file.read().splitlines()
    return map(int, lines)

def submitTestCase(txt, data):
    print(txt)
    doSort(data)
    
if __name__ == '__main__':
    """
    merge sort an array of input number provided in a file
    also counts inversions
    """
    if len(sys.argv) == 2:
	doSort( readInputFile(sys.argv[1]) )
    else:
	Globals.debug = True
	submitTestCase('examcase1', [5,3,8,9,1,7,0,2,6,4] )
	#submitTestCase('TC1', [1,3,5,2,4,6] )
	#submitTestCase('TC2',  [1,5,3,2,4] )
	#submitTestCase('TC3',  [5,4,3,2,1] )
	#submitTestCase('TC4',  [1,6,3,2,4,5] )
	#submitTestCase('Test Case - #1 - 15 numbers',  [9, 12, 3, 1, 6, 8, 2, 5, 14, 13, 11, 7, 10, 4, 0] )
	#submitTestCase('Test Case - #2 - 50 numbers',  [ 37, 7, 2, 14, 35, 47, 10, 24, 44, 17, 34, 11, 16, 48, 1, 39, 6, 33, 43, 26, 40, 4, 28, 5, 38, 41, 42, 12, 13, 21, 29, 18, 3, 19, 0, 32, 46, 27, 31, 25, 15, 36, 20, 8, 9, 49, 22, 23, 30, 45] )
	#submitTestCase('Test Case - #3 - 100 numbers',  [4, 80, 70, 23, 9, 60, 68, 27, 66, 78, 12, 40, 52, 53, 44, 8, 49, 28, 18, 46, 21, 39, 51, 7, 87, 99, 69, 62, 84, 6, 79, 67, 14, 98, 83, 0, 96, 5, 82, 10, 26, 48, 3, 2, 15, 92, 11, 55, 63, 97, 43, 45, 81, 42, 95, 20, 25, 74, 24, 72, 91, 35, 86, 19, 75, 58, 71, 47, 76, 59, 64, 93, 17, 50, 56, 94, 90, 89, 32, 37, 34, 65, 1, 73, 41, 36, 57, 77, 30, 22, 13, 29, 38, 16, 88, 61, 31, 85, 33, 54] )
    

import sys

def doSort(input):
    print('doSort: input = ', input)
    recurseSort = splitToSort(input)
    print('        sort  = ', recurseSort)
    
def splitToSort(input):
    if len(input) <= 1:
	return input
	
    if len(input) == 2:
	if input[0] > input[1]:
	    return input[1]+input[0]
	else:
	    return input[0]+input[1]
    else:
	h1 = input[0:len(input) /2]
	h2 = input[len(input) /2:]
	#print('splitting into 2', h1, h2)
	
	arr1 = splitToSort(input[0:len(input) /2])
	arr2 = splitToSort(input[len(input) /2:])
	#print('got back from sort into 2', arr1, arr2)

	return mergeResults([arr1, arr2])

def mergeResults(arrs):	
    arr1, arr2 = arrs
    res = ''
    i = 0
    j = 0
    
    while i < len(arr1) and j < len(arr2):				# lecture example instead uses k like this - for k in range(0, n): where n = len(input)
	#print('going into main loop i=', i, 'j=', j)
	while i < len(arr1) and arr1[i] <= arr2[j]:
	    res = res + arr1[i]					# using the k technique this would look like - res[k] = arr1[i]
	    i += 1
	while i < len(arr1) and j < len(arr2) and arr2[j] < arr1[i]:
	    res = res + arr2[j]
	    j += 1

    if i < len(arr1):
	res = res + arr1[i:]
    elif j < len(arr2):
	res = res + arr2[j:]

    return res

if __name__ == '__main__':
    """
    basic merge sort on input number
    """
    if len(sys.argv) != 2:
	    print('Usage: python w1_merge_sort.py <string>')
	    sys.exit()
    
    doSort(sys.argv[1])
    

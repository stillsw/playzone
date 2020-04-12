"""
support class based on algorithms 1, pw2 quick sort problem, uses a pivot idx to home in on a value that is closest to the requested one
"""
def findValue(data, value, start=0, end=None, debug=False, convertToValue=None):
    """
    given a sorted data array and a value to find in it and a pivot to start partitioning
    find and return the closest value and its index in the data
    """
    if end == None:
        end = len(data)-1;
    return partition(data, value, start, end, convertToValue)
    
def partition(data, value, left, right, convertToValue):
    
    if right - left < 0:
        raise ValueError('partition slice length dropped below 1')
    
    if right == left: # base case, nothing left to process
        return data[left], left

    pivotIdx = choosePivotIndex(left, right)
    p = data[pivotIdx]
    
    if convertToValue != None:   # when passed a function use it to change the p value from some data obj to something that can be compared to value
        p = convertToValue(p)
        
    if p == value:
        return value, pivotIdx

    # recurse the side that contains the value
    if p < value: # partition to the right of it
        return partition(data, value, pivotIdx+1, right, convertToValue)
    else:
        return partition(data, value, left, pivotIdx, convertToValue)

def choosePivotIndex(left, right):
    # not very sophisticated, based on the left/right index return the mid point
    if left == right: return left

    return left + ((right - left) / 2)


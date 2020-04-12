import time

class Queue:
    "A container with a first-in-first-out (FIFO) queuing policy."
    def __init__(self):
        self.list = []

    def push(self,item):
        "Enqueue the 'item' into the queue"
        self.list.insert(0,item)

    def pop(self):
        """
          Dequeue the earliest enqueued item still in the queue. This
          operation removes the item from the queue.
        """
        return self.list.pop()

    def isEmpty(self):
        "Returns true if the queue is empty"
        return len(self.list) == 0

class HashableList:
    def __init__(self, srcList):
        self.srcList = srcList

class Debug:
    debug = False
    debugAndWait = False
    timing = False

def waitForInput(msg=None):
    if msg != None: print msg
    raw_input('Press enter to continue...')
    
def formatString(string, numDigits, right=True):
    if len(string) > numDigits:
        return string[:numDigits+1]
    elif len(string) == numDigits:
        return string
    elif right:
        return string + (' ' * (numDigits - len(string)))
    else:
        return (' ' * (numDigits - len(string))) + string
    
def formatNumber(num, numDigits, right=True):
    return formatString(str.format('{:4.2f}', num), numDigits, right)

def formatInteger(num, numDigits, right=True):
    return formatString(str.format('{:d}', num), numDigits, right)

def formatNumberSquare(value, strLen, middle=False, right=False, left=False, width=5):
    if value == None:
        fStr = ' ' * strLen
    else:
        numStr = formatNumber(value, width, right)
	if right:
	    fStr = ' ' * (strLen - width)
	    fStr = fStr + numStr
	elif left:
	    fStr = ' ' * (strLen - width)
	    fStr = numStr + fStr
	else: # middle
	    tmpStr = ' ' * ((strLen - width) / 2)
	    fStr = tmpStr + numStr + tmpStr
    
    return fStr

def reportTime(stmt, prevTime):
    now = time.clock()
    if now - prevTime > 0.0:
        print '           timing for %s, %f' % (stmt, (now - prevTime))
    return now

def readInputFile(fname):
    text_file = open(fname, "rU")
    lines = text_file.read().splitlines()
    return lines

def readInputFileAsInts(fname):
    text_file = open(fname, "rU")
    lines = map(int, text_file.read().splitlines())
    return lines

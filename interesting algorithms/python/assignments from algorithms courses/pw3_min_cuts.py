import sys, time
from random import randint

class Globals:
    debug = False
    debugAndWait = False
    timing = False

def doMinCut(N):
    """
    N is the set of all vertices, it doesn't change because it is needed to recreate the edges each time round
    M is the set of edges, they are removed as the corresponding vertices are collapsed 
    """
    # keep the best cut found until done the min number to get the result
    numIterations = len(N) * len(N) # this should be * ln n (ie. uprated to the natural log of n) for best results, 
				    # but that's > 3 times the work for the assigment, first see if this works well enough
    leastEdgesSoFar = None
    
    print('     run for numIterations', numIterations)
    for i in range(0, numIterations):
	M = initEdges(N)
	cutEdges = calcMinCutEdges(N, M)
	if leastEdgesSoFar == None or len(cutEdges) < len(leastEdgesSoFar):
	    leastEdgesSoFar = cutEdges
	    print('     after i', i, 'best so far', len(leastEdgesSoFar), 'edges', leastEdgesSoFar)
	
	if i > 0 and len(N) > 50 and i % len(N) == 0:   
	    print('     upto', i)

    # finally the best result
    print('     after numIterations', numIterations, 'found min cut', len(leastEdgesSoFar), 'edges', leastEdgesSoFar)
    
def calcMinCutEdges(N, M):

    if Globals.debug: print('calc: edges', M)
    
    prevTime = time.clock()
    
    collapsedVs = [] # sets of vertices that have been collapsed together
    soloVs = N.keys()
    
    loopTime = prevTime
    while len(collapsedVs) + len(soloVs) > 2: 	# collapse edges until only 2 left
	if len(M) == 0:
	    raise ValueError('ran out of edges but still have vertices')
	
	collapseEdge = M[randint(0, len(M) -1)]	# choose any M at random
	M.remove(collapseEdge)			# remove it
	
	if Globals.debug: print('chosen edge', collapseEdge)
	if Globals.timing: prevTime = reportTime('choose an edge', prevTime)
	
	v1, v2 = collapseEdge			# 2 vertices 
	cv1 = None
	cv2 = None
	for cv in collapsedVs:			# perhaps either or both are already collapsed
	    if v1 in cv: cv1 = cv
	    if v2 in cv: cv2 = cv
	
	if cv1 != None and cv2 == cv1:
	    #raise ValueError('edge exists for 2 vertices that are already collapsed together')
	    # instead of throwing an error when find an edge that is now a self loop, just remove it and 
	    # go around again... saving the slow self-loop deletion at the end
	    continue
	
	elif cv1 == None and cv2 == None:		# neither vertex has had an edge collapse it before
	    cv1 = [v1, v2]
	    collapsedVs.append(cv1)		# add as a new set
	    soloVs.remove(v1)			# remove both from the solo list
	    soloVs.remove(v2)
	
	elif cv1 != None and cv2 != None:	# both vertices are in collapsed sets already, means they have to be joined
	    for cv in cv2: 			# add all the occurences from the 2nd to the 1st set
		cv1.append(cv)
	    collapsedVs.remove(cv2)		# remove the 2nd one
	    cv2 = None				# clear the ref (for the self loop removal below)

	else: 					# one or the other is in a set and the other is not
	    if cv1 != None:			# move the one that isn't from the solo list to the other
		cv1.append(v2)
		soloVs.remove(v2)		
	    else:
		cv2.append(v1)
		soloVs.remove(v1)
		cv1 = cv2			# just to make self loop check easier
	
	if Globals.debug: print('soloVs', soloVs, 'collapsedVs', collapsedVs)
	if Globals.timing: prevTime = reportTime('collapse the vertices', prevTime)
	
	"""
	# self loops: (remove any edges to vertices within the same collapsed set)
	for v in (cv1):
	    for adjV in N.get(v):			# check the adjacent vertices from the start
		if adjV in cv1 and (v, adjV) in M:	# there'll only be one if not previously removed (and v < adjV)
		    M.remove((v, adjV))
		    if Globals.debug: print('also removed edge', v, adjV)
	
	if Globals.timing: prevTime = reportTime('take out self loops', prevTime)
	"""
	
    if Globals.timing: prevTime = reportTime('collapse ALL vertices, ie. to do the whole loop', loopTime)
    
    # count edges that span what's left
    cutEdges = []
    for v in collapsedVs[0]:			# one set is all that's needed, just look for vertices not both in it
	for adjV in N.get(v):
	    if adjV not in collapsedVs[0] and (adjV, v) not in cutEdges: 
		cutEdges.append((v, adjV))
    
    if Globals.timing: prevTime = reportTime('count cuts', prevTime)
    
    return cutEdges
    
def initEdges(N):
    M = []
    for n in N.keys(): 		# read each vertex
	for vs in N.get(n): 	# return array of adjacent vertices
	    if n < vs:		# remove dupes by only adding an edge one way
		M.append((n, vs))	# create edge both ways (creates dupes, but it's ok for this, makes count a bit simpler)
		

    return M
    
def reportTime(stmt, prevTime):
    now = time.clock()
    if now - prevTime > 0.0:
	print('           timing for', stmt, now - prevTime)
    return now
    
def readInputFile(fname):
    text_file = open(fname, "rU")
    lines = text_file.read().splitlines()
    return lines
    
    # map(int, lines)

def convertDataLines(dataLines):
    N = dict()
    for line in dataLines:
	vs = map(int, line.split()) # convert to int so <> compare easy
	N[vs[0]] = vs[1:]
	
    return N
    
def submitDataFile(hint, fname):

    dataLines = readInputFile(fname)
    # each line is a string of numbers separated by spaces
    print(fname, 'contains #vertices', len(dataLines), 'hint', hint)
    N = convertDataLines(dataLines)
    if Globals.debug: print('vertices', N)
    doMinCut(N)
    
if __name__ == '__main__':
    """
    min cut the matrix of input numbers provided in a file
    """
    if len(sys.argv) == 2:
	submitDataFile('no hint', sys.argv[1])
	#print('num cuts', Globals.numCompararisons)
    else:
	submitDataFile('expected result: 2, cuts are [(1,7), (4,5)]', 'testcase1.txt')
	submitDataFile('expected result: 2, cuts are [(1,7), (4,5)]', 'testcase2.txt')
	submitDataFile('expected result: 1, cut is [(4,5)]', 'testcase3.txt')
	submitDataFile('expected result: 3', 'testcase4.txt')
	submitDataFile('mincut=2', 'testcase5.txt')
    

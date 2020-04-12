# searchAgents.py
# ---------------
# Licensing Information:  You are free to use or extend these projects for
# educational purposes provided that (1) you do not distribute or publish
# solutions, (2) you retain this notice, and (3) you provide clear
# attribution to UC Berkeley, including a link to http://ai.berkeley.edu.
# 
# Attribution Information: The Pacman AI projects were developed at UC Berkeley.
# The core projects and autograders were primarily created by John DeNero
# (denero@cs.berkeley.edu) and Dan Klein (klein@cs.berkeley.edu).
# Student side autograding was added by Brad Miller, Nick Hay, and
# Pieter Abbeel (pabbeel@cs.berkeley.edu).


"""
This file contains all of the agents that can be selected to control Pacman.  To
select an agent, use the '-p' option when running pacman.py.  Arguments can be
passed to your agent using '-a'.  For example, to load a SearchAgent that uses
depth first search (dfs), run the following command:

> python pacman.py -p SearchAgent -a fn=depthFirstSearch

Commands to invoke other search strategies can be found in the project
description.

Please only change the parts of the file you are asked to.  Look for the lines
that say

"*** YOUR CODE HERE ***"

The parts you fill in start about 3/4 of the way down.  Follow the project
description for details.

Good luck and happy searching!
"""

from game import Directions
from game import Agent
from game import Actions
import game
import util
import time
import search
import sys

class GoWestAgent(Agent):
    "An agent that goes West until it can't."

    def getAction(self, state):
        "The agent receives a GameState (defined in pacman.py)."
        if Directions.WEST in state.getLegalPacmanActions():
            return Directions.WEST
        else:
            return Directions.STOP

#######################################################
# This portion is written for you, but will only work #
#       after you fill in parts of search.py          #
#######################################################

class SearchAgent(Agent):
    """
    This very general search agent finds a path using a supplied search
    algorithm for a supplied search problem, then returns actions to follow that
    path.

    As a default, this agent runs DFS on a PositionSearchProblem to find
    location (1,1)

    Options for fn include:
      depthFirstSearch or dfs
      breadthFirstSearch or bfs


    Note: You should NOT change any code in SearchAgent
    """

    def __init__(self, fn='depthFirstSearch', prob='PositionSearchProblem', heuristic='nullHeuristic'):
        # Warning: some advanced Python magic is employed below to find the right functions and problems

        # Get the search function from the name and heuristic
        if fn not in dir(search):
            raise AttributeError, fn + ' is not a search function in search.py.'
        func = getattr(search, fn)
        if 'heuristic' not in func.func_code.co_varnames:
            print('[SearchAgent] using function ' + fn)
            self.searchFunction = func
        else:
            if heuristic in globals().keys():
                heur = globals()[heuristic]
            elif heuristic in dir(search):
                heur = getattr(search, heuristic)
            else:
                raise AttributeError, heuristic + ' is not a function in searchAgents.py or search.py.'
            print('[SearchAgent] using function %s and heuristic %s' % (fn, heuristic))
            # Note: this bit of Python trickery combines the search algorithm and the heuristic
            self.searchFunction = lambda x: func(x, heuristic=heur)

        # Get the search problem type from the name
        if prob not in globals().keys() or not prob.endswith('Problem'):
            raise AttributeError, prob + ' is not a search problem type in SearchAgents.py.'
        self.searchType = globals()[prob]
        print('[SearchAgent] using problem type ' + prob)

    def registerInitialState(self, state):
        """
        This is the first time that the agent sees the layout of the game
        board. Here, we choose a path to the goal. In this phase, the agent
        should compute the path to the goal and store it in a local variable.
        All of the work is done in this method!

        state: a GameState object (pacman.py)
        """
        if self.searchFunction == None: raise Exception, "No search function provided for SearchAgent"
        starttime = time.time()
        problem = self.searchType(state) # Makes a new search problem
        self.actions  = self.searchFunction(problem) # Find a path
        totalCost = problem.getCostOfActions(self.actions)
        print('Path found with total cost of %d in %.1f seconds' % (totalCost, time.time() - starttime))
        if '_expanded' in dir(problem): print('Search nodes expanded: %d' % problem._expanded)

    def getAction(self, state):
        """
        Returns the next action in the path chosen earlier (in
        registerInitialState).  Return Directions.STOP if there is no further
        action to take.

        state: a GameState object (pacman.py)
        """
        if 'actionIndex' not in dir(self): self.actionIndex = 0
        i = self.actionIndex
        self.actionIndex += 1
        if i < len(self.actions):
            return self.actions[i]
        else:
            return Directions.STOP

class PositionSearchProblem(search.SearchProblem):
    """
    A search problem defines the state space, start state, goal test, successor
    function and cost function.  This search problem can be used to find paths
    to a particular point on the pacman board.

    The state space consists of (x,y) positions in a pacman game.

    Note: this search problem is fully specified; you should NOT change it.
    """

    def __init__(self, gameState, costFn = lambda x: 1, goal=(1,1), start=None, warn=True, visualize=True):
        """
        Stores the start and goal.

        gameState: A GameState object (pacman.py)
        costFn: A function from a search state (tuple) to a non-negative number
        goal: A position in the gameState
        """
        self.walls = gameState.getWalls()
        self.startState = gameState.getPacmanPosition()
        if start != None: self.startState = start
        self.goal = goal
        self.costFn = costFn
        self.visualize = visualize
        if warn and (gameState.getNumFood() != 1 or not gameState.hasFood(*goal)):
            print 'Warning: this does not look like a regular search maze'

        # For display purposes
        self._visited, self._visitedlist, self._expanded = {}, [], 0 # DO NOT CHANGE

    def getStartState(self):
        return self.startState

    def isGoalState(self, state):
        isGoal = state == self.goal

        # For display purposes only
        if isGoal and self.visualize:
            self._visitedlist.append(state)
            import __main__
            if '_display' in dir(__main__):
                if 'drawExpandedCells' in dir(__main__._display): #@UndefinedVariable
                    __main__._display.drawExpandedCells(self._visitedlist) #@UndefinedVariable

        return isGoal

    def getSuccessors(self, state):
        """
        Returns successor states, the actions they require, and a cost of 1.

         As noted in search.py:
             For a given state, this should return a list of triples,
         (successor, action, stepCost), where 'successor' is a
         successor to the current state, 'action' is the action
         required to get there, and 'stepCost' is the incremental
         cost of expanding to that successor
        """

        successors = []
        for action in [Directions.NORTH, Directions.SOUTH, Directions.EAST, Directions.WEST]:
            x,y = state
            dx, dy = Actions.directionToVector(action)
            nextx, nexty = int(x + dx), int(y + dy)
            if not self.walls[nextx][nexty]:
                nextState = (nextx, nexty)
                cost = self.costFn(nextState)
                successors.append( ( nextState, action, cost) )

        # Bookkeeping for display purposes
        self._expanded += 1 # DO NOT CHANGE
        if state not in self._visited:
            self._visited[state] = True
            self._visitedlist.append(state)

        return successors

    def getCostOfActions(self, actions):
        """
        Returns the cost of a particular sequence of actions. If those actions
        include an illegal move, return 999999.
        """
        if actions == None: return 999999
        x,y= self.getStartState()
        cost = 0
        for action in actions:
            # Check figure out the next state and see whether its' legal
            dx, dy = Actions.directionToVector(action)
            x, y = int(x + dx), int(y + dy)
            if self.walls[x][y]: return 999999
            cost += self.costFn((x,y))
        return cost

class StayEastSearchAgent(SearchAgent):
    """
    An agent for position search with a cost function that penalizes being in
    positions on the West side of the board.

    The cost function for stepping into a position (x,y) is 1/2^x.
    """
    def __init__(self):
        self.searchFunction = search.uniformCostSearch
        costFn = lambda pos: .5 ** pos[0]
        self.searchType = lambda state: PositionSearchProblem(state, costFn, (1, 1), None, False)

class StayWestSearchAgent(SearchAgent):
    """
    An agent for position search with a cost function that penalizes being in
    positions on the East side of the board.

    The cost function for stepping into a position (x,y) is 2^x.
    """
    def __init__(self):
        self.searchFunction = search.uniformCostSearch
        costFn = lambda pos: 2 ** pos[0]
        self.searchType = lambda state: PositionSearchProblem(state, costFn)

def manhattanHeuristic(position, problem, info={}):
    "The Manhattan distance heuristic for a PositionSearchProblem"
    xy1 = position
    xy2 = problem.goal
    return abs(xy1[0] - xy2[0]) + abs(xy1[1] - xy2[1])

def euclideanHeuristic(position, problem, info={}):
    "The Euclidean distance heuristic for a PositionSearchProblem"
    xy1 = position
    xy2 = problem.goal
    return ( (xy1[0] - xy2[0]) ** 2 + (xy1[1] - xy2[1]) ** 2 ) ** 0.5

#####################################################
# This portion is incomplete.  Time to write code!  #
#####################################################

class CornersProblem(search.SearchProblem):
    """
    This search problem finds paths through all four corners of a layout.

    You must select a suitable state space and successor function
    """

    def __init__(self, startingGameState):
        """
        Stores the walls, pacman's starting position and corners.
        """
        self.walls = startingGameState.getWalls()
        self.startingPosition = startingGameState.getPacmanPosition()
        top, right = self.walls.height-2, self.walls.width-2
        self.corners = ((1,1), (1,top), (right, 1), (right, top))
        for corner in self.corners:
            if not startingGameState.hasFood(*corner):
                print 'Warning: no food in corner ' + str(corner)
        self._expanded = 0 # DO NOT CHANGE; Number of search nodes expanded
        # Please add any code here which you would like to use
        # in initializing the problem
        "*** YOUR CODE HERE ***"

    def getStartState(self):
        """
        Returns the start state (in your state space, not the full Pacman state
        space)
        """
        "*** YOUR CODE HERE ***"
        #Project 1: Q5
        ret = ( self.startingPosition, getUpdatedCorners(self.corners, self.startingPosition, '0F1F2F3F') )
#        print('start', ret)
        return ret

    def isGoalState(self, state):
        """
        Returns whether this search state is a goal state of the problem.
        """
        "*** YOUR CODE HERE ***"
        #Project 1: Q5
        visitedCorners = state[1]
        visitedCorners = getUpdatedCorners(self.corners, state[0], visitedCorners)
        return (visitedCorners.find('F') == -1)

    def getSuccessors(self, state):
        """
        Returns successor states, the actions they require, and a cost of 1.

         As noted in search.py:
            For a given state, this should return a list of triples, (successor,
            action, stepCost), where 'successor' is a successor to the current
            state, 'action' is the action required to get there, and 'stepCost'
            is the incremental cost of expanding to that successor
        """

        successors = []
        for action in [Directions.NORTH, Directions.SOUTH, Directions.EAST, Directions.WEST]:
            # Add a successor state to the successor list if the action is legal
            # Here's a code snippet for figuring out whether a new position hits a wall:
            #   x,y = currentPosition
            #   dx, dy = Actions.directionToVector(action)
            #   nextx, nexty = int(x + dx), int(y + dy)
            #   hitsWall = self.walls[nextx][nexty]

            "*** YOUR CODE HERE ***"
            #Project 1: Q5
            x,y = state[0]
            dx, dy = Actions.directionToVector(action)
            nextx, nexty = int(x + dx), int(y + dy)
            nextState = (nextx, nexty)
            visitedCorners = state[1]
            visitedCorners = getUpdatedCorners(self.corners, state[0], visitedCorners)
            if not self.walls[nextx][nexty]:
                successors.append( ( (nextState, visitedCorners), action, 1) )
        
        self._expanded += 1 # DO NOT CHANGE
        return successors

    def getCostOfActions(self, actions):
        """
        Returns the cost of a particular sequence of actions.  If those actions
        include an illegal move, return 999999.  This is implemented for you.
        """
        if actions == None: return 999999
        x,y= self.startingPosition
        for action in actions:
            dx, dy = Actions.directionToVector(action)
            x, y = int(x + dx), int(y + dy)
            if self.walls[x][y]: return 999999
        return len(actions)


def getUpdatedCorners(corners, position, currentCorners):
    if position in corners:
        return currentCorners.replace(str(corners.index(position))+'F', str(corners.index(position))+'T')
    return currentCorners
    
def cornersHeuristic(state, problem):
    """
    A heuristic for the CornersProblem that you defined.

      state:   The current search state
               (a data structure you chose in your search problem)

      problem: The CornersProblem instance for this layout.

    This function should always return a number that is a lower bound on the
    shortest path from the state to a goal of the problem; i.e.  it should be
    admissible (as well as consistent).
    """
    corners = problem.corners # These are the corner coordinates
    walls = problem.walls # These are the walls of the maze, as a Grid (game.py)

    "*** YOUR CODE HERE ***"
    #Project 1: Q6
    """ It depends on which corners have been visited already 
        find the nearest corner and keep its manhattan distance 
        then loop through all the remaining unvisited corners and 
        add the nearest distance to each one... 
        got a good result this way, 702 expanded nodes """
    
    visitedCorners = state[1]
    nearestCorner, hNearest = getNearestUnvisitedCorner(state[0], corners, visitedCorners)
    if nearestCorner == -1:
        return 0
        
    # go from one to the next to the next until all are included, start at the nearest
    while True:
        visitedCorners = getUpdatedCorners(corners, corners[nearestCorner], visitedCorners)
        if visitedCorners.find('F') == -1:
            return hNearest
        nearestCorner, hNextNearest = getNearestUnvisitedCorner(corners[nearestCorner], corners, visitedCorners)
        hNearest += hNextNearest
        
    return hNearest
        
def getNearestUnvisitedCorner(position, corners, visitedCorners):
    hNearest = 99999
    nearestCorner = -1
    for (i, corner) in enumerate(corners):
        if visitedCorners.find(str(i)+'F') != -1:
            manhattan = util.manhattanDistance(position, corner)
            if manhattan < hNearest:
                hNearest = manhattan
                nearestCorner = i
    return (nearestCorner, hNearest)
    

class AStarCornersAgent(SearchAgent):
    "A SearchAgent for FoodSearchProblem using A* and your foodHeuristic"
    def __init__(self):
        self.searchFunction = lambda prob: search.aStarSearch(prob, cornersHeuristic)
        self.searchType = CornersProblem

class FoodSearchProblem:
    """
    A search problem associated with finding the a path that collects all of the
    food (dots) in a Pacman game.

    A search state in this problem is a tuple ( pacmanPosition, foodGrid ) where
      pacmanPosition: a tuple (x,y) of integers specifying Pacman's position
      foodGrid:       a Grid (see game.py) of either True or False, specifying remaining food
    """
    def __init__(self, startingGameState):
        self.start = (startingGameState.getPacmanPosition(), startingGameState.getFood())
        self.walls = startingGameState.getWalls()
        self.startingGameState = startingGameState
        self._expanded = 0 # DO NOT CHANGE
        self.heuristicInfo = {} # A dictionary for the heuristic to store information

    def getStartState(self):
        return self.start

    def isGoalState(self, state):
        return state[1].count() == 0

    def getSuccessors(self, state):
        "Returns successor states, the actions they require, and a cost of 1."
        successors = []
        self._expanded += 1 # DO NOT CHANGE
        for direction in [Directions.NORTH, Directions.SOUTH, Directions.EAST, Directions.WEST]:
            x,y = state[0]
            dx, dy = Actions.directionToVector(direction)
            nextx, nexty = int(x + dx), int(y + dy)
            if not self.walls[nextx][nexty]:
                nextFood = state[1].copy()
                nextFood[nextx][nexty] = False
                successors.append( ( ((nextx, nexty), nextFood), direction, 1) )
        return successors

    def getCostOfActions(self, actions):
        """Returns the cost of a particular sequence of actions.  If those actions
        include an illegal move, return 999999"""
        x,y= self.getStartState()[0]
        cost = 0
        for action in actions:
            # figure out the next state and see whether it's legal
            dx, dy = Actions.directionToVector(action)
            x, y = int(x + dx), int(y + dy)
            if self.walls[x][y]:
                return 999999
            cost += 1
        return cost

class AStarFoodSearchAgent(SearchAgent):
    "A SearchAgent for FoodSearchProblem using A* and your foodHeuristic"
    def __init__(self):
        self.searchFunction = lambda prob: search.aStarSearch(prob, foodHeuristic)
        self.searchType = FoodSearchProblem

def foodHeuristic(state, problem):

    position, foodGrid = state
    "*** YOUR CODE HERE ***"
    #Project 1: Q7

    """
    nice concise solution from billmoruuv
    """
    position, foodGrid = state
    if 'dist' not in problem.heuristicInfo:
        problem.heuristicInfo['dist'] = {}
        # initialise the distance
        all_food = foodGrid.asList()
        for i in range(len(all_food)):
            for j in range(i+1, len(all_food)):
                d = mazeDistance(all_food[i], all_food[j], problem.startingGameState)
                problem.heuristicInfo['dist'][(all_food[i], all_food[j])] = d
                problem.heuristicInfo['dist'][(all_food[j], all_food[i])] = d
    
        # order the pair in order of longest distance
        all_dist = sorted([(b,a) for (a,b) in problem.heuristicInfo['dist'].items()], reverse=True)
        problem.heuristicInfo["order_pair"] = [b for a,b in all_dist]
        print "final time", time.time(),'dist' not in problem.heuristicInfo
    
    mydistance = lambda x,y, s:  abs(x[0]-y[0]) +abs(x[1]-y[1]) 
    #mydistance = lambda x,y, s: mazeDistance(x,y,s)
    
    available_food = set(foodGrid.asList())
        
    # special case
    if len(available_food) == 0:
        return 0
    if len(available_food) == 1:
        return mydistance(position, list(available_food)[0], problem.startingGameState)
    elif len(available_food) == 2:
        p1, p2 = available_food
        d1 = mydistance(position, p1, problem.startingGameState)
        d2 = mydistance(position, p2, problem.startingGameState)
        d = problem.heuristicInfo['dist'][(p1,p2)]
        return min([d1+d, d2+d])
        
    # find largest pair available
    for p1,p2 in problem.heuristicInfo["order_pair"]:
        if p1 in available_food and p2 in available_food:
            P1, P2 = p1,p2
            break
    p1,p2= P1,P2
    #search a third point which maximizes the distance to the two other.
    max_d=0
    max_p = 0
    for p in available_food:
        if p in [p1,p2]:
            continue 
        if problem.heuristicInfo['dist'][(p,p1)] + problem.heuristicInfo['dist'][(p,p2)] >max_d:
            max_d = problem.heuristicInfo['dist'][(p,p1)] + problem.heuristicInfo['dist'][(p,p2)]
            max_p = p

    d1 = mydistance(position, p1, problem.startingGameState)
    d2 = mydistance(position, p2, problem.startingGameState)
    d3 = mydistance(position, max_p, problem.startingGameState)
    d4 = problem.heuristicInfo['dist'][(max_p, p1)]
    d5 = problem.heuristicInfo['dist'][(max_p, p2)]
    d6 = problem.heuristicInfo['dist'][(p1, p2)]
    
    return min([d1 + d4 + d5, d1 + d6 + d5,
                d2 + d5 + d4, d2 + d6 + d4,
                d3 + d4 + d6, d3 + d5 + d6])
    
    return min([d1,d2,d3]) + min([d4+d6,max_d, d5+d6])
    
    """
    WIP: basically abandoned unless I feel like working hard towards something already solved in better ways... consider convex hull algorithms etc
    walls = problem.startingGameState.getWalls()
    
    # this section is done just once
    if not problem.heuristicInfo.has_key('mazeRoutes'):
        mazeRoutes = MazeRoutes(walls, problem)
        problem.heuristicInfo['mazeRoutes'] = mazeRoutes
        problem.heuristicInfo['pelletDists'] = dict()        
        problem.heuristicInfo['pelletStartCount'] = foodGrid.count()
        
        # create a list of groups, in the order they should be processed
        groupsList = groupFoodPellets(position, foodGrid, problem, walls, mazeRoutes)
        
        
        drawDebugMap(foodGrid, groupsList, walls, position)

        calcGroupsDistances(groupsList, walls, problem, mazeRoutes)
        fullPath, groupOrder = orderGroupsForShortestPath(position, groupsList, walls, problem, mazeRoutes)
        #print('groupOrder', groupOrder)
        
        # fill in the heuristics for the complete path, provided have one
        if fullPath == None:
            fullPath = []
        elif not testPathGetsAllFood(fullPath, position, problem):
            # it's possible the path ran through all the groups and picked up all the food along the way
            # check first before doing a more detailed run, the previous call had to assess from many 
            # group to group combinations which was fastest, so not the time to do this level of detail
            fullPath = makeDetailedPathThroughGroups(groupOrder, position, groupsList, walls, problem, mazeRoutes)
        
        fillExactHeuristicsForPath(problem.heuristicInfo.get('pelletDists'), fullPath, position, problem)
        
    pelletDists = problem.heuristicInfo.get('pelletDists')
    mazeRoutes = problem.heuristicInfo.get('mazeRoutes')
    
    if pelletDists.has_key(position+(foodGrid.count(),0)):
        return pelletDists.get(position+(foodGrid.count(),0))
    else:
        # for a correct and consistent heuristic find the place furthest on the path where the current food count is set and
        # add the distance from there to this point
        currentFood = foodGrid.count()
        posList = []
        for key in pelletDists.keys():
            if key[2] == currentFood:
                #print (key, 'currentFood', currentFood, 'dist', pelletDists.get(key))
                posList.append((key[0],key[1]))
                
        route, nearestPoint = getMazeRouteToFurthestPointInList(position, posList, walls, problem, mazeRoutes)
        res = len(route) + pelletDists.get(nearestPoint+(currentFood,0))
        pelletDists[position+(currentFood,0)] = res
        return res
    """
    
def drawDebugMap(foodGrid, groupsList, walls, start):
    # debugging aid, drawing the whole thing, 5 spaces per square
    for ry in range(0, foodGrid.height):
        y = foodGrid.height - 1 - ry
        line1 = ''
        line2 = ''
        for x in range(0, foodGrid.width):
            if walls[x][y]: 
                line1 = line1 + '<<+>>'
                line2 = line2 + '<<+>>'
            elif (x,y) == start: 
                line1 = line1 + ' / \ '
                line2 = line2 + ' \ / '
            elif not foodGrid[x][y]: 
                line1 = line1 + '     '
                line2 = line2 + '     '
            else:
                donePellet = False
                for group in groupsList:
                    if not donePellet and group.pellets.count((x,y)) == 1:
                        donePellet = True
                        grp = ' g' + str(group.fgNum)
                        if len(grp) == 3: grp = grp + ' '
                        line1 = line1 + grp + ' '
                        if len(group.segments) > 0:
                            seg = ' s' + str(group.getSegmentContainingPoint((x,y)).segNum)
                            if len(seg) == 3: seg = seg + ' '
                            line2 = line2 + seg + ' '
                        else:
                            line2 = line2 + '     '
                if not donePellet:
                    line1 = line1 + ' ??? '
                    line2 = line2 + ' ??? '
                    
        print(line1)
        print(line2)
            
class MazeRoutes():
    def __init__(self, walls, problem):
        self.routes = dict()
        self.walls = walls
        self.problem = problem
        
    def getRoute(self, point1, point2):
        if self.routes.has_key((point1, point2)):
            return self.routes.get((point1, point2))
        # maybe has it the other way around
        elif self.routes.has_key((point2, point1)):
            return getReverseRoute(self.routes.get((point2, point1)))
            
        x1, y1 = point1
        x2, y2 = point2
        prob = PositionSearchProblem(self.problem.startingGameState, start=point1, goal=point2, warn=False, visualize=False)
        route = search.bfs(prob)
        self.routes[(point1, point2)] = route
        return route
    
    def getDistance(self, point1, point2):
        return len(self.getRoute(point1, point2))
        
def getReverseRoute(route):
    rev = []
    for i in range(0, len(route)):
        j = len(route) - 1 - i
        if route[j] == Directions.NORTH: rev.append(Directions.SOUTH)
        elif route[j] == Directions.SOUTH: rev.append(Directions.NORTH)
        elif route[j] == Directions.EAST: rev.append(Directions.WEST)
        elif route[j] == Directions.WEST: rev.append(Directions.EAST)
    return rev
        
def makeDetailedPathThroughGroups(groupOrder, position, groupsList, walls, problem, mazeRoutes):
    # similar to FoodGroupsOrderProblem.getShortestPath except now already have the order of groups
    # so the problem is to fill them
    print ('makeDetailedPathThroughGroups: need to improve full path')
        
    # split the string of paths into nodes
    nodes = groupOrder.split('>')
    
    fullRoute = []
    prevPos = position
    
    for i in range(0, len(nodes)):
        node = nodes[i]
        
        if len(node) > 1: # ignore '' 

            group = getGroupWithNum(int(node[1:]), groupsList)
            
            # see if there's a next group to include
            nextGroup = None
            j = i + 1
            if j < len(nodes):
                nextNode = nodes[j]
                if len(nextNode) > 1: # ignore '' 
                    nextGroup = getGroupWithNum(int(nextNode[1:]), groupsList)
                
            route, prevPos = group.getShortestPathToEatAllPellets(prevPos, nextGroup, walls, problem, mazeRoutes)
            fullRoute = fullRoute + route
    
    return fullRoute
    
def orderGroupsForShortestPath(point1, groupsList, walls, problem, mazeRoutes):
    
    orderProb = FoodGroupsOrderProblem(point1, groupsList, walls, problem, mazeRoutes)
    search.bfs(orderProb)
    return orderProb.getShortestPath()
    
def calcGroupsDistances(groupsList, walls, problem, mazeRoutes):
    for i in range(0, len(groupsList) -1): # stepping through and pairing with each subsequent one, so don't go to the end one
        group = groupsList[i]
        #print ('group i=', i, 'id=', group.fgNum, 'avePos', group.avePos, 'pellets', group.pellets)
        for j in range(i+1, len(groupsList)):
            pairGroup = groupsList[j]
            path = mazeRoutes.getRoute(group.avePos, pairGroup.avePos)
            reversePath = mazeRoutes.getRoute(pairGroup.avePos, group.avePos)
            #print ('    pair with group j=', j, 'id=', pairGroup.fgNum, 'avePos', pairGroup.avePos)
            #print ('        route one way', len(path), path)
            #print ('        route other way', len(reversePath), reversePath)
    
def groupFoodPellets(position, foodGrid, problem, walls, mazeRoutes):
    startFood = foodGrid.copy()
    #print('start food count = ', startFood.count())
    foodLeft = startFood # only for initial while... test, will be reassigned when the first group completes
    groups = []

    while foodLeft.count() != 0:
        groupsProblem = FoodGroupsProblem(position, startFood, problem, walls, mazeRoutes)
        search.bfs(groupsProblem) 
        group, food, endPoints, segments = groupsProblem.getGroupAndRemainingFood()
        foodLeft = food
        finished = foodLeft.count() == 0
        avePos = getAveragePosition(group)
        groups.append(FoodGroup(len(groups), avePos, group, endPoints, segments))
        #print('groupFoodPellets', finished, '# in group', len(group), group, '# remaining', food.count(), 'ave pos = ', avePos)
        
    return groups

class FoodGroup:
    """ encapsulate a food group, will contain all the info for a specific group """
    def __init__(self, fgNum, avePos, pellets, endPoints, segments):
        self.fgNum = fgNum
        self.pellets = pellets
        self.avePos = avePos
        self.endPoints = endPoints
        self.segments = segments
        #print('group', fgNum, 'has # endPoints =', endPoints)
    
    # for convenience to draw the map in debugging
    def getSegmentContainingPoint(self, point):
        for seg in self.segments: 
            if seg.points.count(point) != 0: 
                return seg
                
    def getShortestPathThrough(self, startPos, nextGroup, walls, problem, mazeRoutes):
        # assess the best way through this group from a given position to the next group, 
        # or the furthest point in this group from the start pos if no next group

        if len(self.pellets) == 0:
            print('getShortestPathThrough: group has no food left', self.fgNum)
            sys.exit()
        
        # for now assume a single segment, so take the first one and see if better to start at one end or the other
        segment = self.segments[0]
        
        # either better to go to nearest end of the segment and through segment to end and then on to other group
        # start -> segment near point -> segment far point -> onwards
        # if no other group to consider, this is best
        
        # or better to go to furthest end of the segment and through segment and then on to other group
        # start -> segment far point -> segment near point -> onwards
        
        startToSegNearestPointRoute, thisSegNearestPoint = getMazeRouteToNearestPointInList(startPos, segment.endPoints, walls, problem, mazeRoutes)
        if thisSegNearestPoint == None:
            print('getShortestPathThrough: None for thisSegNearestPoint!!')
            sys.exit()
        
        # default furthest to no route and same as nearest point, only changes if > 1 point in the segment
        if len(segment.points) > 1:        
            thisSegNearestToFurthestPointRoute, thisSegFurthestPoint, ignorePosOnPath = segment.getRouteFromPoint(thisSegNearestPoint)[0]
        else:
            thisSegNearestToFurthestPointRoute, thisSegFurthestPoint = [], thisSegNearestPoint
    
        """
        # somehow will need to get to the furthest pellet in this group
        thisFurthestPelletRoute, thisFurthestPelletPos = getMazeRouteToFurthestPelletInGroupSegment(startPos, segment, walls, problem, mazeRoutes)
        if thisFurthestPelletPos == None:
            print('thisFurthestPelletPos: None for thisFurthestPelletPos')
            sys.exit()
        """

        # see if going to the next group is better from the furthest point, or if it's better from a nearer spot
        if nextGroup != None and len(nextGroup.pellets) != 0:
            if len(nextGroup.endPoints) == 0:
                #print('getShortestPathThrough. no endPoints for next group, using pellets', nextGroup.fgNum)
                nextGroupList = nextGroup.pellets
            else:
                nextGroupList = nextGroup.endPoints
            otherNearestPointRoute, otherNearestPoint = getMazeRouteToNearestPointInList(self.avePos, nextGroupList, walls, problem, mazeRoutes)
            
            #if self.fgNum == 4 and nextGroup.fgNum == 5:
            #    print('getShortestPathThrough. nearest point=', otherNearestPoint, nextGroup.fgNum)
            
            # compare 1:
            # start -> furthest point -> next group
            if (otherNearestPoint == None or thisSegFurthestPoint == None):
                print ('FAILED WITH',thisSegFurthestPoint, otherNearestPoint, nextGroupList)
                sys.exit()
                
            onwardsRouteFromFurthest = mazeRoutes.getRoute(thisSegFurthestPoint, otherNearestPoint)
            
            # only actually need to compare further if there is > 1 pellet in this segment
            if len(segment.points) > 1:
        
                nearFirstLen = len(startToSegNearestPointRoute) + len(thisSegNearestToFurthestPointRoute) + len(onwardsRouteFromFurthest)
                
                # compare 2:
                # start -> furthest point -> nearest point -> next group
                startToSegFurthestPointRoute, notUsedPoint = getMazeRouteToFurthestPointInList(startPos, segment.endPoints, walls, problem, mazeRoutes)
                # already have length of segment above (just in reverse direction)
                onwardsRouteFromNearest = mazeRoutes.getRoute(thisSegNearestPoint, otherNearestPoint)
                furthestThenNearestLen = len(startToSegFurthestPointRoute) + len(thisSegNearestToFurthestPointRoute) + len(onwardsRouteFromNearest)

                if furthestThenNearestLen < nearFirstLen:           
                    # need the reverse route through the segment
                    thisSegFurthestToNearestPointRoute, ignorePoint, ignorePosOnPath = segment.getRouteFromPoint(thisSegFurthestPoint)
                    return (startToSegFurthestPointRoute + thisSegFurthestToNearestPointRoute, thisSegNearestPoint) # + onwardsRouteFromNearest, otherNearestPoint)

        # either no next group, or only one pellet anyway, or the result of the 2nd compare was to go with the furthest
        return (startToSegNearestPointRoute + thisSegNearestToFurthestPointRoute, thisSegFurthestPoint)

    def getShortestPathToEatAllPellets(self, startPos, nextGroup, walls, problem, mazeRoutes):
        
        # the shortest path method just needs a single segment and is quicker
        if len(self.segments) <= 1:
            return self.getShortestPathThrough(startPos, nextGroup, walls, problem, mazeRoutes)
        
        if len(self.pellets) == 0:
            print('getShortestPathToEatAllPellets: group has no food left', self.fgNum)
            sys.exit()

        if nextGroup != None:
            print('############## WORKING ON THIS getShortestPathToEatAllPellets with next group, not started yet')
            sys.exit()
            
        # no nextGroup is easy case, just get the nearest point in the group and start there, tie break won't matter
        # because all points are connected and all have to be eaten
        endPos = None
        
        ######## POSSIBLE REFACTOR NOTE also in getShortestPathThrough
        startToSegNearestPointRoute, thisSegNearestPoint = getMazeRouteToNearestPointInList(startPos, self.pellets, walls, problem, mazeRoutes)
        
        # also pass it the very last move to get there, so doesn't try to double back
        lastDirection = startToSegNearestPointRoute[len(startToSegNearestPointRoute)-1]
        prob = EatAllFoodInOneGroupProblem(thisSegNearestPoint, lastDirection, endPos, self, walls, problem, mazeRoutes)
        routeThrough, routeEndPos = (search.bfs(prob), endPos)
        
        # it's not likely that routeThrough completed with a path because otherwise the easier method would probably have found it 
        if len(routeThrough) == 0:
            routeThrough, routeEndPos = prob.constructCompleteRoute()

        # solution, add route to first point
        return (startToSegNearestPointRoute + routeThrough, routeEndPos) 
        
def getAveragePosition(group):
    totX = 0
    totY = 0
    for x,y in group:
        totX = totX + x
        totY = totY + y
    
    # get the average position for the group, if that's actually a group pos return it
    pos = (totX / len(group), totY / len(group))
    if group.count(pos) != 0:
        return pos
    
    # not a position in the group, take any spot in the group then
    if len(group) < 3:
        return group[0]
    else:
        return group[len(group) / 2]

def getMazeRouteToFurthestPointInList(fromPos, aList, walls, problem, mazeRoutes):
    furthestDist = 0
    furthestRoute = None
    furthestPelletPos = None
    
    for pelletXy in aList:
        route = mazeRoutes.getRoute(fromPos, pelletXy)

        if len(route) >= furthestDist:
            furthestRoute = route
            furthestDist = len(route)
            furthestPelletPos = pelletXy

    return (furthestRoute, furthestPelletPos)
    
def getMazeRouteToNearestPointInList(fromPos, aList, walls, problem, mazeRoutes):
    nearestDist = 9999999
    nearestRoute = None
    nearestPelletPos = None
    
    for pelletXy in aList:
        route = mazeRoutes.getRoute(fromPos, pelletXy)

        if len(route) < nearestDist:
            nearestRoute = route
            nearestDist = len(route)
            nearestPelletPos = pelletXy

    return (nearestRoute, nearestPelletPos)

"""    
def getExactSolutionToAllFoodProblem(problem):
    prob = FoodSearchProblem(problem.startingGameState)
    return search.astar(prob)
"""    
    
def testPathGetsAllFood(actions, position, problem): 

    # a copy of the food at the start, going to be munching that
    food = problem.startingGameState.getFood().copy()

    x,y = position
    
    for action in actions:
        if action == 'West': x = x -1
        if action == 'East': x = x +1
        if action == 'North': y = y +1
        if action == 'South': y = y -1

        if food.asList().count((x,y)): # pellet there, so consume it
            food[x][y] = False 

    if food.count() == 0:
        print('testPathGetsAllFood complete', 'path', actions, 'length', len(actions))
    else:
        print('testPathGetsAllFood complete but there is remaining food', food.asList(), 'path', actions, 'length', len(actions))
    return food.count() == 0

def fillExactHeuristicsForPath(dists, actions, position, problem): 

    # a copy of the food at the start, going to be munching that
    food = problem.startingGameState.getFood().copy()
    
    # init start state, and store its heuristic
    x,y = position
    h = len(actions)
    dists[position+(food.count(),0)] = h
    
    for action in actions:
        h = h -1
        if action == 'West': x = x -1
        if action == 'East': x = x +1
        if action == 'North': y = y +1
        if action == 'South': y = y -1

        if food.asList().count((x,y)): # pellet there, so consume it, (changes the state)
            #print('fillExactHeuristicsForPath consumed some food', (x,y))
            food[x][y] = False 
        
        # store the state now for the position found
        dists[(x,y)+(food.count(),0)] = h

    if food.count() != 0:
        print('fillExactHeuristicsForPath complete, but still have remaining food', food.asList())

"""
def getMazeFurthestFoodPellet(position, pelletList, problem):
    hFurthest = 0
    furthestPellet = None
    for pelletXy in pelletList:
        maze = mazeDistance(pelletXy, position, problem.startingGameState)
        #print('distance from', position, 'to', pelletXy, maze)
        if maze > hFurthest:
            hFurthest = maze
            furthestPellet = pelletXy
    return (furthestPellet, hFurthest)

def getMazeNearestFoodPellet(position, pelletList, problem):
    h = 999999
    p = None
    for pelletXy in pelletList:
        maze = mazeDistance(pelletXy, position, problem.startingGameState)
        if maze < h:
            h = maze
            p = pelletXy
    return (p, h)
def getFurthestFoodPellet(position, pelletList):
    hFurthest = 0
    furthestPellet = None
    for pelletXy in pelletList:
        manhattan = util.manhattanDistance(pelletXy, position)
        if manhattan > hFurthest:
            hFurthest = manhattan
            furthestPellet = pelletXy
    return (furthestPellet, hFurthest)
"""

class HashableList:
    def __init__(self, srcList):
        self.copyList = list()
        for item in srcList: self.copyList.append(item)
    
    def remove(self, item):
        if self.copyList.count(item) != 0:
            self.copyList.remove(item)
        
class Global:
    debugPoint = False

class EatAllFoodInOneGroupProblem:
    """
    Find the path from the start pos to either:
        a) if no end pos, eat all pellets and return whatever position it ends on
        b) or eat all pellets but whenever a decision is needed about direction it favours finishing near/at the end pos
    """
    FOOD_COUNT_IDX = 6
    
    def __init__(self, startPos, lastDirection, preferredEndPos, group, walls, problem, mazeRoutes):
        self.startPos = startPos
        self.lastMoveBeforeStart = lastDirection[0] # just the N,S,W,E
        self.preferredEndPos = preferredEndPos
        self.group = group
        self.walls = walls
        self.problem = problem
        self.mazeRoutes = mazeRoutes
        self.completePaths = []

    """
        packed = foodGrid.packBits()
        recon = game.reconstituteGrid(packed)
        print('packed grid', packed, 'recon grid', recon)
        drawDebugMap(recon, groupsList, walls, position)
        
    A search state in this problem is a tuple ( position, segNum, path, positionOnPath ) where
      position: a tuple (x,y) current search pos
      path : a compressed path to continue on this path in the form, for eg. 'NWSEEEE'
      segNum: the segment num id that the position is on
      positionOnPath: index of position on the path
      packedFoodGrid: the grid at the time of the state
      foodCount: saves on an unpack just to determine goal state
      moves: the sequences that got it here
    """
    def getStartState(self):
        segment = self.group.getSegmentContainingPoint(self.startPos)
        if segment.endPoints.count(self.startPos) == 0:
            print('EatAllFoodInOneGroupProblem.getStartState: starting segment not at an end point, TODO!', segment, 'startPos', self.startPos, 'end points', segment.endPoints)
            sys.exit()

        route = segment.getCompressedRouteFromPoint(self.startPos) 
        path, ignorePosOnPath = route[0] # for now not expecting/supporting starting a group anywhere but the end of one of the segments, 
                                         # this is probably not the best in future, in which case something will be done about the above error condition
                                         # and will have to incorporate branching from a single segment at the outset
        path = path + 'F' # for finished
        
        # assemble a new grid for just this group
        foodGrid = game.Grid(self.walls.width, self.walls.height, False)
        for x,y in self.group.pellets:
            if (x,y) != self.startPos: # don't add the start node, since it's eaten by default
                foodGrid[x][y] = True
            
        packedFood = foodGrid.packBits()
        print('EatAllFoodInOneGroupProblem.getStartState: start segment is', segment.segNum, 'startPos', self.startPos, 'path', path, 'len pellets -1', len(self.group.pellets)-1, 'food in grid', foodGrid.count())

        return (self.startPos, self.lastMoveBeforeStart, path, segment.segNum, 0, packedFood, foodGrid.count(), 's'+str(segment.segNum)+':', '') # last is the moves to get here 

    def isGoalState(self, state):
        foodLeft = state[EatAllFoodInOneGroupProblem.FOOD_COUNT_IDX]
        if foodLeft == 0:
            print('isGoalState says no more food left and done!')
        return foodLeft == 0 # no more food in group, unlikely to end this way, but it would be very nice

    def getFromDirectionFromLastMove(self, direction):
        if direction == 'N': return 'S'
        elif direction == 'S': return 'N'
        elif direction == 'W': return 'E'
        elif direction == 'E': return 'W'

    def walkToBranchOrEnd(self, position, path, segNum, positionOnPath, foodGrid, expandedSegs):
        
        if Global.debugPoint: print('DEBUG POINT: start walk at position', position, 'path', path, 'segNum', segNum, 'pos on path', positionOnPath)
        
        atEndOrBranch = False
        moves = ''
        currentDir = None
        movesInCurrDir = 0
        stepsTaken = 0
        
        while not atEndOrBranch:
            direction = path[positionOnPath]
            
            # change position to that place
            fromDirection = self.getFromDirectionFromLastMove(direction)
            position = getPositionFromDirection(direction, position)

            # advance on the path and inc num steps taken
            stepsTaken = stepsTaken + 1
            positionOnPath = positionOnPath + 1
            
            # add this move to the list
            if currentDir == direction:
                movesInCurrDir = movesInCurrDir + 1
            else:
                # add the latest move to the list
                if currentDir != None:
                    moves = moves + currentDir + str(movesInCurrDir) + ':'
                
                currentDir = direction
                movesInCurrDir = 1
                
            # consume the food on the spot, if there is some
            x,y = position
            foodGrid[x][y] = False
            
            # check for end of path
            if len(path) - 1 - positionOnPath == 0:
                atEndOrBranch = True
            else:
                if Global.debugPoint: print('      DEBUG POINT: call isBranching',position, segNum, positionOnPath, path, fromDirection, expandedSegs)
                atEndOrBranch, ignoreBranches = self.isBranching(position, segNum, positionOnPath, path, fromDirection, expandedSegs)
                    
            #print('moved one, see map next')
            #drawDebugMap(foodGrid, [self.group], self.walls, position)
            #time.sleep(0.05)
                        
        # add the last sequence of moves to the list (out of the loop)
        moves = moves + currentDir + str(movesInCurrDir) + ':'
        
        return (position, positionOnPath, foodGrid.count(), moves, currentDir, stepsTaken)
    
    def isBranching(self, position, segNum, positionOnPath, path, fromDirection, expandedSegs):
        
        atEndOfPath = len(path) - 1 - positionOnPath == 0
        
        #print('isBranching test: now at position', position, 'pos on path', positionOnPath, 'at end', atEndOfPath, 'path len', len(path))
        
        # look for a branch in any direction that is not where came from and not also the next point on this path (so only 2 directions count)
        branches = []
        for testDir in ['N', 'S', 'E', 'W']:
            if fromDirection != testDir and (atEndOfPath or testDir != path[positionOnPath + 1]): # FOR BACKWARDS DIRECTION THIS WOULD NOT BE CORRECT
                testPos = getPositionFromDirection(testDir, position)
                
                if Global.debugPoint: print('      DEBUG POINT:isBranching testDir loop, position', position, 'testPos', testPos)
                
                # not a wall and not in the current segment
                testx, testy = testPos
                if not self.walls[testx][testy]:
                    segment = self.group.getSegmentContainingPoint(testPos)
                    if segment == None:
                        print('EatAllFoodInOneGroupProblem.walkToBranchOrEnd: hit a spot where branch in maze which is not part of the group, need to code for it, testPos=', 
                                                                                        testPos, 'from dir=', fromDirection, 'problem direction=', testDir)
                    elif segment.segNum != segNum:
                        branches.append([testDir, testPos, segment.segNum])
        
        if len(branches) > 0:
            return (True, branches)
        else:
            return (False, None)
        
    def getSuccessors(self, state):
        successors = []
        
        statePos, stateLastDir, statePath, stateSegNum, statePosOnPath, statePackedFood, stateFoodLeftCount, stateExpandedSegs, stateMoves = state
        if stateExpandedSegs.find('s'+str(stateSegNum)+':') == -1:
            stateExpandedSegs = stateExpandedSegs + 's'+str(stateSegNum)+':'
        isEndOfPath = len(statePath) -1 - statePosOnPath == 0 
        hasBeenToEndOfPath = len(statePath) - 1 - statePosOnPath <= 0 # so can plot walking back along it, if do use that
        
        Global.debugPoint = False#statePos == (3,3)

        print('EatAllFoodInOneGroupProblem.getSuccessors: statePos',statePos,'expanded segs=', 
            stateExpandedSegs, 'stateSegNum',stateSegNum, 'path=', statePath, 'steps left on path', len(statePath) - 1 - statePosOnPath, 'at end', isEndOfPath, 
            'lastDir', stateLastDir, 'stateMoves', stateMoves)
        
        #drawDebugMap(game.reconstituteGrid(statePackedFood), [self.group], self.walls, statePos)
        #stopForInput(None)
        if self.walls[statePos[0]][statePos[1]]: 
            print('ERROR: how can statePos be on a wall??!', statePos)
            sys.exit()
        
        # unless end of path add this path as a successor
        if hasBeenToEndOfPath:
            self.completePaths.append([statePos, stateMoves])
        else: 
            # first successor is just walk to branch or end of path
            # unpack the food grid, just for this successor (since each will affect their own food grid differently, they each need a copy)
            foodGrid = game.reconstituteGrid(statePackedFood)

            nextPos, nextPosOnPath, nextFoodLeftCount, moves, lastDir, stepsTaken = self.walkToBranchOrEnd(statePos, statePath, stateSegNum, statePosOnPath, foodGrid, stateExpandedSegs)
            successors.append(( (nextPos, lastDir, statePath, stateSegNum, nextPosOnPath, foodGrid.packBits(), nextFoodLeftCount, stateExpandedSegs, stateMoves + moves), #state
                                    moves, stepsTaken)) # eg. 'W4:N2:E1:'

            if Global.debugPoint: print('DEBUG POINT: added successor of the same segment, segNum=', stateSegNum)
            #stopForInput('added a successor, see map next')
            #time.sleep(0.5)
            #drawDebugMap(foodGrid, [self.group], self.walls, nextPos)
        
        # test for branches
        isBranch, branches = self.isBranching(statePos, stateSegNum, statePosOnPath, statePath, self.getFromDirectionFromLastMove(stateLastDir), stateExpandedSegs)
        if Global.debugPoint: print('      DEBUG POINT: isbranch', isBranch, branches, statePos, stateSegNum, statePosOnPath, statePath, stateLastDir, stateExpandedSegs)
        
        if isBranch:
            for branch in branches:
                # eg ['N', (11, 2), 2])
                moveToGetThere, startSegPos, segNum = branch
                segment = self.group.getSegmentContainingPoint(startSegPos)
                if Global.debugPoint: print('      DEBUG POINT: got back a branch', branch, 'startSegPos', startSegPos)
                if segment == None:
                    print('EatAllFoodInOneGroupProblem.getSuccessors: hit a spot where branch in maze which is not part of the group, need to code for it, perhaps create a join segment?')
                    sys.exit()

                if stateExpandedSegs.find('s'+str(segment.segNum)+':') != -1: 
                    # already branched to this segment, don't re-add it
                    print('EatAllFoodInOneGroupProblem.getSuccessors: WARNING at point on expanded segment already traveled on, currSeg=',
                        stateSegNum, 'branch seg', segment.segNum, 'stateExpandedSegs', stateExpandedSegs)

                else:
                    route = segment.getCompressedRouteFromPoint(startSegPos) # allowing for > 1 direction from path (in case joining it somewhere in the middle)
                    if Global.debugPoint: print('      DEBUG POINT: compressed route from point, num routes', len(route))
                    for path, posOnPath in route:
                        if Global.debugPoint: print('      DEBUG POINT: adding another successor of the same segment', (segment.segNum == stateSegNum), 'segNum=', segment.segNum, 'path', path, 'posOnPath', posOnPath,)
                        path = moveToGetThere + path + 'F' # for finished, posOnPath is usually 0 so adding one at the start is ok... this breaks down when join a path mid way though
                        foodGrid = game.reconstituteGrid(statePackedFood)
                        nextPos, nextPosOnPath, nextFoodLeftCount, moves, lastDir, stepsTaken = self.walkToBranchOrEnd(statePos, path, segNum, posOnPath, foodGrid, stateExpandedSegs)
                        if Global.debugPoint and nextPos == (3,2): 
                            print('      DEBUG POINT: statePos, ', statePos, 'path', path, 'nextPos', nextPos, 'nextPosOnPath', nextPosOnPath)
                            sys.exit()
                        successors.append(( (nextPos, lastDir, path, segNum, nextPosOnPath, foodGrid.packBits(), nextFoodLeftCount, stateExpandedSegs, stateMoves + moves), moves, stepsTaken)) 
                        
                        #stopForInput('added a branch successor, see map next')
                        #drawDebugMap(foodGrid, [self.group], self.walls, nextPos)
                
        
        if Global.debugPoint:
            for successor in successors:
                print('successor', successor)
            sys.exit()
            
        return successors
    
    def constructCompleteRoute(self):
        """
        the interesting part, where the pruned routes are assembled in a good order
        """
        self.decompressCompletedPaths()
        endPos, basePath = self.pruneBaseFinalPath()
        print('basePath', basePath, 'endPos', endPos)
        
        tree = self.getTree(basePath, endPos)
        
    def getTree(self, basePath, endPos):
        # compare basePath to each of the other paths in the set and prune and join to create a tree
        # 
        tree = Branch(basePath, endPos, 0)
        
        print(basePath)
        while len(self.completePaths) > 0:
            self.joinLatestDivergingBranch(tree, 0) # start index (only join anything further up the tree than this

        tree.pellets.append(self.startPos)
        tree.makePellets(self.startPos)
        
        """
        THIS IS ALL SUBJECT TO CHANGE, ACTUALLY BEFORE THIS TOO, BUT LEAVE THIS NEXT BIT TILL SORT OUT THE PREV STUFF
        # for debugging, make a 'groupsList'
        branchesList = []
        self.addBranchesToList(branchesList, tree)
    
        # assemble a new grid for just this group
        foodGrid = game.Grid(self.walls.width, self.walls.height, False)
        for x,y in self.group.pellets: foodGrid[x][y] = True
        drawDebugMap(foodGrid, branchesList, self.walls, self.problem.startingGameState.getPacmanPosition())
        tree.walkRoute2(foodGrid, self.walls) #, 0) # just for debugging, or could be useful...?
        """
            
    def addBranchesToList(self, branchesList, branch):
        branchesList.append(branch)
        for nextBranch, ignoreIdx in branch.branches:
            self.addBranchesToList(branchesList, nextBranch)
        print(branch.fgNum, branch.pellets)
            
    def joinLatestDivergingBranch(self, branch, startIdx):
        
        findIdx = len(branch.path) -1
        while findIdx > startIdx:

            repeat = True
            while repeat: # loop because once find one, the next perhaps needs to be added to that one, not this... so there's a recursion to complete
                          # that one before the next here
                candPath = None
                for route in self.completePaths:
                    endPos, path = route
                    
                    if len(path) > findIdx and path[:findIdx] == branch.path[:findIdx]: # long enough and matches to this point
                        if candPath == None or len(path) > len(candPath[1]):
                            candPath = route
                
                if candPath != None:
                    newBranch = Branch(candPath[1], candPath[0], findIdx)
                    branch.addBranch(newBranch, findIdx)
                    self.completePaths.remove(candPath)
                    print(' ' * newBranch.startPositionOnPath + newBranch.path[newBranch.startPositionOnPath:]) 
                    #print(newBranch.path) 
                    # make a recursive call for that new branch
                    self.joinLatestDivergingBranch(newBranch, findIdx +1)
                else:
                    repeat = False
            
            findIdx = findIdx -1 # dec the index to step back from the end
        
    def pruneBaseFinalPath(self):
        chosenPath = None
        
        if self.preferredEndPos != None:
            for route in self.completePaths:
                endPos, path = route
                if endPos == self.preferredEndPos:
                    print('constructCompleteRoute: choosing based on preferred ends at', endPos, 'path', path)
                    chosenPath = route
                    
        else:
            # need to choose a route that will give the best path through, the wrong one will be way off optimal!
            # assuming the longest one is best
            # the routes were added based on number of moves and branches involved, to find the longest one add up all the numbers
            longestSteps = 0
            for route in self.completePaths:
                endPos, path = route
                path = ':'+path
                
                if len(path) > longestSteps:
                    longestSteps = len(path)
                    chosenPath = route
            
        self.completePaths.remove(chosenPath)
        return chosenPath

    def decompressCompletedPaths(self):
        uncompressedPaths = []
        for route in self.completePaths:
            endPos, path = route
            path = ':'+path
            uncompressedPath = ''
            findIdx = 0
            while findIdx != -1:
                nextIdx = path.find(':', findIdx + 1)
                nextVal = path[findIdx + 1 : nextIdx]
                if len(nextVal) > 0:
                    uncompressedPath = uncompressedPath + nextVal[0:1] * int(nextVal[1:])
                findIdx = nextIdx
                
            uncompressedPaths.append([endPos, uncompressedPath])
        
        self.completePaths = uncompressedPaths
        
def stopForInput(msg):
    if msg != None:
        print(msg)
    raw_input("Press enter to continue...")
    
class Branch:
    
    def __init__(self, path, endPos, startPositionOnPath):
        self.path = path
        self.endPos = endPos
        self.startPositionOnPath = startPositionOnPath
        self.pellets = []
        self.branches = []
        # for debugging the map
        self.fgNum = startPositionOnPath
        self.segments = []
        
    def makePellets(self, startPos):
        
        pos = startPos
        for i in range(0,len(self.path)):
            if i >= self.startPositionOnPath:
                pos = getPositionFromDirection(self.path[i], pos)
                self.pellets.append(pos)
                
                for branch, atIdx in self.branches:
                    if atIdx == i + 1:
                        branch.makePellets(pos)

    def walkRoute(self, foodGrid, walls, startAt):

        for i in range(startAt, len(self.path)):
            print(self.fgNum)
            drawDebugMap(foodGrid, [self], walls, self.pellets[i - startAt])
            time.sleep(1)
            
            for branch, atIdx in self.branches:
                if atIdx == i:
                    branch.walkRoute(foodGrid, walls, i)            
    
    def walkRoute2(self, foodGrid, walls): # walk complete path before walking the branches

        for pellet in self.pellets:
            print(self.fgNum)
            drawDebugMap(foodGrid, [self], walls, pellet)
            time.sleep(.5)
            
        for branch, atIdx in self.branches:
            branch.walkRoute2(foodGrid, walls)            
    
    def addBranch(self, branch, atIdx):
        self.branches.append((branch, atIdx))
        
class FoodGroupsOrderProblem:
    """
    A search problem that generates the best (least overall distance) for moving
    from one group to another
    """
    def __init__(self, startPos, groupsList, walls, problem, mazeRoutes):
        self.startPos = startPos
        self.groupsList = groupsList
        self.walls = walls
        self.problem = problem
        self.mazeRoutes = mazeRoutes
        self.firstPaths = dict()
        self.completePaths = []

    def getStartState(self):
        return ('') # state is the list of groups visited

    def isGoalState(self, state):
        return False # not determined by the state, but instead when the fringe is empty and the problem terminates

    def getSuccessors(self, state):
        successors = []
        paths = state
        
        for group in self.groupsList:
            
            # first group there's nothing in paths yet, go from start to this group
            if len(paths) == 0:
                #route = getMazeRoute(self.startPos, group.avePos, self.walls, self.problem)
                route, furthestPos = getMazeRouteToFurthestPointInList(self.startPos, group.endPoints, self.walls, self.problem, self.mazeRoutes)
                successors.append((('<'+str(group.fgNum)+'>'), '', 1))
                self.firstPaths[group.fgNum] = (route, furthestPos)
                
            # otherwise only add groups not already included in the path
            elif paths.count('<'+str(group.fgNum)+'>') == 0:
                
                # chop out the number of the last group in the string of paths
                prevGroupNum = paths[paths.rindex('<')+1:paths.rindex('>')]
                prevGroup = getGroupWithNum(int(prevGroupNum), self.groupsList)
                successors.append((paths + '<'+str(group.fgNum)+'>', '', 1))
                
        # no more successors added, then it's a complete path, add to list
        if len(successors) == 0:
            self.completePaths.append(paths)

        return successors

    def getShortestPath(self):
        """returns the shortest path"""
        shortestDist = 999999
        shortestPath = None
        groupOrder = None
        
        for path in self.completePaths:
            
            # split the string of paths into nodes
            nodes = path.split('>')
            
            fullRoute = []
            prevPos = self.startPos
            dist = 0
            
            for i in range(0, len(nodes)):
                node = nodes[i]
                
                if len(node) > 1: # ignore '' 

                    group = getGroupWithNum(int(node[1:]), self.groupsList)
                    
                    # see if there's a next group to include
                    nextGroup = None
                    j = i + 1
                    if j < len(nodes):
                        nextNode = nodes[j]
                        if len(nextNode) > 1: # ignore '' 
                            nextGroup = getGroupWithNum(int(nextNode[1:]), self.groupsList)
                        
                    route, prevPos = group.getShortestPathThrough(prevPos, nextGroup, self.walls, self.problem, self.mazeRoutes)
                    dist = dist + len(route)
                    fullRoute = fullRoute + route

            # get the distance to the first group as stored previously
            if dist < shortestDist:
                shortestDist = dist
                shortestPath = fullRoute
                groupOrder = path
                #print('completed path is new shortest', path, 'dist', shortestDist, 'fullroute', shortestPath)
        
        return (shortestPath, groupOrder)

def getPositionFromDirection(direction, (x,y)):
    if direction == 'N': return (x, y+1)
    elif direction == 'S': return (x, y-1)
    elif direction == 'W': return (x-1, y)
    elif direction == 'E': return (x+1, y)
    
def getGroupWithNum(num, groupsList):
    for group in groupsList:
        if group.fgNum == num:
            return group

class FoodGroupSegment:
    # a piece of a food group with 2 end points (branches off it are separate segments)
    def __init__(self, num, aPoint):
        self.segNum = num
        self.points = [aPoint]
        self.endPoints = [aPoint]
        self.path = []

    def getRouteFromPoint(self, point):
        doReverse = True # default send to 2 paths in each direction 
        doForwards = True # unless turned off (which would be normal because mostly will be at the start, unless coming from a side into a branch spot
        
        if point == self.endPoints[0]:
            doReverse = False
        elif point == self.points[len(self.points)-1]: # in case no end point logged
            doForwards = False
            
        route = []
        if doForwards:
            route.append( (self.path, self.points[len(self.points)-1], self.points.index(point)) )
        
        if doReverse:
            path = getReverseRoute(self.path)
            posOnPath = (len(self.points) - 1 - self.points.index(point))
            route.append( (path, self.endPoints[0], posOnPath) )
            
            if (posOnPath != 0):
                print('FoodGroupSegment.getRouteFromPoint: reverse route: pointOnPath', posOnPath, 'path', path, 'point', point, 'all points', self.points)

        return route
    
    def getCompressedRouteFromPoint(self, point):
        route = self.getRouteFromPoint(point)
        
        compressedRoute = []
        for path, ignorePoint, position in route:
            res = ''
            if position > 0:
                newPath = path[position:]
                print('FoodGroupSegment.getCompressedRouteFromPoint: unusual case, chopping path because position > 0, path', path, 'position', position, 'newPath', newPath)
                path = newPath
                position = 0
            for direction in path:
                res = res + direction[0] # just keep the first letter
            
            compressedRoute.append((res, position))
            
        return compressedRoute
        
    def isClosed(self):
        return len(self.endPoints) == 2
        
    def addEndPoint(self, endPoint):
        if self.isClosed():
            print('FoodGroupSegment.addEndPoint: attempted to add end point to closed segment, failed!, segNum='+str(self.segNum))
        else:
            self.endPoints.append(endPoint)

    def addPoint(self, point):
        if self.isClosed():
            print('FoodGroupSegment.addPoint: attempted to add to closed segment, failed!, segNum='+str(self.segNum))
        else:
            self.points.append(point)
    
    def arrangePath(self):
        # start at one end and make the path to the other end
        if len(self.endPoints) == 1:
            #print('FoodGroupSegment.arrangePath: none applies, only one point, segNum='+str(self.segNum))
            return
        if len(self.endPoints) == 0:
            print('FoodGroupSegment.arrangePath: problem there should be at least 1 end point, failed!, segNum='+str(self.segNum))
            return
        
        startPoint = self.endPoints[0]
        orderedPoints = [startPoint]
        
        while orderedPoints[len(orderedPoints)-1] != self.endPoints[1]: # loop until the last point on the path is the 2nd end point
            self.addNextPointToPath(orderedPoints)
        
        if len(orderedPoints) != len(self.points):
            print('FoodGroupSegment.arrangePath: PROBLEM - ORDERED POINTS AND POINTS DIFFER, endPoints', self.endPoints, 'ordered', orderedPoints, 'points', self.points)
            sys.exit()
        
        self.points = orderedPoints
        
    def addNextPointToPath(self, orderedPoints):
        x,y = orderedPoints[len(orderedPoints)-1] 
        
        for direction in [Directions.NORTH, Directions.SOUTH, Directions.EAST, Directions.WEST]:
            dx, dy = Actions.directionToVector(direction)
            nextx, nexty = int(x + dx), int(y + dy)
            if orderedPoints.count((nextx, nexty)) == 0 and self.points.count((nextx, nexty)) == 1:
                orderedPoints.append((nextx, nexty))
                self.path.append(direction)
                return

        print('FoodGroupSegment.addNextPointToPath: added no point to path, segment endPoints', self.endPoints, 'points/count', self.points, len(self.points), ' ordered/count', orderedPoints, len(orderedPoints), ' could not add from x,y', (x,y))
        sys.exit()
        
class FoodGroupsProblem:
    """
    A search problem that generates a group of food pellets that are all connected
    in a list, removing food pellets from the list as they are found. Kind of a hacky
    way to take advantage of the fact that searching is already working, just bend
    it to produce a group of pellets.
    
    When there are no more connected pellets or no more food in the list the problem
    ends, ie. that's the goal state

    A search state in this problem is a tuple food position ( x, y ) 
      The problem should exit with a group of touching pellets and either drop out
      of the search with an empty array (because the fringe is empty after finding
      no more touching pellets) or with an empty food list, because they've
      all been grouped already
    """
    def __init__(self, position, food, problem, walls, mazeRoutes):
        self.startPos = position
        self.food = food
        self.problem = problem
        self.walls = walls
        self.mazeRoutes = mazeRoutes
        self.group = []
        self.endPoints = []
        self.lastDirection = Directions.NORTH # build the paths in straight lines if possible

    def getStartState(self):
        ignore, (x,y) = getMazeRouteToNearestPointInList(self.startPos, self.food.asList(), self.walls, self.problem, self.mazeRoutes)
        self.group.append((x, y))
        self.food[x][y] = False
        self.segments = [FoodGroupSegment(1, (x,y))] # start a segment, the first point is automatically an end point
        return (x, y)

    def isGoalState(self, state):
        return self.food.count() == 0

    def addToSegment(self, prevPoint, point, isBranching):
        # branching means always start a new segment
        if isBranching:
            self.segments.append(FoodGroupSegment(len(self.segments)+1, point))
            #print('created a branch between points', prevPoint, point)

        else:
            # try to find a previous segment to put the point in
            useSeg = self.getSegmentForPoint(prevPoint)
            
            # if none found, create a new segment for it
            if useSeg == None:
                self.segments.append(FoodGroupSegment(len(self.segments)+1, point))
            else:
                useSeg.addPoint(point)
    
    def getSegmentForPoint(self, point):
        for seg in self.segments: 
            if not seg.isClosed() and seg.points.count(point) != 0: # the prev point is in the segment
                return seg
        
    def atePellet(self, state, direction, successors):
        x,y = state
        dx, dy = Actions.directionToVector(direction)
        nextx, nexty = int(x + dx), int(y + dy)
        if self.food[nextx][nexty]:
            self.group.append((nextx, nexty))
            self.food[nextx][nexty] = False
            self.addToSegment(state, (nextx, nexty), len(successors) > 0) # already added one means we're branching now
            successors.append( ( (nextx, nexty), direction, 1) )
            # store the direction so can keep going that way by preference
            self.lastDirection = direction
                
            # special case, if it's the last pellet it must also be an end point
            if self.food.count() == 0:
                self.addEndPoint(state, (nextx, nexty))
    
    def addEndPoint(self, prevPos, point):        
        self.endPoints.append(point)
        
        useSeg = self.getSegmentForPoint(point)
        if useSeg == None:
            print('FoodGroupsProblem.addEndPoint: no segment found to add end point to', point)
        elif useSeg.endPoints.count(point) == 0: # don't add it if it's already there
            useSeg.addEndPoint(point)
        
    def getSuccessors(self, state):
        successors = []
        # try direction was travelling first
        self.atePellet(state, self.lastDirection, successors)
        
        for direction in [Directions.NORTH, Directions.SOUTH, Directions.EAST, Directions.WEST]:
            if direction != self.lastDirection:
                self.atePellet(state, direction, successors) 
                
        # is this an end point, only if it has < 2 successors at start or none after that
        # (because successors must have food on them, and they're removed at each call)
        if len(successors) == 0 or (len(successors) == 1 and len(self.group) == 2): # 1 successor added to group, plus the starter 1 means we by chance started on an endpoint
            self.addEndPoint(None, state)
            
        return successors

    def getGroupAndRemainingFood(self):
        self.makeSegmentPaths()
        return (self.group, self.food, self.endPoints, self.segments)
        
    def makeSegmentPaths(self):
        # walk the end points to collect up segments, biased in the direction already travelling
        #totPoints = 0 #for sanity checking
        for seg in self.segments:
            #totPoints = totPoints + len(seg.points)
            seg.arrangePath()
            #print('walking segment, endPoints', seg.endPoints, 'points', seg.points, 'path', seg.path)
        
        #if totPoints == len(self.group):
        #    print('yay, correct num of totPoints')
        #else:
        #    print('OH NO MISSING SOMETHING, points', len(self.group), 'totted up', totPoints)
        #    sys.exit()
        
class ClosestDotSearchAgent(SearchAgent):
    "Search for all food using a sequence of searches"
    def registerInitialState(self, state):
        self.actions = []
        currentState = state
        while(currentState.getFood().count() > 0):
            nextPathSegment = self.findPathToClosestDot(currentState) # The missing piece
            self.actions += nextPathSegment
            for action in nextPathSegment:
                legal = currentState.getLegalActions()
                if action not in legal:
                    t = (str(action), str(currentState))
                    raise Exception, 'findPathToClosestDot returned an illegal move: %s!\n%s' % t
                currentState = currentState.generateSuccessor(0, action)
        self.actionIndex = 0
        print 'Path found with cost %d.' % len(self.actions)

    def findPathToClosestDot(self, gameState):
        """
        Returns a path (a list of actions) to the closest dot, starting from
        gameState.
        """
        # Here are some useful elements of the startState
        startPosition = gameState.getPacmanPosition()
        food = gameState.getFood()
        walls = gameState.getWalls()
        problem = AnyFoodSearchProblem(gameState)

        "*** YOUR CODE HERE ***"
        #Project 1: Q8
        return search.bfs(problem)

class AnyFoodSearchProblem(PositionSearchProblem):
    """
    A search problem for finding a path to any food.

    This search problem is just like the PositionSearchProblem, but has a
    different goal test, which you need to fill in below.  The state space and
    successor function do not need to be changed.

    The class definition above, AnyFoodSearchProblem(PositionSearchProblem),
    inherits the methods of the PositionSearchProblem.

    You can use this search problem to help you fill in the findPathToClosestDot
    method.
    """

    def __init__(self, gameState):
        "Stores information from the gameState.  You don't need to change this."
        # Store the food for later reference
        self.food = gameState.getFood()

        # Store info for the PositionSearchProblem (no need to change this)
        self.walls = gameState.getWalls()
        self.startState = gameState.getPacmanPosition()
        self.costFn = lambda x: 1
        self._visited, self._visitedlist, self._expanded = {}, [], 0 # DO NOT CHANGE

    def isGoalState(self, state):
        """
        The state is Pacman's position. Fill this in with a goal test that will
        complete the problem definition.
        """
        x,y = state

        "*** YOUR CODE HERE ***"
        #Project 1: Q8
        return self.food.asList().count(state) != 0

def mazeDistance(point1, point2, gameState):
    """
    Returns the maze distance between any two points, using the search functions
    you have already built. The gameState can be any game state -- Pacman's
    position in that state is ignored.

    Example usage: mazeDistance( (2,4), (5,6), gameState)

    This might be a useful helper function for your ApproximateSearchAgent.
    """
    x1, y1 = point1
    x2, y2 = point2
    walls = gameState.getWalls()
    assert not walls[x1][y1], 'point1 is a wall: ' + str(point1)
    assert not walls[x2][y2], 'point2 is a wall: ' + str(point2)
    prob = PositionSearchProblem(gameState, start=point1, goal=point2, warn=False, visualize=False)
    return len(search.bfs(prob))

# multiAgents.py
# --------------
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


from util import manhattanDistance
from game import Directions, Actions
import random, util

from game import Agent

class ReflexAgent(Agent):
    """
      A reflex agent chooses an action at each choice point by examining
      its alternatives via a state evaluation function.

      The code below is provided as a guide.  You are welcome to change
      it in any way you see fit, so long as you don't touch our method
      headers.
    """
    def __init__(self):
        self.nearestFoodPath = None # for parking the latest best path to food (cleared whenever another action is chosen

    def getAction(self, gameState):
        """
        You do not need to change this method, but you're welcome to.

        getAction chooses among the best options according to the evaluation function.

        Just like in the previous project, getAction takes a GameState and returns
        some Directions.X for some X in the set {North, South, West, East, Stop}
        """
        # Collect legal moves and successor states
        legalMoves = gameState.getLegalActions()

        # Choose one of the best actions
        scores = [self.evaluationFunction(gameState, action) for action in legalMoves]
        bestScore = max(scores)
        bestIndices = [index for index in range(len(scores)) if scores[index] == bestScore]
        chosenIndex = random.choice(bestIndices) # Pick randomly among the best

        "Add more of your code here if you want to"
        if bestScore == 0:
            if self.nearestFoodPath == None or len(self.nearestFoodPath) == 0:
                self.nearestFoodPath = self.findPathToNearestFood(gameState)
            return self.nearestFoodPath.pop(0)
        else:
            self.nearestFoodPath = None
            return legalMoves[chosenIndex]

    def evaluationFunction(self, currentGameState, action):
        """
        Design a better evaluation function here.

        The evaluation function takes in the current and proposed successor
        GameStates (pacman.py) and returns a number, where higher numbers are better.

        The code below extracts some useful information from the state, like the
        remaining food (newFood) and Pacman position after moving (newPos).
        newScaredTimes holds the number of moves that each ghost will remain
        scared because of Pacman having eaten a power pellet.

        Print out these variables to see what you're getting, then combine them
        to create a masterful evaluation function.
        """
        # Useful information you can extract from a GameState (pacman.py)
        successorGameState = currentGameState.generatePacmanSuccessor(action)
        newPos = successorGameState.getPacmanPosition()
        newFood = successorGameState.getFood()
        newGhostStates = successorGameState.getGhostStates()
        newScaredTimes = [ghostState.scaredTimer for ghostState in newGhostStates]

        "*** YOUR CODE HERE ***"
        # newFood is a grid, see if the move lowers the count
        foodValue = 0
        if newFood.count() < currentGameState.getFood().count(): foodValue = 10

        # scared times are how long the ghosts will remain scared, anything over a small value (say 3) is very nice, no bother with that ghost
        scaredValue = min(newScaredTimes)

        evadeValue = 0
        evadeWeight = 0
        
        if scaredValue < 5:                # only care about the ghosts if they're dangerous
            scaredWeight = 0
            
            for gs in newGhostStates:      # check for a ghost 1 position away, that means the walls won't matter
                dist = util.manhattanDistance(gs.getPosition(), newPos)

                if dist <= 2:              # within 2 of a ghost, wanna probably move it, but depends on the direction
                    evadeValue = -50
                    evadeWeight = .3

                    dx,dy = Actions.directionToVector(action)
                    newDist = util.manhattanDistance(gs.getPosition(), (newPos[0] + dx, newPos[1] + dy))
                    if min(dist, newDist) <= 1:
                        evadeValue = -100
                        evadeWeight = 1
                        break
                    
        else:
            scaredWeight = .8
        
        foodWeight = 1 - max(evadeWeight, scaredWeight)
        foodValue *= foodWeight
        scaredValue *= scaredWeight
        evadeValue *= evadeWeight
        
        # often there's no clear next action, no ghost or food near enough, if the values are all 0, figure out any direction that leads to close food
        # which means a search, try bfs
        totalScore = foodValue + evadeValue # ignore scared value because unless tell him what to do he will vacilate + scaredValue 
        
        #if totalScore != 0:
        #    print 'action=%s weights food=%f, scared=%f, evade=%f. values food=%f, scared=%f, evade=%f' % (action, foodWeight, scaredWeight, evadeWeight, foodValue, scaredValue, evadeValue) 
            
        return totalScore

    def findPathToNearestFood(self, gameState):

        startPos = gameState.getPacmanPosition()
        food = gameState.getFood()
        walls = gameState.getWalls()
        
        closed = set()
        fringe = util.PriorityQueue()
        # put each valid action onto the stack
        fringe.push([startPos, [], 0],0)	

        while not fringe.isEmpty():
            statePos,actions,cost = fringe.pop()

            # goal is food there
            if food[statePos[0]][statePos[1]]:
                return actions
            
            # expand not in closed
            if not statePos in closed:

                closed.add(statePos)
                
                for action in [Directions.NORTH, Directions.SOUTH, Directions.EAST, Directions.WEST]:
                    x,y = statePos
                    dx, dy = Actions.directionToVector(action)
                    nextx, nexty = int(x + dx), int(y + dy)
                    if not walls[nextx][nexty]:
                        nextActions = actions[:]
                        nextActions.append(action)
                        nextState = (nextx, nexty)
                        fringe.push([nextState, nextActions, cost+1], cost+1)
            
def scoreEvaluationFunction(currentGameState):
    """
      This default evaluation function just returns the score of the state.
      The score is the same one displayed in the Pacman GUI.

      This evaluation function is meant for use with adversarial search agents
      (not reflex agents).
    """
    return currentGameState.getScore()

class MultiAgentSearchAgent(Agent):
    """
      This class provides some common elements to all of your
      multi-agent searchers.  Any methods defined here will be available
      to the MinimaxPacmanAgent, AlphaBetaPacmanAgent & ExpectimaxPacmanAgent.

      You *do not* need to make any changes here, but you can if you want to
      add functionality to all your adversarial search agents.  Please do not
      remove anything, however.

      Note: this is an abstract class: one that should not be instantiated.  It's
      only partially specified, and designed to be extended.  Agent (game.py)
      is another abstract class.
    """

    def __init__(self, evalFn = 'scoreEvaluationFunction', depth = '2'):
        self.index = 0 # Pacman is always agent index 0
        self.evaluationFunction = util.lookup(evalFn, globals())
        self.depth = int(depth)

class MinimaxAgent(MultiAgentSearchAgent):
    """
      Your minimax agent (question 2)
    """

    def getAction(self, gameState):
        """
          Returns the minimax action from the current gameState using self.depth
          and self.evaluationFunction.

          Here are some method calls that might be useful when implementing minimax.

          gameState.getLegalActions(agentIndex):
            Returns a list of legal actions for an agent
            agentIndex=0 means Pacman, ghosts are >= 1

          gameState.generateSuccessor(agentIndex, action):
            Returns the successor game state after an agent takes an action

          gameState.getNumAgents():
            Returns the total number of agents in the game

          gameState.isWin():
            Returns whether or not the game state is a winning state

          gameState.isLose():
            Returns whether or not the game state is a losing state
        """
        "*** YOUR CODE HERE ***"
        
        value,action = self.getValueAction(gameState, self.index, 0) # recursive, pass it the depth so far
        return action

    def getValueAction(self, gameState, agentIndex, depth):
        
        isPacman = agentIndex == self.index
        if GlobalDebug.debugOn: print 'getValueAction entered for agent%d, depth=%d (self.depth=%d)' % (agentIndex, depth, self.depth)
        
        # base case: depth limit reached, or win/lose state
        if gameState.isWin() or gameState.isLose() or (isPacman and depth == self.depth):
            value = self.evaluationFunction(gameState) 
            if GlobalDebug.debugOn: print 'base case value=%f, pacman=%s, maxDepth=%s, win=%s, lose=%s' % (value, str(isPacman), str(depth == self.depth), str(gameState.isWin()), str(gameState.isLose()))
            return value,None

        # need to recurse further, advance depth if the next agent falls off the end of the number of agents
        nextDepth = depth
        if agentIndex +1 == gameState.getNumAgents(): nextDepth += 1
        
        bestAction = None
        if isPacman: bestScore = -1e3000 # neg infinity
        else: bestScore = 1e3000         # infinity
            
        for action in gameState.getLegalActions(agentIndex):
            successorGameState = gameState.generateSuccessor(agentIndex, action)
            value,_ = self.getValueAction(successorGameState, (agentIndex +1) % gameState.getNumAgents(), nextDepth) # mod num agents to go round to first agent again

            # maximise for pacman, minimise others
            if (isPacman and value > bestScore) or (not isPacman and value < bestScore):
                bestScore = value
                bestAction = action
        
        if GlobalDebug.debugOn: 
            if isPacman:
                print 'pacman at level=%d, best score=%f action=%s' % (depth, bestScore, bestAction)
            else:
                print 'ghost (%d) at level=%d, best score=%f action=%s' % (agentIndex, depth, bestScore, bestAction)

        return bestScore, bestAction
        
class AlphaBetaAgent(MultiAgentSearchAgent):
    """
      Your minimax agent with alpha-beta pruning (question 3)
    """

    def getAction(self, gameState):
        """
          Returns the minimax action using self.depth and self.evaluationFunction
        """
        "*** YOUR CODE HERE ***"
        value,action = self.getValueAction(gameState, self.index, 0) # recursive, pass it the depth so far
        return action

    def getValueAction(self, gameState, agentIndex, depth, alpha=-1e3000, beta=1e3000): # alpha/beta best on path to root: alpha = max's best, beta = min's best
        
        isPacman = agentIndex == self.index
        if GlobalDebug.debugOn: print 'getValueAction entered for agent%d, depth=%d (self.depth=%d)' % (agentIndex, depth, self.depth)
        
        # base case: depth limit reached, or win/lose state
        if gameState.isWin() or gameState.isLose() or (isPacman and depth == self.depth):
            value = self.evaluationFunction(gameState) 
            if GlobalDebug.debugOn: print 'base case value=%f, pacman=%s, maxDepth=%s, win=%s, lose=%s' % (value, str(isPacman), str(depth == self.depth), str(gameState.isWin()), str(gameState.isLose()))
            return value,None

        # need to recurse further, advance depth if the next agent falls off the end of the number of agents
        nextDepth = depth
        if agentIndex +1 == gameState.getNumAgents(): nextDepth += 1
        
        bestAction = None
        if isPacman: bestScore = -1e3000 # neg infinity
        else: bestScore = 1e3000         # infinity
            
        for action in gameState.getLegalActions(agentIndex):
            successorGameState = gameState.generateSuccessor(agentIndex, action)
            value,_ = self.getValueAction(successorGameState, (agentIndex +1) % gameState.getNumAgents(), nextDepth, alpha, beta) # mod num agents to go round to first agent again

            if isPacman:                                # maximise for pacman
                if value > bestScore:
                    bestScore = value
                    bestAction = action
                    
                    if bestScore > beta:                # pruning here on beta
                        return bestScore, bestAction
                    
                    if bestScore > alpha:               # update alpha
                        alpha = bestScore
                         
            elif value < bestScore:                     # minimise for others
                bestScore = value
                bestAction = action
                
                if bestScore < alpha:                   # pruning here on alpha
                    return bestScore, bestAction
                
                if bestScore < beta:                    # update beta
                    beta = bestScore
        
        if GlobalDebug.debugOn: 
            if isPacman:
                print 'pacman at level=%d, best score=%f action=%s' % (depth, bestScore, bestAction)
            else:
                print 'ghost (%d) at level=%d, best score=%f action=%s' % (agentIndex, depth, bestScore, bestAction)

        return bestScore, bestAction
 
class ExpectimaxAgent(MultiAgentSearchAgent):
    """
      Your expectimax agent (question 4)
    """

    def getAction(self, gameState):
        """
          Returns the expectimax action using self.depth and self.evaluationFunction

          All ghosts should be modeled as choosing uniformly at random from their
          legal moves.
        """
        "*** YOUR CODE HERE ***"
        value,action = self.getValueAction(gameState, self.index, 0) # recursive, pass it the depth so far
        return action

    def getValueAction(self, gameState, agentIndex, depth):
        
        isPacman = agentIndex == self.index
        
        # base case: depth limit reached, or win/lose state
        if gameState.isWin() or gameState.isLose() or (isPacman and depth == self.depth):
            value = self.evaluationFunction(gameState) 
            return value,None

        # need to recurse further, advance depth if the next agent falls off the end of the number of agents
        nextDepth = depth
        if agentIndex +1 == gameState.getNumAgents(): nextDepth += 1
        
        bestAction = None
        if isPacman: bestScore = -1e3000 # neg infinity, still using max
        else:
            accumScore = 0.0
            
        legalMoves = gameState.getLegalActions(agentIndex)
        
        for action in legalMoves:
            successorGameState = gameState.generateSuccessor(agentIndex, action)
            value,_ = self.getValueAction(successorGameState, (agentIndex +1) % gameState.getNumAgents(), nextDepth) # mod num agents to go round to first agent again

            # maximise for pacman
            if isPacman and (value > bestScore or (value == bestScore and action != Directions.STOP)):  # tie break, don't choose stop
                bestScore = value
                bestAction = action
        
            elif not isPacman:                      # sum for average for others
                accumScore += value                 # action for adversaries is irrelevant for caller (only use it at the top of the recursion)

        if not isPacman:        # now average the values
            bestScore = accumScore / len(legalMoves)
            
        return bestScore, bestAction

class GlobalDebug:
    debugOn = False
    
class EvalGlobals:
    # to help with evaluation
    MAX_VALUE = 1000.0
    MAX_DIST = None
    MAX_CAPSULES = None
    MAX_FOOD_COUNT = None
    ONE_FOOD_VALUE = None
    # last used paths
    prevFoodPath = None
    
def betterEvaluationFunction(currentGameState):
    """
      Your extreme ghost-hunting, pellet-nabbing, food-gobbling, unstoppable
      evaluation function (question 5).

      DESCRIPTION: <write something here so we know what you did>
      I made 4 values all with 1/4 weighting:
      1) REMOVED THIS, STILL WORKS BECAUSE OF SCORE: inverse of amount of food left (full value for none left) 
      2) inverse of distance to nearest food (always ensuring the value of eating another food is worth more than getting nearer to one)
      3) inverse of number of capsules remaining
      4) score 
    """
    "*** YOUR CODE HERE ***"
    
    # easy to evaluate win/lose
    if currentGameState.isWin(): return 1000000.0
    if currentGameState.isLose(): return -1000000.0    
    
    statePos = currentGameState.getPacmanPosition()
    food = currentGameState.getFood()
    capsules = currentGameState.getCapsules()

    # store the first known counts for computing how well it's doing 
    if EvalGlobals.MAX_FOOD_COUNT == None or food.count() > EvalGlobals.MAX_FOOD_COUNT:
        EvalGlobals.MAX_FOOD_COUNT = food.count() * 1.0 # make sure it's a float
        EvalGlobals.ONE_FOOD_VALUE = EvalGlobals.MAX_VALUE / EvalGlobals.MAX_FOOD_COUNT
        EvalGlobals.MAX_DIST = food.width + food.height * 1.5 # just to add some for non-manhattan, updated below
    
    if EvalGlobals.MAX_CAPSULES == None or EvalGlobals.MAX_CAPSULES < len(capsules):
        EvalGlobals.MAX_CAPSULES = len(capsules)

    capsuleValue = .3 * (EvalGlobals.MAX_VALUE / EvalGlobals.MAX_CAPSULES) * (EvalGlobals.MAX_CAPSULES - len(capsules))
    scoreValue = .4 * currentGameState.getScore()
    
    # also keep the path (with its points for a short time)
    if EvalGlobals.prevFoodPath == None or EvalGlobals.prevFoodPath[1][0] != statePos or not food[EvalGlobals.prevFoodPath[1][-1][0]][EvalGlobals.prevFoodPath[1][-1][1]]:
        EvalGlobals.prevFoodPath = findPathToNearestThing(currentGameState, 'food')
        # when a path is used, and the distance > max, update the max dist
        if len(EvalGlobals.prevFoodPath[0]) > EvalGlobals.MAX_DIST:
            EvalGlobals.MAX_DIST = len(EvalGlobals.prevFoodPath[0])
    
    valuePerFoodDistance = EvalGlobals.ONE_FOOD_VALUE / EvalGlobals.MAX_DIST
    foodNearValue = .3 * (EvalGlobals.MAX_DIST - valuePerFoodDistance * len(EvalGlobals.prevFoodPath[0]))
    
    totalScore = foodNearValue + scoreValue + capsuleValue
    #if GlobalDebug.debugOn: print 'oneFood=%f, weights food=%f, foodNear%f, evade=%f, totalWeights=%f' % (EvalGlobals.ONE_FOOD_VALUE, foodWeight, foodNearWeight, evadeWeight, (foodWeight + foodNearWeight + evadeWeight))        
    #if GlobalDebug.debugOn: print 'values total=%f food=%f, foodNear=%f, nearestFood=%f, foodCount%f, scoreValue=%f' % (totalScore, foodValue, foodNearValue, len(EvalGlobals.prevFoodPath[0]), food.count(), scoreValue)       

    return totalScore
        
def findPathToNearestThing(gameState, thing, points=None):

    startPos = gameState.getPacmanPosition()
    food = gameState.getFood()
    capsules = gameState.getCapsules()
    walls = gameState.getWalls()
    
    closed = set()
    fringe = util.PriorityQueue()
    # put each valid action onto the stack
    fringe.push([startPos, [], [startPos], 0],0)	

    while not fringe.isEmpty():
        statePos,actions,points,cost = fringe.pop()

        # goal test
        if (thing == 'food' and food[statePos[0]][statePos[1]]) or (thing == 'capsule' and capsules.count(statePos) == 1) or (thing == 'list' and points.count(statePos) == 1):
            return actions, points
        
        # expand not in closed
        if not statePos in closed:

            closed.add(statePos)
            
            for action in [Directions.NORTH, Directions.SOUTH, Directions.EAST, Directions.WEST]:
                x,y = statePos
                dx, dy = Actions.directionToVector(action)
                nextx, nexty = int(x + dx), int(y + dy)
                if not walls[nextx][nexty]:
                    nextActions = actions[:]
                    nextActions.append(action)
                    nextState = (nextx, nexty)
                    nextPoints = points[:]
                    nextPoints.append(nextState)
                    fringe.push([nextState, nextActions, nextPoints, cost+1], cost+1)

def drawDebugMap(foodGrid, walls, start, movesList, pointsList):
    # debugging aid, drawing the whole thing, 5 spaces per square
    if pointsList == None: pointsList = []
    if movesList != None and len(pointsList) == 0:   # construct points from moves
        x,y = start
        for action in movesList:
            dx,dy = Actions.directionToVector(action)
            x += dx
            y += dy
            pointsList.append((x,y))
    else:
        print 'not making points list'
        
    print('points', pointsList)
    warning = None
    for ry in range(0, foodGrid.height):
        y = foodGrid.height - 1 - ry
        line1 = ''
        line2 = ''
        
        for x in range(0, foodGrid.width):
            if walls[x][y]: 
                if pointsList.count((x,y)) == 1:
                    line1 = line1 + '<O_o>'
                    line2 = line2 + '<***>'
                    warning = 'point is in a wall %d,%d' % (x,y)
                else:
                    line1 = line1 + '<<+>>'
                    line2 = line2 + '<<+>>'
            elif (x,y) == start: 
                if pointsList.count((x,y)) == 1:
                    line1 = line1 + ' / \ '
                    line2 = line2 + ' \M/ '
                else:
                    line1 = line1 + ' / \ '
                    line2 = line2 + ' \ / '
            else:
                if foodGrid[x][y]: 
                    line1 = line1 + '  o  '
                else:
                    line1 = line1 + '     '
                    
                if pointsList.count((x,y)) == 1:
                    line2 = line2 + '  M  '
                else:
                    line2 = line2 + '     '
                    
        print(line1)
        print(line2)
    
    if warning != None:
        print warning

# Abbreviation
better = betterEvaluationFunction


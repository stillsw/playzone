# search.py
# ---------
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
In search.py, you will implement generic search algorithms which are called by
Pacman agents (in searchAgents.py).
"""

from util import *
import util

class SearchProblem:
    """
    This class outlines the structure of a search problem, but doesn't implement
    any of the methods (in object-oriented terminology: an abstract class).

    You do not need to change anything in this class, ever.
    """

    def getStartState(self):
        """
        Returns the start state for the search problem.
        """
        util.raiseNotDefined()

    def isGoalState(self, state):
        """
          state: Search state

        Returns True if and only if the state is a valid goal state.
        """
        util.raiseNotDefined()

    def getSuccessors(self, state):
        """
          state: Search state

        For a given state, this should return a list of triples, (successor,
        action, stepCost), where 'successor' is a successor to the current
        state, 'action' is the action required to get there, and 'stepCost' is
        the incremental cost of expanding to that successor.
        """
        util.raiseNotDefined()

    def getCostOfActions(self, actions):
        """
         actions: A list of actions to take

        This method returns the total cost of a particular sequence of actions.
        The sequence must be composed of legal moves.
        """
        util.raiseNotDefined()


def tinyMazeSearch(problem):
    """
    Returns a sequence of moves that solves tinyMaze.  For any other maze, the
    sequence of moves will be incorrect, so only use this for tinyMaze.
    """
    from game import Directions
    s = Directions.SOUTH
    w = Directions.WEST
    return  [s, s, w, s, w, w, s, w]

def depthFirstSearch(problem):
    """
    Search the deepest nodes in the search tree first.

    Your search algorithm needs to return a list of actions that reaches the
    goal. Make sure to implement a graph search algorithm.

    To get started, you might want to try some of these simple commands to
    understand the search problem that is being passed in:

    print "Start:", problem.getStartState()
    print "Is the start a goal?", problem.isGoalState(problem.getStartState())
    print "Start's successors:", problem.getSuccessors(problem.getStartState())
    """
    "*** YOUR CODE HERE ***"
    #Project 1: Q1
    return graphSearch(problem, Stack(), Strategy())

def breadthFirstSearch(problem):
    """Search the shallowest nodes in the search tree first."""
    "*** YOUR CODE HERE ***"
    #Project 1: Q2
    return graphSearch(problem, Queue(), Strategy())

def uniformCostSearch(problem):
    """Search the node of least total cost first."""
    "*** YOUR CODE HERE ***"
    #Project 1: Q3
    return graphSearch(problem, PriorityQueue(), UcsStrategy())

def nullHeuristic(state, problem=None):
    """
    A heuristic function estimates the cost from the current state to the nearest
    goal in the provided SearchProblem.  This heuristic is trivial.
    """
    return 0

def aStarSearch(problem, heuristic=nullHeuristic):
    """Search the node that has the lowest combined cost and heuristic first."""
    "*** YOUR CODE HERE ***"
    #Project 1: Q4
    return graphSearch(problem, PriorityQueue(), AStarStrategy(heuristic, problem), True)

#Project 1: Q1 - 4
def graphSearch(problem, fringe, strategy, costSensitive=False):
    """
    Only the strategy and fringe varies
    A node looks like this: 
        [0] = state (eg. the coords)
        [1] = action
        [2] = cost
        [3] = parent node
    """
    closed = dict() # set of states already done
    strategy.initFringe(fringe, problem.getStartState())
    
    while not fringe.isEmpty():
        node = fringe.pop()
        state, _, cost, _ = node
        
        if problem.isGoalState(state):
            return expandActions(node)
                
        if shouldExpandNode(state, cost, closed, costSensitive):                
            # add to closed (which will update the cost if already there)
            closed[state] = cost
            for child in problem.getSuccessors(state):
                strategy.addNode(child, node, fringe)
            
    """ got no solution """                    
    return []

def expandActions(node):
    _, action, _, parent = node
    
    path = []
    while action:
        path.insert(0, action)
        _, action, _, parent = parent
    return path
    
def shouldExpandNode(state, cost, closed, costSensitive):
    if closed.has_key(state): 
      if costSensitive:
        # cscs means if the cost of this path is less than that on the path already, it should expand
        return cost < closed.get(state)
      else:
        return False
    else:
        return True

#Project 1: Q1 - 4
class Strategy:
    def initFringe(self, fringe, state):
        fringe.push([state, [], 0, None]) # [0] = the state, [1] = action, [2] = cost, [3] = parent node
        
    def addNode(self, child, parent, fringe):
        fringe.push([child[0], child[1], self.accumCost(child,parent), parent]) 
        
    def accumCost(self, child, parent):
        _, _, cost = child
        _, _, accumCost, _ = parent
        return accumCost + cost
        
#Project 1: Q3
class UcsStrategy( Strategy ):
    def initFringe(self, fringe, state):
        fringe.push([state, [], 0, None], 0) # like parent, only now the cost decides the priority too
        
    def addNode(self, child, parent, fringe):
        state, action, _ = child
        accumCost = self.accumCost(child, parent)
        fringe.push([state, action, accumCost, parent], accumCost)  
        
#Project 1: Q4
class AStarStrategy( Strategy ):
    def __init__(self, heuristic, problem):
        self.prob = problem
        self.heuristic = heuristic

    def initFringe(self, fringe, state):
        h = self.heuristic(state, self.prob)
        fringe.push([state, [], 0, None], h) # like parent, only now the initial value of priority is the heuristic

    def addNode(self, child, parent, fringe):
        state, action, _ = child
        G = self.accumCost(child, parent)
        H = self.heuristic(state, self.prob)
        fringe.push([state, action, G, parent], G + self.heuristic(state, self.prob)) 
        
# Abbreviations
bfs = breadthFirstSearch
dfs = depthFirstSearch
astar = aStarSearch
ucs = uniformCostSearch


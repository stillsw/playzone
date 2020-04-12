# valueIterationAgents.py
# -----------------------
# Licensing Information:  You are free to use or extend these projects for 
# educational purposes provided that (1) you do not distribute or publish 
# solutions, (2) you retain this notice, and (3) you provide clear 
# attribution to UC Berkeley, including a link to 
# http://inst.eecs.berkeley.edu/~cs188/pacman/pacman.html
# 
# Attribution Information: The Pacman AI projects were developed at UC Berkeley.
# The core projects and autograders were primarily created by John DeNero 
# (denero@cs.berkeley.edu) and Dan Klein (klein@cs.berkeley.edu).
# Student side autograding was added by Brad Miller, Nick Hay, and 
# Pieter Abbeel (pabbeel@cs.berkeley.edu).


import mdp, util

from learningAgents import ValueEstimationAgent

class ValueIterationAgent(ValueEstimationAgent):
    """
        * Please read learningAgents.py before reading this.*

        A ValueIterationAgent takes a Markov decision process
        (see mdp.py) on initialization and runs value iteration
        for a given number of iterations using the supplied
        discount factor.
    """
    def __init__(self, mdp, discount = 0.9, iterations = 100):
        """
          Your value iteration agent should take an mdp on
          construction, run the indicated number of iterations
          and then act according to the resulting policy.

          Some useful mdp methods you will use:
              mdp.getStates()
              mdp.getPossibleActions(state)
              mdp.getTransitionStatesAndProbs(state, action)
              mdp.getReward(state, action, nextState)
              mdp.isTerminal(state)
        """
        self.mdp = mdp
        self.discount = discount
        self.iterations = iterations
        self.values = util.Counter() # A Counter is a dict with default 0

        # Write value iteration code here
        "*** YOUR CODE HERE ***"

        #print 'DEBUG               ValueIterationAgent.init: called with it=%d' % iterations
        self.actions = dict()

        k = -1
        while k < iterations: 
            k += 1
            
            values = self.values.copy()
            for state in mdp.getStates():

                bestAction = None
                
                if k == 0:
                    bestValue = 0
                else:
                    bestValue = -1e3000

                    for action in self.mdp.getPossibleActions(state):
                        
                        #print 'DEBUG             before get qvalue sum: k=%d, state=%s, action=%s' % (k, state, str(action))
                        qValue = self.computeQValueFromValues(state, action)
                        #print 'DEBUG             after got sum: qValue = %s' % (str(qValue))

                        if qValue > bestValue:
                            bestValue = qValue
                            bestAction = action


                values[state] = bestValue # will overwrite at each iteration (value of which is based on the previous)
                self.actions[state] = bestAction

            self.values = values # overwrite the whole set with the values from the latest iteration

    def getValue(self, state):
        """
          Return the value of the state (computed in __init__).
        """
        if self.mdp.isTerminal(state):
            return 0
        value = self.values.get(state)
        defaulted = False
        if value == None: 
            value = 0
            defaulted = True
        
        #print 'DEBUG               ValueIterationAgent.getValue: called for state=%s, returning value=%.2f (defaulted=%s)' % (state, value, str(defaulted))
        return value


    def computeQValueFromValues(self, state, action):
        """
          Compute the Q-value of action in state from the
          value function stored in self.values.
        """
        "*** YOUR CODE HERE ***"
        
        actionSum = 0

        for t,p in self.mdp.getTransitionStatesAndProbs(state, action):
            
            if self.mdp.isTerminal(t):
                vk_1 = self.mdp.getReward(t, action, 'exit')
            elif self.values.has_key(t):                
                vk_1 = self.values.get(t)                
            else:
                vk_1 = 0

            vAction = p * (self.mdp.getReward(state, action, t) + self.discount * vk_1)
            #print 'DEBUG    transition = %s, prob = %.2f, vk_1 = %s, vAction=%.2f' % (str(t), p, str(vk_1), vAction)
            
            actionSum += vAction

        return actionSum

    def computeActionFromValues(self, state):
        """
          The policy is the best action in the given state
          according to the values currently stored in self.values.

          You may break ties any way you see fit.  Note that if
          there are no legal actions, which is the case at the
          terminal state, you should return None.
        """
        "*** YOUR CODE HERE ***"
        action = self.actions.get(state)
        #print 'DEBUG               ValueIterationAgent.computeActionFromValues: called for state=%s, returning value=%s' % (state, action)
        return action
        
    def getPolicy(self, state):
        return self.computeActionFromValues(state)

    def getAction(self, state):
        "Returns the policy at the state (no exploration)."
        return self.computeActionFromValues(state)

    def getQValue(self, state, action):
        return self.computeQValueFromValues(state, action)

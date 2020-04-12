import numpy as np


class UnionFind(object):
    """simple implementation of UnionFind algorithm
       initialize members, rank and number of sets"""
    def __init__(self, size=2**24, arr=None):
        self.members = np.arange(size)
        self.rank = np.ones(size, dtype=int)
        self.sets = len(arr.nonzero()[0])

    def connected(self, node1, node2):
        """simply compare if 2 nodes have the same root"""
        return self.find_root(node1) == self.find_root(node2)

    def union(self, node1, node2):
        """find root of both nodes and return if they are already
           the same.
           Otherwise we need to unite them, in this case decrease
           the number of sets.
           Than simply change the root of the node which has lower rank"""
        root1 = self.find_root(node1)
        root2 = self.find_root(node2)

        if root1 == root2:
            return None

        self.sets -= 1

        if self.rank[root1] < self.rank[root2]:
            self.members[root1] = root2
        else:
            if self.rank[root1] == self.rank[root2]:
                self.rank[root1] += 1
            self.members[root2] = root1

    def find_root(self, node):
        """This implementation is the path compression principle
           of the UnionFind data structure. As we search for the root
           and go up the 'chain' we compress the path by assigning the
           root of each node to the root of their parent."""
        while node != self.members[node]:
            self.members[node] = self.members[self.members[node]]
            node = self.members[node]
        return node





def bitmasks(n, dist):
    """Returns list of numbers that XORed with any integer
       will give another integer within given hamming distance
       'dist' away
    n : an integer indicating the number of bits
    dist : Hamming distance
    """
    if dist < n:
        if dist > 0:
            for x in bitmasks(n - 1, dist - 1):
                yield (1 << (n - 1)) + x
            for x in bitmasks(n - 1, dist):
                yield x
        else:
            yield 0
    else:
        yield (1 << n) - 1





"""Simple implementation of clustering algorithm using
   bit hamming to determine verticies distance
"""

import os
import sys
#misc_path = os.path.abspath(os.path.join('..', 'misc'))
#sys.path.append(misc_path)
#from bitmasks import bitmasks
#from union_find_clustering import UnionFind
#import numpy as np
import itertools

class Graph():

    """simple Graph class to run clustering algorithm"""

    def __init__(self, file_name):
        self._verticies = np.zeros(2**24, dtype=bool)
        self.process_lines(file_name)
        self._bitmasks = [i for i in itertools.chain(bitmasks(24,2),
                                                     bitmasks(24,1))]

    def process_lines(self, file_name):
        with open(file_name) as myfile:
            for line in myfile:
                self._verticies[int("".join(line.split()), base=2)] = True
        self._union_find = UnionFind(arr=self._verticies)

    def clustering(self):
        """ """
        for vertex in self._verticies.nonzero()[0]:
            for mask in self._bitmasks:
                if self._verticies[vertex ^ mask]:
                    self._union_find.union(vertex, vertex^mask)
        return self._union_find.sets

    def clustering_numpy(self):
        """ """
        verticies = self._verticies.nonzero()[0]
        for mask in self._bitmasks:
            for v_index, connected_vertex in enumerate(verticies ^ mask):
                if self._verticies[connected_vertex]:
                    self._union_find.union(verticies[v_index], connected_vertex)
        return self._union_find.sets

# import profile
graph = Graph('w2_clustering_big_stefano.txt')
# profile.run("graph.clustering()")
# profile.run("print(graph.clustering_numpy())")

print(graph.clustering_numpy())

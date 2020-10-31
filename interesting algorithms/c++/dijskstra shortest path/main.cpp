#include <iostream>

using namespace std;

class Node {
public:
    Node(const string &label, int totalDistance) : label(label), total_distance(totalDistance) {}

    virtual ~Node() {}

    const string &getLabel() const {
        return label;
    }

private:
    string label;
    // convenient for processing
    int total_distance;
    Node* previous;
};

class Edge {
public:
    Edge(Node *fromNode, Node *toNode, int distance) : from_node(fromNode), to_node(toNode), distance(distance) {}

    virtual ~Edge() {}

private:
    Node* from_node;
    Node* to_node;
    int distance;
};

int main() {

    //!- 7 nodes go from a - z

    Node unvisted[] = { Node("a", 0),
                        Node("b", INT32_MAX),
                        Node("c", INT32_MAX),
                        Node("d", INT32_MAX),
                        Node("e", INT32_MAX),
                        Node("f", INT32_MAX),
                        Node("z", INT32_MAX) };

    //!- edges are directed

    Edge edges[] = { Edge(&unvisted[0], &unvisted[1], 6)};

    bool done = false;
    while (!done) {

    }

    cout << "Hello, World!" << endl;
    return 0;
}
